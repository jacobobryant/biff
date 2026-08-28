(function () {
  function fallbackCopy(text) {
    var textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.style.position = "fixed";
    textarea.style.opacity = "0";
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand("copy");
    textarea.remove();
  }

  function showSuccess(element) {
    var successText = element.getAttribute("data-clipboard-success-text");
    if (!successText) {
      return;
    }
    var originalText = element.textContent;
    element.textContent = successText;
    setTimeout(function () {
      element.textContent = originalText;
    }, 2000);
  }

  window.biffAdminCopy = function (element) {
    var text = element.getAttribute("data-clipboard");
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () {
        showSuccess(element);
      }).catch(function () {
        fallbackCopy(text);
        showSuccess(element);
      });
    } else {
      fallbackCopy(text);
      showSuccess(element);
    }
  };

  function copyOnLoad() {
    document.querySelectorAll("[data-clipboard-on-load]")
      .forEach(window.biffAdminCopy);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", copyOnLoad);
  } else {
    copyOnLoad();
  }
})();
