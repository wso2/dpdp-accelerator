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
import { OxygenUIThemeProvider, AcrylicOrangeTheme, CssBaseline } from '@wso2/oxygen-ui'
import App from './App'
import i18n from './i18n/i18n'
import queryClient from './utils/queryClient'

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('Root element not found. Check index.html for <div id="root">.')
}

createRoot(rootElement).render(
  <StrictMode>
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <CssBaseline />
      <I18nextProvider i18n={i18n}>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter basename={import.meta.env.BASE_URL.replace(/\/$/, '')}>
            <App />
          </BrowserRouter>
        </QueryClientProvider>
      </I18nextProvider>
    </OxygenUIThemeProvider>
  </StrictMode>,
)
