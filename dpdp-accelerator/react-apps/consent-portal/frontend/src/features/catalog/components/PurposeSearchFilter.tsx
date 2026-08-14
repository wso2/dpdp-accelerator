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

import { Box, Button, SearchBar, Stack, TextField } from '@wso2/oxygen-ui'
import { Search, X } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

export interface PurposeSearchValue {
  name: string
  type: string
}

interface PurposeSearchFilterProps {
  value: PurposeSearchValue
  onSearch: (value: PurposeSearchValue) => void
}

const EMPTY_SEARCH: PurposeSearchValue = { name: '', type: '' }

/**
 * Name is matched as a substring (`co`), type as an exact match (`eq`) since
 * the Identity Server has no type enum to search within -- both combine with
 * `and` when set. The parent remounts this component (via a `key` tied to
 * the applied value) when the search changes elsewhere, so the draft is
 * seeded from props rather than synchronised in an effect.
 */
function PurposeSearchFilter({ value, onSearch }: PurposeSearchFilterProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [draft, setDraft] = useState(value)
  const canReset = Boolean(draft.name || draft.type || value.name || value.type)

  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
      <Box sx={{ flex: 1, maxWidth: { sm: 280 } }}>
        <SearchBar
          size="small"
          fullWidth
          value={draft.name}
          placeholder={t('catalog.purposes.searchPlaceholder')}
          onChange={(event) => setDraft({ ...draft, name: event.target.value })}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              onSearch(draft)
            }
          }}
        />
      </Box>
      <TextField
        size="small"
        sx={{ width: { xs: '100%', sm: 200 }, flexShrink: 0 }}
        label={t('catalog.purposes.typeFilterLabel')}
        value={draft.type}
        onChange={(event) => setDraft({ ...draft, type: event.target.value })}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            onSearch(draft)
          }
        }}
      />
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
          setDraft(EMPTY_SEARCH)
          onSearch(EMPTY_SEARCH)
        }}
      >
        {t('catalog.actions.reset')}
      </Button>
    </Stack>
  )
}

export default PurposeSearchFilter
