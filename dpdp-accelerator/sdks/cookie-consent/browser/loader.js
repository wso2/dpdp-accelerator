/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/* Runs vanilla-cookieconsent with the decision the site's backend holds in WSO2 IS. */
(function () {
  'use strict';

  var core = window.DPDPCookieConsentCore;
  var base = document.currentScript.src.replace(/\/loader\.js(\?.*)?$/, '');
  // document.currentScript is only set while this file first runs, so the attribute is read now.
  var floatingPosition = core.floatingButtonPosition(document.currentScript.getAttribute('data-floating-button'));
  var floatingButton = null;
  var STATE_TIMEOUT_MS = 3000;
  var serverState = null;
  var applyingServerState = false;

  function addStylesheet(href) {
    var link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    document.head.appendChild(link);
  }

  function addScript(src) {
    return new Promise(function (resolve, reject) {
      var tag = document.createElement('script');
      tag.src = src;
      tag.onload = resolve;
      tag.onerror = reject;
      document.head.appendChild(tag);
    });
  }

  function fetchState() {
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, STATE_TIMEOUT_MS);
    return fetch(base + '/state', { credentials: 'same-origin', signal: controller.signal })
      .then(function (response) {
        if (!response.ok) throw new Error('state ' + response.status);
        return response.json();
      })
      .finally(function () { clearTimeout(timer); });
  }

  var PENDING_KEY = 'dpdp_cc_pending';

  function readPending() {
    try {
      var pending = JSON.parse(window.localStorage.getItem(PENDING_KEY));
      return pending && typeof pending.revision === 'number' && pending.categories ? pending : null;
    } catch (e) {
      return null;
    }
  }

  function writePending(pending) {
    try {
      if (pending) window.localStorage.setItem(PENDING_KEY, JSON.stringify(pending));
      else window.localStorage.removeItem(PENDING_KEY);
    } catch (e) {
      // Storage unavailable: behave as if there is no pending decision.
    }
  }

  // One decision is sent at a time: two saves in flight would both be planned from the same
  // consents, and whichever finished last would win even if it was the older choice. Only the latest
  // choice waits, since it replaces any before it.
  var sending = false;
  var queued = null;

  function sendDecision(pending) {
    if (sending) {
      queued = pending;
      return;
    }
    sending = true;
    postDecision(pending).then(function () {
      sending = false;
      var next = queued;
      queued = null;
      if (next) sendDecision(next);
    });
  }

  function postDecision(pending) {
    return fetch(base + '/decision', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ revision: pending.revision, categories: pending.categories }),
    }).then(function (response) {
      if (response.ok) {
        return response.json().then(function (state) {
          serverState = state;
          // The waiting choice now follows this saved one, so it is kept against the new basis.
          if (queued) queued.basis = state.basis;
          writePending(queued);
        });
      }
      if (response.status === 409) {
        // The categories changed after this page loaded; reload to show the new banner.
        writePending(null);
        window.location.reload();
      }
      // Any other status keeps the pending decision, which is sent again on the next page load.
    }).catch(function () {
      // Network error: the pending decision stays and is sent again on the next page load.
    });
  }

  var COOKIE_ICON = '<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">'
    + '<path d="M12 3a9 9 0 1 0 9 9 4 4 0 0 1-4-4 4 4 0 0 1-4-4 1 1 0 0 0-1-1z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>'
    + '<circle cx="8.5" cy="11" r="1.3" fill="currentColor"/><circle cx="11.5" cy="16" r="1.3" fill="currentColor"/>'
    + '<circle cx="16" cy="14" r="1.1" fill="currentColor"/></svg>';

  // The library's stylesheet unsets every button inside #cc-main, so these rules repeat the id to win.
  var FLOATING_CSS = '#cc-main .dpdp-cc-fab{all:unset;position:fixed;bottom:16px;z-index:1;width:48px;height:48px;'
    + 'border-radius:50%;display:flex;align-items:center;justify-content:center;cursor:pointer;'
    + 'background:var(--cc-btn-primary-bg);color:var(--cc-btn-primary-color);box-shadow:0 2px 8px rgba(0,0,0,.25);'
    + 'transition:background-color .2s}'
    + '#cc-main .dpdp-cc-fab--bottom-left{left:16px}#cc-main .dpdp-cc-fab--bottom-right{right:16px}'
    + '#cc-main .dpdp-cc-fab:hover{background:var(--cc-btn-primary-hover-bg);color:var(--cc-btn-primary-hover-color)}'
    + '#cc-main .dpdp-cc-fab:focus-visible{outline:2px solid var(--cc-btn-primary-bg);outline-offset:3px}'
    + '#cc-main .dpdp-cc-fab[hidden],.show--preferences #cc-main .dpdp-cc-fab{display:none}'
    + '@media print{#cc-main .dpdp-cc-fab{display:none}}';

  function addFloatingButton(label) {
    var container = document.getElementById('cc-main');
    if (!floatingPosition || !container) return;
    var style = document.createElement('style');
    style.textContent = FLOATING_CSS;
    document.head.appendChild(style);
    floatingButton = document.createElement('button');
    floatingButton.type = 'button';
    floatingButton.className = 'dpdp-cc-fab dpdp-cc-fab--' + floatingPosition;
    floatingButton.setAttribute('aria-label', label);
    floatingButton.title = label;
    floatingButton.innerHTML = COOKIE_ICON;
    floatingButton.addEventListener('click', function () { CookieConsent.showPreferences(); });
    container.appendChild(floatingButton);
    updateFloatingButton();
  }

  // Hidden while the banner asks for a first choice, which it already offers.
  function updateFloatingButton() {
    if (floatingButton) floatingButton.hidden = !CookieConsent.validConsent();
  }

  function onUserDecision() {
    updateFloatingButton();
    if (applyingServerState) return;
    var pending = {
      revision: serverState.revision,
      basis: serverState.basis,
      categories: core.decisionFromAccepted(CookieConsent.getUserPreferences().acceptedCategories, serverState),
    };
    writePending(pending);
    sendDecision(pending);
  }

  function applyAccepted(accepted) {
    applyingServerState = true;
    try {
      CookieConsent.acceptCategory(accepted);
    } finally {
      applyingServerState = false;
    }
  }

  function deleteLibraryCookie() {
    // The library would otherwise treat a stale choice from this browser as valid consent.
    core.consentCookieDeletions(core.PREFS_COOKIE, location.hostname).forEach(function (cookie) {
      document.cookie = cookie;
    });
  }

  function deleteCookie(name) {
    core.consentCookieDeletions(name, location.hostname).forEach(function (cookie) {
      document.cookie = cookie;
    });
  }

  // CookieConsent.run executes scripts of every category in the stored consent before the loader can
  // correct it, so stale consent and declined categories' cookies are removed before run.
  function alignStoredState(state, accepted) {
    if (core.needsReset(core.storedConsent(document.cookie, core.PREFS_COOKIE), accepted, state.revision)) {
      deleteLibraryCookie();
    }
    core.declinedCookieNames(state, accepted).forEach(deleteCookie);
  }

  function start(state) {
    serverState = state;
    var pending = readPending();
    if (pending && !core.pendingApplies(pending, state)) {
      writePending(null);
      pending = null;
    }
    var action = core.startAction(state, pending);
    if (action.type === 'ask') {
      deleteLibraryCookie();
      // Remove any optional catalog cookies from previous consent holders to avoid carryover
      core.declinedCookieNames(state, []).forEach(deleteCookie);
    }
    else alignStoredState(state, action.accepted);
    var config = core.buildRunConfig(state);
    config.onFirstConsent = onUserDecision;
    config.onChange = onUserDecision;
    // The library builds #cc-main only when a modal first opens; the button needs it at once for its theme.
    if (floatingPosition) config.lazyHtmlGeneration = false;
    return CookieConsent.run(config).then(function () {
      if (action.type === 'ask') {
        CookieConsent.show(true);
        addFloatingButton(state.config.texts.preferencesTitle);
        return;
      }
      var local = CookieConsent.validConsent() ? CookieConsent.getUserPreferences().acceptedCategories : null;
      if (!local || !core.sameOptionalCategories(local, action.accepted)) applyAccepted(action.accepted);
      CookieConsent.hide();
      addFloatingButton(state.config.texts.preferencesTitle);
      if (action.type === 'retryPending') sendDecision(pending);
    });
  }

  addStylesheet(base + '/vendor/cookieconsent.css');
  Promise.all([addScript(base + '/vendor/cookieconsent.umd.js'), fetchState()])
    .then(function (results) { return start(results[1]); })
    .catch(function () {
      // Server or library unavailable: optional scripts stay blocked until a later page load works.
      console.warn('[cookie-consent] banner unavailable');
    });
})();
