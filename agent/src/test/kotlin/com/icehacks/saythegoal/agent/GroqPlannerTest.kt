package com.icehacks.saythegoal.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GroqPlannerTest {
    private lateinit var server: MockWebServer
    private val slept = mutableListOf<Long>()

    private fun planner() = GroqPlanner(
        AgentConfig(baseUrl = server.url("/openai/v1").toString(), apiKey = "gsk_test", model = "openai/gpt-oss-120b", visionModel = "qwen/qwen3.8-27b"),
        sleep = { slept += it },
        log = {},
    )

    private val turn = PlannerTurn("open Gmail", false, "Monday", emptyList(), emptyList(), emptyList(), null, "app: home screen\nn16 TextView \"Gmail\" [tap]")

    private fun toolCall(name: String, args: String) = MockResponse().setResponseCode(200).setBody(
        """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function",
           "function":{"name":"$name","arguments":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(args))}}}]}}],
           "usage":{"prompt_tokens":900,"completion_tokens":40}}""",
    )

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    @Test
    fun sendsOneToolTurnAndParsesTheCall() = runTest {
        server.enqueue(toolCall("tap", """{"node_id":"n16","caption":"Opening Gmail"}"""))
        val step = planner().next(turn)
        assertEquals("tap", step.tool)
        assertEquals("n16", step.input["node_id"]!!.jsonPrimitive.content)

        val req = server.takeRequest()
        assertEquals("/openai/v1/chat/completions", req.path)
        assertEquals("Bearer gsk_test", req.getHeader("Authorization"))
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("openai/gpt-oss-120b", body["model"]!!.jsonPrimitive.content)
        assertEquals("required", body["tool_choice"]!!.jsonPrimitive.content)
        val msgs = body["messages"]!!.jsonArray
        assertEquals("system", msgs[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue(msgs[1].jsonObject["content"]!!.jsonPrimitive.content.contains("n16 TextView"))
        val names = body["tools"]!!.jsonArray.map { it.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(AgentTools.all.map { it.name }, names)
    }

    @Test
    fun waitsOnRateLimitThenRetries() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("retry-after", "7").setBody("""{"error":{"message":"Rate limit reached"}}"""))
        server.enqueue(toolCall("done", """{"summary":"Gmail is open."}"""))
        val step = planner().next(turn)
        assertEquals("done", step.tool)
        assertEquals(listOf(7_250L), slept)
    }

    @Test
    fun tooLongRateLimitGivesUpWithASpokenReason() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("retry-after", "3600"))
        val step = planner().next(turn)
        assertEquals("stop", step.tool)
        assertTrue(step.input["reason"]!!.jsonPrimitive.content.contains("free usage limit"))
    }

    @Test
    fun dropsUnsupportedOptionalFieldsAfterA400() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"`reasoning_effort` is not supported with this model","code":"invalid_request_error"}}"""))
        server.enqueue(toolCall("back", """{"caption":"Going back"}"""))
        val p = planner()
        assertEquals("back", p.next(turn).tool)
        server.takeRequest()
        val retried = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertFalse("reasoning_effort" in retried)
    }

    @Test
    fun malformedToolCallBecomesFeedback() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"Failed to call a function.","code":"tool_use_failed","failed_generation":"..."}}"""))
        val step = planner().next(turn)
        assertEquals("none", step.tool)
        assertTrue(step.text!!.startsWith("Your last tool call was malformed"))
    }

    @Test
    fun badKeyIsSpoken() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invalid API Key"}}"""))
        val step = planner().next(turn)
        assertEquals("stop", step.tool)
        assertTrue(step.input["reason"]!!.jsonPrimitive.content.contains("GROQ_API_KEY"))
    }
}
