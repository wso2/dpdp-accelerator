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
import { I18nextProvider } from 'react-i18next'
import { afterEach, describe, expect, it } from 'vitest'
import { OxygenTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import i18n from '../i18n/i18n'
import ConsentPurposesSection from '../features/consent-registry/components/details/ConsentPurposesSection'
import type { ConsentPurposeItem } from '../types/consent'

/**
 * Purposes and elements are created by administrators after the portal ships,
 * so their wording lives in resources/<lang>/catalog.ts rather than common.ts.
 * These cover the half of that mechanism the sync script cannot: that the
 * portal actually renders the translated wording, and falls back safely when a
 * language has none.
 */

const PURPOSE: ConsentPurposeItem = {
  purposeId: 'purpose-1',
  name: 'marketing_communications',
  version: 'v1',
  displayName: 'Marketing Communications',
  description: 'Sending you offers, newsletters and product updates',
  elements: [
    {
      elementId: 'element-1',
      name: 'account_balance',
      namespace: 'default',
      version: 'v1',
      displayName: 'Account Balance',
      description: 'Your current balance',
      approved: true,
      mandatory: true,
    },
  ],
}

function renderSection(): void {
  render(
    <I18nextProvider i18n={i18n}>
      <OxygenUIThemeProvider theme={OxygenTheme}>
        <ConsentPurposesSection purposes={[PURPOSE]} />
      </OxygenUIThemeProvider>
    </I18nextProvider>,
  )
}

afterEach(async () => {
  cleanup()
  i18n.removeResourceBundle('ta', 'catalog')
  await i18n.changeLanguage('en')
})

describe('catalog translations', () => {
  it('renders the translated wording for the active language', async () => {
    i18n.addResourceBundle('ta', 'catalog', {
      purposes: {
        marketing_communications: {
          displayName: 'சந்தைப்படுத்தல் தொடர்பு',
          description: 'உங்களுக்கு சலுகைகளை அனுப்புகிறோம்',
        },
      },
      elements: {
        account_balance: { displayName: 'கணக்கு இருப்பு', description: 'தற்போதைய இருப்பு' },
      },
    })
    await i18n.changeLanguage('ta')

    renderSection()

    expect(screen.getByText('சந்தைப்படுத்தல் தொடர்பு')).toBeTruthy()
    expect(screen.getByText('கணக்கு இருப்பு')).toBeTruthy()
    expect(screen.getByText('தற்போதைய இருப்பு')).toBeTruthy()
    expect(screen.queryByText('Marketing Communications')).toBeNull()
  })

  it('falls back to the English the server returned when the language has no entry', async () => {
    await i18n.changeLanguage('ta')

    renderSection()

    expect(screen.getByText('Marketing Communications')).toBeTruthy()
    expect(screen.getByText('Account Balance')).toBeTruthy()
  })

  it('treats a blank entry as untranslated rather than rendering empty text', async () => {
    // This is the shape `pnpm i18n:catalog` writes for anything not yet done.
    i18n.addResourceBundle('ta', 'catalog', {
      purposes: { marketing_communications: { displayName: '', description: '' } },
      elements: { account_balance: { displayName: '', description: '' } },
    })
    await i18n.changeLanguage('ta')

    renderSection()

    expect(screen.getByText('Marketing Communications')).toBeTruthy()
    expect(screen.getByText('Account Balance')).toBeTruthy()
  })

  it('prefers a version-specific entry so a reworded version cannot show old wording', async () => {
    i18n.addResourceBundle('ta', 'catalog', {
      purposes: {
        marketing_communications: { displayName: 'பழைய தலைப்பு', description: '' },
        'marketing_communications@v1': { displayName: 'புதிய தலைப்பு', description: '' },
      },
      elements: {},
    })
    await i18n.changeLanguage('ta')

    renderSection()

    expect(screen.getByText('புதிய தலைப்பு')).toBeTruthy()
    expect(screen.queryByText('பழைய தலைப்பு')).toBeNull()
  })
})
