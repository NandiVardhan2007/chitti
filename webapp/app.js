/* Chitti website — behaviour
   Only client-side state used anywhere on this site: one localStorage flag
   ("chitti-cookie-consent") remembering that the cookie banner was dismissed.
   Nothing is sent to any server. See cookies.html for the plain-language disclosure. */
(function () {
  "use strict";

  var KEY = "chitti-cookie-consent";

  function readConsent() {
    try {
      return window.localStorage.getItem(KEY);
    } catch (e) {
      return null; // storage blocked (private mode etc.) — treat as not-yet-decided
    }
  }

  function writeConsent(value) {
    try {
      window.localStorage.setItem(KEY, value);
    } catch (e) {
      /* nothing we can do; banner simply reappears next load */
    }
  }

  function clearConsent() {
    try {
      window.localStorage.removeItem(KEY);
    } catch (e) {}
  }

  var banner = document.getElementById("cookie-banner");

  function hideBanner() {
    if (banner) banner.classList.remove("show");
  }
  function showBanner() {
    if (banner) banner.classList.add("show");
  }

  // Show the banner only if no choice has been stored yet.
  if (banner && !readConsent()) {
    showBanner();
  }

  var acceptBtn = document.getElementById("cookie-accept");
  var dismissBtn = document.getElementById("cookie-dismiss");
  if (acceptBtn) {
    acceptBtn.addEventListener("click", function () {
      writeConsent("accepted");
      hideBanner();
      reflectStatus();
    });
  }
  if (dismissBtn) {
    dismissBtn.addEventListener("click", function () {
      writeConsent("dismissed");
      hideBanner();
      reflectStatus();
    });
  }

  // "Clear stored preference" control on the Cookies page.
  var clearBtn = document.getElementById("cookie-clear");
  var statusEl = document.getElementById("cookie-status");

  function reflectStatus() {
    if (!statusEl) return;
    var v = readConsent();
    statusEl.textContent = v
      ? 'Stored preference: "' + v + '".'
      : "No preference is currently stored on this device.";
  }

  if (clearBtn) {
    clearBtn.addEventListener("click", function () {
      clearConsent();
      reflectStatus();
      showBanner(); // let the visitor see the banner again immediately
    });
  }

  reflectStatus();
})();
