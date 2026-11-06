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

'use strict';

const { createIsClient } = require('./isClient');
const { createCatalog } = require('./catalog');
const { createCookieConsentService } = require('./cookieConsentService');
const { createRouter } = require('./router');
const { readSubjectCookie, writeSubjectCookie } = require('./subjectCookie');
const DEFAULT_TEXTS = require('./defaultTexts');

const MIN_SECRET_LENGTH = 32;
const REQUIRED_STRINGS = ['isBaseUrl', 'clientId', 'clientSecret', 'serviceId', 'siteName', 'cookieSecret'];

function validate(options) {
  for (const name of REQUIRED_STRINGS) {
    if (typeof options[name] !== 'string' || options[name].trim() === '') {
      throw new Error(`createCookieConsent: "${name}" is required`);
    }
  }
  if (options.cookieSecret.length < MIN_SECRET_LENGTH) {
    throw new Error(`createCookieConsent: "cookieSecret" must be at least ${MIN_SECRET_LENGTH} characters`);
  }
  if (typeof options.getUsername !== 'function') {
    throw new Error('createCookieConsent: "getUsername" must be a function');
  }
  if (!Array.isArray(options.categories) || options.categories.length === 0) {
    throw new Error('createCookieConsent: "categories" must list at least one cookie category');
  }
  for (const c of options.categories) {
    if (!c || !/^[a-z][a-z0-9_]*$/.test(c.key || '') || c.key === 'necessary' || !c.purposeName || !c.title) {
      throw new Error('createCookieConsent: each category needs a lowercase "key" (not "necessary"), a "purposeName" and a "title"');
    }
  }
}

function withoutTrailingSlashes(url) {
  let end = url.length;
  while (end > 0 && url[end - 1] === '/') end -= 1;
  return url.slice(0, end);
}

function createCookieConsent(options) {
  validate(options);
  const onError = options.onError || ((e) => console.error(`[cookie-consent] ${e.message}`));
  const cookieOptions = { secure: options.secureCookies !== false, maxAgeDays: options.expiryDays || 365 };
  const isClient = createIsClient({
    baseUrl: withoutTrailingSlashes(options.isBaseUrl),
    clientId: options.clientId,
    clientSecret: options.clientSecret,
    fetch: options.fetch,
    onApiCall: options.onApiCall,
    subjectQuery: options.subjectQuery,
  });
  const service = createCookieConsentService({
    isClient,
    catalog: createCatalog({ isClient, categories: options.categories }),
    serviceId: options.serviceId,
    siteName: options.siteName,
    texts: { ...DEFAULT_TEXTS, ...(options.texts || {}) },
    basisSecret: options.cookieSecret,
    expiryDays: options.expiryDays,
    onError,
  });

  return {
    router() {
      return createRouter({ service, cookieSecret: options.cookieSecret, cookieOptions, getUsername: options.getUsername, onError });
    },

    async linkOnLogin(req, res, username) {
      try {
        const cookie = await service.linkOnLogin({ username, cookie: readSubjectCookie(req, options.cookieSecret) });
        writeSubjectCookie(res, options.cookieSecret, cookie, cookieOptions);
      } catch (e) {
        onError(e);
      }
    },
  };
}

module.exports = { createCookieConsent };
