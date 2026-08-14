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

import { useEffect, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { CacheProvider } from '@emotion/react'
import createCache from '@emotion/cache'
import { prefixer } from 'stylis'
import {
  AcrylicOrangeTheme,
  createTheme,
  CssBaseline,
  OxygenUIThemeProvider,
} from '@wso2/oxygen-ui'
import { applyLanguageSideEffects } from './languages'

/**
 * The portal renders left-to-right in every language it offers, including
 * Kashmiri, Sindhi and Urdu, which are written in Perso-Arabic script. Only the
 * translated text changes; the layout does not mirror.
 */

const cache = createCache({ key: 'oxy', stylisPlugins: [prefixer] })

const theme = createTheme(AcrylicOrangeTheme, { direction: 'ltr' })

export interface LocaleProviderProps {
  children: ReactNode
}

/**
 * Supplies the theme and keeps the document's lang attribute in step with the
 * chosen language. Must sit inside I18nextProvider.
 */
function LocaleProvider({ children }: LocaleProviderProps): React.JSX.Element {
  const { i18n } = useTranslation('common')
  const language = i18n.resolvedLanguage ?? i18n.language

  useEffect(() => {
    applyLanguageSideEffects(language)
  }, [language])

  // The cache must sit *inside* OxygenUIThemeProvider: that provider renders
  // <StyledEngineProvider injectFirst>, which installs its own Emotion cache and
  // would otherwise replace ours.
  return (
    <OxygenUIThemeProvider theme={theme}>
      <CacheProvider value={cache}>
        <CssBaseline />
        {children}
      </CacheProvider>
    </OxygenUIThemeProvider>
  )
}

export default LocaleProvider
