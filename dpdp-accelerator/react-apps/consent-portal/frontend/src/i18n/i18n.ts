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

import i18n from 'i18next'
import HttpBackend from 'i18next-http-backend'
import { initReactI18next } from 'react-i18next'
import { applyLanguageSideEffects, DEFAULT_LANGUAGE, readStoredLanguage } from './languages'

/**
 * Translations are fetched at run time from `public/i18n/<lang>/<ns>.json`
 * rather than compiled into the bundle.
 *
 * Two things follow from that, and both are the point of doing it:
 *
 *   - a reader downloads only their own language, not all 23
 *   - the wording of an existing key can be corrected by editing the JSON in
 *     the deployed folder, with no rebuild, because JSON is data and only code
 *     is compiled
 *
 * Adding a new key is still a code change: the component that reads it has to
 * be written and built either way.
 *
 * Two namespaces live under each language: `common` is our own UI text, and
 * `catalog` is the wording of purposes and elements, which administrators
 * create at run time. English is the complete set; every other language falls
 * back to English for any key it is missing.
 */
const loadPath = `${import.meta.env.BASE_URL}i18n/{{lng}}/{{ns}}.json`.replace(/([^:])\/\//g, '$1/')

const initialLanguage = readStoredLanguage()

/**
 * Resolves once the initial language has been fetched.
 *
 * The entry point awaits this before the first render. Without it React paints
 * before any translation has arrived and the reader sees raw keys for a frame -
 * the alternative, a Suspense fallback, trades that flash for a spinner on
 * every language change.
 */
export const i18nReady = i18n
  .use(HttpBackend)
  .use(initReactI18next)
  .init({
    backend: { loadPath },
    lng: initialLanguage,
    fallbackLng: DEFAULT_LANGUAGE,
    defaultNS: 'common',
    ns: ['common', 'catalog'],
    // The fallback language is fetched alongside the active one, so a key the
    // translator has not reached yet resolves to English rather than to its
    // own name.
    partialBundledLanguages: false,
    interpolation: {
      // React escapes every interpolated value before it reaches the DOM, so
      // escaping here as well would double-encode. That holds only while no
      // translation is rendered as raw markup - the production security check
      // forbids the React prop that would allow it, which is what keeps this
      // safe even for wording an administrator supplies.
      escapeValue: false,
    },
    react: {
      // The entry point awaits i18nReady instead, so no component needs a
      // Suspense boundary of its own.
      useSuspense: false,
    },
  })

applyLanguageSideEffects(initialLanguage)

export default i18n
