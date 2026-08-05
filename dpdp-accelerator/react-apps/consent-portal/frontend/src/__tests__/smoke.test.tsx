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

import { cleanup, render, screen } from '@testing-library/react'
import { I18nextProvider, useTranslation } from 'react-i18next'
import { afterEach, describe, expect, it } from 'vitest'
import i18n from '../i18n/i18n'

function I18nPlaceholder(): React.JSX.Element {
  const { t } = useTranslation('common')

  return <h1>{t('app.title')}</h1>
}

function I18nMissingKeyPlaceholder(): React.JSX.Element {
  const { t } = useTranslation('common')

  return <h1>{t('app.missingTitle')}</h1>
}

afterEach(() => {
  cleanup()
})

describe('app i18n setup', () => {
  it('renders translated heading text with i18n provider', () => {
    render(
      <I18nextProvider i18n={i18n}>
        <I18nPlaceholder />
      </I18nextProvider>,
    )

    expect(
      screen.getByRole('heading', {
        name: String(i18n.t('common:app.title')),
      }),
    ).toBeInTheDocument()
  })

  it('falls back gracefully when the translation key is missing', () => {
    render(
      <I18nextProvider i18n={i18n}>
        <I18nMissingKeyPlaceholder />
      </I18nextProvider>,
    )

    expect(
      screen.getByRole('heading', {
        name: 'app.missingTitle',
      }),
    ).toBeInTheDocument()
  })
})
