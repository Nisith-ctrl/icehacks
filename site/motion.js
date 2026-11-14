// Motion for the SuuS site, with Motion's vanilla API (the library behind Framer Motion).
// Patterns from Framer Motion, without React:
//   variants + staggerChildren  -> reveal(): parents orchestrate children with stagger()
//   whileInView / useInView     -> inView(..., once)
//   useScroll + useTransform    -> scroll(progress => ...) mapped to transforms and CSS variables
//   layoutId (shared element)   -> the tab pill glides between tabs; panels enter with a stagger
//   whileHover / whileTap / drag-> lift, press squash, card tilt, and a robot you can fling
// Only transform and opacity are animated. Nothing runs with "reduce motion" on, and if this file
// fails to load, the page shows everything as normal (see the inline script in index.html).
import { animate, scroll, inView, stagger } from "https://cdn.jsdelivr.net/npm/motion@11.18.2/+esm";

const root = document.documentElement;
if (root.classList.contains("motion")) start();

function start() {
  root.classList.add("motion-ready");

  const SPRING = { type: "spring", stiffness: 260, damping: 26 };
  const BOUNCY = { type: "spring", stiffness: 420, damping: 14 };
  const EASE_OUT = [0.16, 1, 0.3, 1];
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const finePointer = matchMedia("(hover: hover) and (pointer: fine)").matches;

  // ---------- variants: hero entrance ----------
  const h1 = $(".hero h1");
  splitWords(h1);
  const heroBits = $$(".hero-text > *").filter((el) => el !== h1);
  h1.style.opacity = 1;
  animate($$(".hero h1 .w"), { opacity: [0, 1], y: [28, 0], filter: ["blur(6px)", "blur(0px)"] },
    { delay: stagger(0.07, { startDelay: 0.1 }), duration: 0.7, ease: EASE_OUT });
  animate(heroBits, { opacity: [0, 1], y: [18, 0] },
    { delay: stagger(0.09, { startDelay: 0.55 }), duration: 0.6, ease: EASE_OUT });

  const art = $(".hero-art");
  const bubble = $(".bubble");
  const robot = $(".hero-art img");
  const robotBtn = wrapRobot(robot);
  animate(art, { opacity: [0, 1] }, { duration: 0.2, delay: 0.3 });
  animate(robotBtn, { y: [-90, 0], scale: [0.7, 1], rotate: [-12, 0] }, { ...BOUNCY, delay: 0.35 });
  animate(bubble, { opacity: [0, 1], scale: [0.6, 1], y: [12, 0] }, { ...BOUNCY, delay: 0.75 })
    .then(() => typeInto(bubble, bubble.dataset.say));
  // Idle: the robot breathes (a slow float), like it's waiting for "Suus".
  const floater = $(".robot-float");
  animate(floater, { y: [0, -9, 0] }, { duration: 3.2, repeat: Infinity, ease: "easeInOut", delay: 1.2 });

  // ---------- gestures: the robot you can grab ----------
  const SAYINGS = [
    "Suus, sign me up for the library workshop that fits my calendar.",
    "Suus, take over. Attach yesterday's dog photo.",
    "Suus, undo the photo.",
    "Suus, use the one by the door.",
    "Suus, send it.",
    "Suus, what do you remember?",
    "Stop.",
  ];
  let saying = 0;
  const nextSaying = () => {
    saying = (saying + 1) % SAYINGS.length;
    animate(bubble, { scale: [0.9, 1] }, BOUNCY);
    typeInto(bubble, SAYINGS[saying]);
  };
  dragRobot(robot, robotBtn, nextSaying);
  robotBtn.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      animate(robot, { scaleY: [0.82, 1], scaleX: [1.12, 1] }, BOUNCY);
      nextSaying();
    }
  });

  // ---------- scroll: reading progress and hero parallax ----------
  const ribbon = $(".top .ribbon");
  scroll((p) => ribbon.style.setProperty("--sp", p.toFixed(4)));
  scroll((p) => {
    art.style.setProperty("--hp", p.toFixed(4)); // drives the ribbon glow behind the robot (CSS)
    art.style.translate = `0 ${(p * 70).toFixed(1)}px`;
  }, { target: $(".hero"), offset: ["start start", "end start"] });

  // ---------- variants + inView: every section orchestrates its children ----------
  const GROUPS = [".split", ".steps", ".grid3", ".team", ".setup", ".exits", ".tools ul", ".arch-mods"];
  $$(".section").forEach((sec) => {
    const leads = $$(":scope > .eyebrow, :scope > h2, :scope > .lede, :scope > .sub", sec);
    if (leads.length) {
      inView(sec, () => {
        animate(leads, { opacity: [0, 1], y: [22, 0] }, { delay: stagger(0.08), duration: 0.6, ease: EASE_OUT });
      }, { amount: 0.15 });
    }
  });
  GROUPS.forEach((sel) => $$(sel).forEach((group) => {
    inView(group, () => {
      animate([...group.children], { opacity: [0, 1], y: [36, 0], scale: [0.97, 1] },
        { delay: stagger(0.09), duration: 0.65, ease: EASE_OUT });
      $$(".num", group).forEach((n, i) => animate(n, { scale: [0, 1], rotate: [-90, 0] }, { ...BOUNCY, delay: 0.15 + i * 0.09 }));
    }, { amount: 0.2 });
  }));
  [".notice", ".chart", ".tabs", ".video", ".under"].forEach((sel) => $$(sel).forEach((el) => {
    inView(el, () => { animate(el, { opacity: [0, 1], y: [30, 0] }, { duration: 0.7, ease: EASE_OUT }); }, { amount: 0.15 });
  }));

  // The agent loop: each step lights up in order, like the loop running.
  $$(".flow").forEach((flow) => inView(flow, () => {
    animate($$(".node", flow), { opacity: [0, 1], x: [-24, 0] }, { delay: stagger(0.14), duration: 0.55, ease: EASE_OUT });
  }, { amount: 0.2 }));

  // "Under the hood": the list arrives in order each time it opens.
  $$("details.under").forEach((d) => d.addEventListener("toggle", () => {
    if (d.open) animate($$("li", d), { opacity: [0, 1], y: [12, 0] }, { delay: stagger(0.06), duration: 0.45, ease: EASE_OUT });
  }));

  // ---------- scroll-triggered numbers: bars grow, counts tick up ----------
  const chart = $(".chart");
  if (chart) inView(chart, () => {
    $$(".bar.hand", chart).forEach((bar, i) => {
      animate(bar, { scaleX: [0, 1] }, { type: "spring", stiffness: 90, damping: 18, delay: 0.25 + i * 0.2 });
      const label = $("span", bar);
      if (label) countUp(label, Number(label.textContent), 1.1, 0.25 + i * 0.2);
    });
    $$(".bar.suus", chart).forEach((bar, i) => animate(bar, { scaleY: [0, 1] }, { ...BOUNCY, delay: 0.9 + i * 0.2 }));
    animate($$(".zero", chart), { opacity: [0, 1], x: [-10, 0] }, { delay: stagger(0.2, { startDelay: 1 }), duration: 0.5 });
  }, { amount: 0.35 });
  const title = $("#results-h");
  if (title) {
    title.setAttribute("aria-label", title.textContent.trim());
    const n = wrapNumber(title, "97");
    if (n) inView(title, () => countUp(n, 97, 1.4, 0.2), { amount: 0.6 });
  }
  $$(".fact b").forEach((b) => inView(b, () => {
    const m = b.textContent.match(/^(\d+(?:\.\d+)?)/);
    if (m && Number(m[1]) > 0) countUp(b, Number(m[1]), 1, 0.1, b.textContent, m[1]);
    animate(b, { scale: [0.6, 1] }, BOUNCY);
  }, { amount: 0.8 }));

  // ---------- layout: the tab pill glides between tabs (a shared element, like layoutId) ----------
  const tablist = $(".tablist");
  if (tablist) {
    const pill = document.createElement("span");
    pill.className = "tab-pill";
    pill.setAttribute("aria-hidden", "true");
    tablist.prepend(pill);
    const place = (instant) => {
      const tab = $('[aria-selected="true"]', tablist);
      if (!tab) return;
      const to = { x: tab.offsetLeft, y: tab.offsetTop, width: tab.offsetWidth, height: tab.offsetHeight };
      if (instant) {
        Object.assign(pill.style, { width: to.width + "px", height: to.height + "px", transform: `translate(${to.x}px, ${to.y}px)` });
      } else {
        animate(pill, { x: to.x, y: to.y, width: to.width + "px", height: to.height + "px" }, { type: "spring", stiffness: 380, damping: 32 });
      }
    };
    place(true);
    new ResizeObserver(() => place(true)).observe(tablist);
    // The tab script flips aria-selected and hidden; follow it.
    new MutationObserver(() => {
      place(false);
      const panel = $('[role="tabpanel"]:not([hidden])');
      if (!panel) return;
      animate(panel, { opacity: [0, 1], y: [16, 0] }, { duration: 0.45, ease: EASE_OUT });
      animate($$(".node, .arch-mods > li, .arch-app, .arch-core, .mini, tbody tr", panel), { opacity: [0, 1], y: [14, 0] },
        { delay: stagger(0.05, { startDelay: 0.08 }), duration: 0.4, ease: EASE_OUT });
    }).observe(tablist, { attributes: true, subtree: true, attributeFilter: ["aria-selected"] });
  }

  // ---------- gestures: press squash on every button, lift on hover ----------
  $$(".btn, .tablist button").forEach((b) => {
    b.addEventListener("pointerdown", () => animate(b, { scale: 0.94 }, { type: "spring", stiffness: 600, damping: 30 }));
    const release = () => animate(b, { scale: 1 }, BOUNCY);
    b.addEventListener("pointerup", release);
    b.addEventListener("pointerleave", release);
    if (finePointer && b.classList.contains("btn")) {
      b.addEventListener("pointerenter", () => animate(b, { y: -2 }, SPRING));
      b.addEventListener("pointerleave", () => animate(b, { y: 0 }, SPRING));
    }
  });

  // Cards tilt a little toward the pointer and lift (mouse/trackpad only).
  if (finePointer) {
    $$(".steps .card, .grid3 > .card, .split > .card, .team .card, .fact").forEach((card) => {
      card.addEventListener("pointermove", (e) => {
        const r = card.getBoundingClientRect();
        const px = (e.clientX - r.left) / r.width - 0.5;
        const py = (e.clientY - r.top) / r.height - 0.5;
        animate(card, { rotateY: px * 6, rotateX: -py * 6, y: -6, transformPerspective: 900 }, { type: "spring", stiffness: 300, damping: 25 });
      });
      card.addEventListener("pointerleave", () => animate(card, { rotateY: 0, rotateX: 0, y: 0 }, SPRING));
    });
  }

  // ---------- helpers ----------
  function splitWords(el) {
    const walk = (node) => {
      [...node.childNodes].forEach((child) => {
        if (child.nodeType === 3) {
          const frag = document.createDocumentFragment();
          child.textContent.split(/(\s+)/).forEach((part) => {
            if (!part) return;
            if (/^\s+$/.test(part)) { frag.append(part); return; }
            const s = document.createElement("span");
            s.className = "w";
            s.textContent = part;
            frag.append(s);
          });
          child.replaceWith(frag);
        } else if (child.nodeType === 1 && child.tagName !== "BR") {
          walk(child);
        }
      });
    };
    walk(el);
  }

  function wrapRobot(img) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "robot-btn";
    btn.setAttribute("aria-label", "Show another thing you can say to SuuS");
    const float = document.createElement("span");
    float.className = "robot-float";
    img.replaceWith(btn);
    float.append(img);
    btn.append(float);
    // The bubble keeps its words for screen readers; the typed copy is visual only.
    bubble.dataset.say = bubble.textContent.replace(/^["“]|["”]$/g, "").trim();
    bubble.setAttribute("aria-live", "polite");
    bubble.innerHTML = '<span class="sr"></span><span class="typed" aria-hidden="true"></span>';
    $(".sr", bubble).textContent = bubble.dataset.say;
    return btn;
  }

  let typing;
  function typeInto(el, text) {
    $(".sr", el).textContent = text;
    const out = $(".typed", el);
    typing?.stop();
    const full = `"${text}"`;
    typing = animate(0, full.length, {
      duration: Math.min(1.4, 0.03 * full.length),
      ease: "linear",
      onUpdate: (v) => { out.textContent = full.slice(0, Math.round(v)); },
    });
  }

  // Drag with elastic bounds; let go and it springs home (like drag + dragConstraints + dragElastic).
  function dragRobot(img, btn, onRelease) {
    let start = null;
    let moved = false;
    img.style.touchAction = "none";
    img.addEventListener("pointerdown", (e) => {
      start = { x: e.clientX, y: e.clientY };
      moved = false;
      img.setPointerCapture(e.pointerId);
      animate(img, { scale: 1.08 }, SPRING);
      img.style.cursor = "grabbing";
    });
    img.addEventListener("pointermove", (e) => {
      if (!start) return;
      const dx = e.clientX - start.x;
      const dy = e.clientY - start.y;
      if (Math.abs(dx) + Math.abs(dy) > 4) moved = true;
      const elastic = (d) => Math.sign(d) * Math.min(Math.abs(d), 160) * 0.55; // pulls back the further you go
      animate(img, { x: elastic(dx), y: elastic(dy), rotate: elastic(dx) / 8 }, { duration: 0 });
    });
    const end = () => {
      if (!start) return;
      start = null;
      img.style.cursor = "";
      animate(img, { x: 0, y: 0, rotate: 0, scale: 1 }, { type: "spring", stiffness: 300, damping: 10 });
      if (!moved) animate(img, { scaleY: [0.82, 1], scaleX: [1.12, 1] }, BOUNCY);
      onRelease();
    };
    img.addEventListener("pointerup", end);
    img.addEventListener("pointercancel", end);
  }

  function wrapNumber(el, num) {
    const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    for (let t = walker.nextNode(); t; t = walker.nextNode()) {
      const i = t.textContent.indexOf(num);
      if (i < 0) continue;
      const after = t.splitText(i);
      after.splitText(num.length);
      const span = document.createElement("span");
      span.className = "count";
      span.setAttribute("aria-hidden", "true");
      span.textContent = num;
      after.replaceWith(span);
      return span;
    }
    return null;
  }

  function countUp(el, to, duration, delay, template, token) {
    const decimals = String(to).includes(".") ? String(to).split(".")[1].length : 0;
    const render = (v) => {
      const s = v.toFixed(decimals);
      el.textContent = template ? template.replace(token, s) : s;
    };
    render(0);
    animate(0, to, { duration, delay, ease: EASE_OUT, onUpdate: render });
  }
}
