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

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { I18nextProvider } from 'react-i18next'
import { BrowserRouter } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import LocaleProvider from './i18n/LocaleProvider'
import { loadLanguageCatalogue } from './i18n/languages'
import queryClient from './utils/queryClient'

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('Root element not found. Check index.html for <div id="root">.')
}

// The catalogue names the languages on offer and has to be read before i18next
// starts, because the language it starts in is chosen from that list. Imported
// afterwards for the same reason: i18n.ts resolves the stored language as it
// loads, and a static import would run that before the catalogue arrived.
await loadLanguageCatalogue()
const { default: i18n, i18nReady } = await import('./i18n/i18n')

// Translations are fetched over the network, so the first paint waits for them.
// Rendering earlier shows raw keys for a frame, which is worse than a moment of
// blank page - index.html already carries the app's chrome.
await i18nReady

createRoot(rootElement).render(
  <StrictMode>
    <I18nextProvider i18n={i18n}>
      <LocaleProvider>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter basename={import.meta.env.BASE_URL.replace(/\/$/, '')}>
            <App />
          </BrowserRouter>
        </QueryClientProvider>
      </LocaleProvider>
    </I18nextProvider>
  </StrictMode>,
)
