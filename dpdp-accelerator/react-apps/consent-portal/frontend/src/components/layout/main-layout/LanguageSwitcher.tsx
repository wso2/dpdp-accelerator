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

import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { IconButton, ListItemText, Menu, MenuItem, Tooltip } from '@wso2/oxygen-ui'
import { Globe } from '@wso2/oxygen-ui-icons-react'
import useLanguage from '../../../i18n/useLanguage'

/**
 * Language switcher for the app header. Each option is labelled in its own
 * script, so a speaker who cannot read the current language can still find
 * theirs; the English name goes in the secondary line for accessibility.
 */
function LanguageSwitcher(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { current, languages, setLanguage } = useLanguage()
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null)

  const handleSelect = (code: string): void => {
    setLanguage(code)
    setAnchorEl(null)
  }

  return (
    <>
      <Tooltip title={t('language.label')}>
        <IconButton
          aria-label={t('language.selectAria')}
          aria-haspopup="menu"
          aria-expanded={anchorEl ? true : undefined}
          onClick={(event) => setAnchorEl(event.currentTarget)}
        >
          <Globe />
        </IconButton>
      </Tooltip>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
        slotProps={{ list: { 'aria-label': t('language.selectAria') } }}
      >
        {languages.map((language) => (
          <MenuItem
            key={language.code}
            selected={language.code === current.code}
            lang={language.code}
            onClick={() => handleSelect(language.code)}
          >
            <ListItemText primary={language.endonym} secondary={language.english} />
          </MenuItem>
        ))}
      </Menu>
    </>
  )
}

export default LanguageSwitcher
