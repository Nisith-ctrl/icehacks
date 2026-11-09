// Generic handling for both test forms: required-field checks with visible error text,
// then the submitted values go to sessionStorage and the confirmation page reads them back.
// Nothing here is specific to our agent; the forms behave like ordinary web forms.

(function () {
  function fieldLabel(form, name) {
    const el = form.elements[name];
    const first = el instanceof RadioNodeList ? el[0] : el;
    const fieldset = first && first.closest("fieldset");
    if (fieldset) return fieldset.querySelector("legend").textContent.replace(/\(required\)/i, "").trim();
    const label = first && form.querySelector(`label[for="${first.id}"]`);
    return label ? label.textContent.replace(/\(required\)/i, "").trim() : name;
  }

  function confirmationNumber(prefix) {
    const n = Math.floor(100000 + Math.random() * 900000);
    return `${prefix}-${n}`;
  }

  // options: { formId, storageKey, confirmPage, prefix, order: [field names], describeChoice: fn(name, value) }
  window.StgForm = function setup(options) {
    const form = document.getElementById(options.formId);
    const errorBox = document.getElementById("errors");

    form.addEventListener("submit", (event) => {
      event.preventDefault();
      const missing = [];
      for (const name of options.order) {
        const el = form.elements[name];
        const required = el instanceof RadioNodeList ? el[0].required : el.required;
        if (required && !String(el.value || "").trim()) missing.push(fieldLabel(form, name));
      }
      if (missing.length) {
        errorBox.hidden = false;
        errorBox.textContent = `Please fill in: ${missing.join(", ")}.`;
        errorBox.scrollIntoView({ block: "center" });
        return;
      }
      const values = options.order.map((name) => ({
        label: fieldLabel(form, name),
        value: options.describeChoice ? options.describeChoice(name, form.elements[name].value) : form.elements[name].value,
      }));
      const record = {
        number: confirmationNumber(options.prefix),
        submittedAt: new Date().toISOString(),
        values,
      };
      try {
        sessionStorage.setItem(options.storageKey, JSON.stringify(record));
      } catch (e) {
        // Storage blocked: fall through, the confirmation page shows a clear error instead.
      }
      location.href = options.confirmPage;
    });
  };

  window.StgConfirm = function render(storageKey) {
    let record = null;
    try {
      record = JSON.parse(sessionStorage.getItem(storageKey) || "null");
    } catch (e) {
      record = null;
    }
    const ok = document.getElementById("ok");
    const none = document.getElementById("none");
    if (!record) {
      ok.hidden = true;
      none.hidden = false;
      return;
    }
    document.getElementById("number").textContent = record.number;
    const list = document.getElementById("values");
    for (const v of record.values) {
      const dt = document.createElement("dt");
      dt.textContent = v.label;
      const dd = document.createElement("dd");
      dd.textContent = v.value;
      list.append(dt, dd);
    }
  };
})();
