// Session/shift times for the test forms and the seed page.
// The invite email links carry the times in the query string (?s1=...&s2=...&d=...), so the form
// always matches the email that was sent. Without a query string, times are computed from today.

(function () {
  const EVENTS = {
    library: { firstOffsetDays: 3, firstTime: "10:00", secondOffsetDays: 4, secondTime: "14:00", minutes: 90 },
    volunteer: { firstOffsetDays: 5, firstTime: "09:00", secondOffsetDays: 6, secondTime: "13:00", minutes: 180 },
  };

  const pad = (n) => String(n).padStart(2, "0");

  // Local "YYYY-MM-DDTHH:MM" (no time zone: the device's zone applies).
  function toLocalIso(d) {
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function parseLocalIso(s) {
    const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(s || "");
    if (!m) return null;
    return new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5]);
  }

  function at(base, offsetDays, hhmm) {
    const [h, m] = hhmm.split(":").map(Number);
    return new Date(base.getFullYear(), base.getMonth(), base.getDate() + offsetDays, h, m);
  }

  function defaults(kind, base = new Date()) {
    const e = EVENTS[kind];
    return {
      s1: at(base, e.firstOffsetDays, e.firstTime),
      s2: at(base, e.secondOffsetDays, e.secondTime),
      minutes: e.minutes,
    };
  }

  function fromQuery(kind, search = location.search) {
    const q = new URLSearchParams(search);
    const d = defaults(kind);
    const minutes = parseInt(q.get("d"), 10);
    return {
      s1: parseLocalIso(q.get("s1")) || d.s1,
      s2: parseLocalIso(q.get("s2")) || d.s2,
      minutes: Number.isFinite(minutes) && minutes > 0 ? minutes : d.minutes,
    };
  }

  function addMinutes(d, minutes) {
    return new Date(d.getTime() + minutes * 60000);
  }

  const dayFmt = new Intl.DateTimeFormat("en-US", { weekday: "long", month: "long", day: "numeric" });
  const timeFmt = new Intl.DateTimeFormat("en-US", { hour: "numeric", minute: "2-digit" });

  // "Thursday, November 12, 10:00 AM to 11:30 AM"
  function describe(start, minutes) {
    return `${dayFmt.format(start)}, ${timeFmt.format(start)} to ${timeFmt.format(addMinutes(start, minutes))}`;
  }

  window.StgDates = { EVENTS, defaults, fromQuery, describe, addMinutes, toLocalIso, parseLocalIso };
})();
