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

/* Pure helpers for loader.js, free of DOM access so they can be unit tested in Node. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.DPDPCookieConsentCore = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var PREFS_COOKIE = 'dpdp_cc_prefs';

  function buildRunConfig(state) {
    var texts = state.config.texts;
    var categories = { necessary: { enabled: true, readOnly: true } };
    var sections = [{ title: texts.necessaryTitle, description: texts.necessaryDescription, linkedCategory: 'necessary' }];
    state.config.categories.forEach(function (category) {
      categories[category.key] = {
        autoClear: { cookies: category.cookies.map(function (cookie) { return { name: cookie.name }; }) },
      };
      var section = { title: category.title, description: category.description, linkedCategory: category.key };
      if (category.cookies.length > 0) {
        section.cookieTable = {
          headers: { name: texts.cookieName, desc: texts.cookieDescription },
          body: category.cookies.map(function (cookie) { return { name: cookie.name, desc: cookie.description }; }),
        };
      }
      sections.push(section);
    });
    return {
      mode: 'opt-in',
      autoShow: false,
      revision: state.revision,
      cookie: { name: PREFS_COOKIE, expiresAfterDays: 365 },
      guiOptions: {
        consentModal: { layout: 'box', position: 'bottom right', equalWeightButtons: true },
        preferencesModal: { layout: 'box', equalWeightButtons: true },
      },
      categories: categories,
      language: {
        default: 'en',
        translations: {
          en: {
            consentModal: {
              title: texts.bannerTitle,
              description: texts.bannerDescription,
              acceptAllBtn: texts.acceptAll,
              acceptNecessaryBtn: texts.rejectAll,
              showPreferencesBtn: texts.managePreferences,
            },
            preferencesModal: {
              title: texts.preferencesTitle,
              acceptAllBtn: texts.acceptAll,
              acceptNecessaryBtn: texts.rejectAll,
              savePreferencesBtn: texts.savePreferences,
              closeIconLabel: texts.close,
              sections: sections,
            },
          },
        },
      },
    };
  }

  function acceptedFromState(state) {
    return Object.keys(state.categories).filter(function (key) { return state.categories[key] === true; });
  }

  function decisionFromAccepted(accepted, state) {
    var decision = {};
    state.config.categories.forEach(function (category) {
      decision[category.key] = accepted.indexOf(category.key) !== -1;
    });
    return decision;
  }

  function sameOptionalCategories(a, b) {
    var left = a.filter(function (k) { return k !== 'necessary'; }).sort();
    var right = b.filter(function (k) { return k !== 'necessary'; }).sort();
    return left.length === right.length && left.every(function (k, i) { return k === right[i]; });
  }

  // An unsent decision was made on a state with this basis. Any other basis means the save went
  // through, a later decision or withdrawal overtook it, or another account is now signed in, and
  // sending it again would undo that.
  function pendingApplies(pending, state) {
    return !!pending && pending.revision === state.revision && typeof pending.basis === 'string'
      && pending.basis === state.basis;
  }

  // The browser's own consent cookie is deliberately not an input: only the server's decision or
  // the user's unsent decision may stand for consent.
  function startAction(state, pending) {
    if (pendingApplies(pending, state)) {
      var accepted = Object.keys(pending.categories).filter(function (key) { return pending.categories[key] === true; });
      return { type: 'retryPending', accepted: accepted };
    }
    if (state.hasDecision) return { type: 'applyServer', accepted: acceptedFromState(state) };
    return { type: 'ask' };
  }

  // vanilla-cookieconsent 3.1.0 writes its cookie with Domain=location.hostname whenever the hostname
  // contains a dot; a host-only expiry does not remove such a cookie, so expire both variants.
  function consentCookieDeletions(name, hostname) {
    var base = name + '=; Max-Age=0; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    var out = [base];
    if (hostname && hostname.indexOf('.') !== -1) out.push(base + '; Domain=' + hostname);
    return out;
  }

  // vanilla-cookieconsent 3.1.0 stores encodeURIComponent(JSON.stringify({categories, revision, ...})).
  function storedConsent(cookieHeader, name) {
    try {
      var parts = String(cookieHeader || '').split(';');
      for (var i = 0; i < parts.length; i++) {
        var part = parts[i].replace(/^\s+/, '');
        if (part.indexOf(name + '=') !== 0) continue;
        var parsed = JSON.parse(decodeURIComponent(part.slice(name.length + 1)));
        if (parsed && Array.isArray(parsed.categories) && typeof parsed.revision === 'number') {
          return { categories: parsed.categories, revision: parsed.revision };
        }
        return null;
      }
    } catch (e) {
      // Unparseable cookie: treated as no stored consent.
    }
    return null;
  }

  // The library runs scripts from stored consent at start-up, so any stored consent that is not
  // exactly the target must be cleared before it starts.
  function needsReset(stored, target, revision) {
    return !stored || stored.revision !== revision || !sameOptionalCategories(stored.categories, target);
  }

  function declinedCookieNames(state, target) {
    var names = [];
    state.config.categories.forEach(function (category) {
      if (target.indexOf(category.key) === -1) {
        category.cookies.forEach(function (cookie) { names.push(cookie.name); });
      }
    });
    return names;
  }

  var FLOATING_POSITIONS = ['bottom-left', 'bottom-right'];

  /** The floating button's corner from the snippet's data-floating-button attribute, or null for none. */
  function floatingButtonPosition(value) {
    var position = String(value || '').trim().toLowerCase();
    return FLOATING_POSITIONS.indexOf(position) === -1 ? null : position;
  }

  return {
    floatingButtonPosition: floatingButtonPosition,
    storedConsent: storedConsent,
    needsReset: needsReset,
    declinedCookieNames: declinedCookieNames,
    PREFS_COOKIE: PREFS_COOKIE,
    consentCookieDeletions: consentCookieDeletions,
    buildRunConfig: buildRunConfig,
    acceptedFromState: acceptedFromState,
    decisionFromAccepted: decisionFromAccepted,
    sameOptionalCategories: sameOptionalCategories,
    startAction: startAction,
    pendingApplies: pendingApplies,
  };
});
