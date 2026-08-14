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

import { Box, Button, SearchBar, Stack } from '@wso2/oxygen-ui'
import { Search, X } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

interface ElementSearchFilterProps {
  value: string
  onSearch: (value: string) => void
}

/**
 * The parent remounts this component (via a `key` tied to the applied
 * value) when the search changes elsewhere, so the draft is seeded from
 * props rather than synchronised in an effect.
 */
function ElementSearchFilter({ value, onSearch }: ElementSearchFilterProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [draft, setDraft] = useState(value)
  const canReset = Boolean(draft || value)

  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
      <Box sx={{ flex: 1, maxWidth: { sm: 320 } }}>
        <SearchBar
          size="small"
          fullWidth
          value={draft}
          placeholder={t('catalog.elements.searchPlaceholder')}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              onSearch(draft)
            }
          }}
        />
      </Box>
      <Button
        size="small"
        variant="outlined"
        startIcon={<Search size={16} />}
        onClick={() => onSearch(draft)}
      >
        {t('catalog.actions.search')}
      </Button>
      <Button
        size="small"
        variant="text"
        startIcon={<X size={16} />}
        disabled={!canReset}
        onClick={() => {
          setDraft('')
          onSearch('')
        }}
      >
        {t('catalog.actions.reset')}
      </Button>
    </Stack>
  )
}

export default ElementSearchFilter
