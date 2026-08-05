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

import {
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  SearchBar,
  Select,
  Stack,
} from '@wso2/oxygen-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CONSENT_STATES } from '../../../types/consent'
import type { ConsentRegistryFilters as ConsentRegistryFiltersModel } from '../../../types/consent'
import { getConsentStateLabelKey } from '../utils/statusChip'

interface ConsentRegistryFiltersProps {
  filters: ConsentRegistryFiltersModel
  onFilterChange: (nextFilters: ConsentRegistryFiltersModel) => void
  onClear: () => void
}

const MAIN_FILTER_HEIGHT = 40

function ConsentRegistryFilters({
  filters,
  onFilterChange,
  onClear,
}: ConsentRegistryFiltersProps): React.JSX.Element {
  const { t } = useTranslation('common')
  // The parent remounts this component when the applied filters change, so the
  // draft is seeded from the props rather than synchronised in an effect.
  const [serviceIdDraft, setServiceIdDraft] = useState(filters.serviceId)

  return (
    <Box component="section" aria-label={t('consentRegistry.filters.sectionAriaLabel')}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
        <Box sx={{ flex: 1 }}>
          <SearchBar
            size="small"
            fullWidth
            value={serviceIdDraft}
            placeholder={t('consentRegistry.filters.serviceSearchPlaceholder')}
            onChange={(event) => setServiceIdDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                onFilterChange({ ...filters, serviceId: serviceIdDraft })
              }
            }}
            sx={{ '& .MuiInputBase-root': { height: MAIN_FILTER_HEIGHT } }}
          />
        </Box>

        <FormControl
          size="small"
          sx={{ width: { xs: '100%', sm: 220 }, height: MAIN_FILTER_HEIGHT, flexShrink: 0 }}
        >
          <InputLabel id="consent-state-label">{t('consentRegistry.filters.state')}</InputLabel>
          <Select
            labelId="consent-state-label"
            id="consent-state"
            value={filters.state}
            label={t('consentRegistry.filters.state')}
            sx={{ height: MAIN_FILTER_HEIGHT }}
            onChange={(event) => {
              onFilterChange({
                ...filters,
                serviceId: serviceIdDraft,
                state: event.target.value as ConsentRegistryFiltersModel['state'],
              })
            }}
          >
            <MenuItem value="All">{t('consentRegistry.status.all')}</MenuItem>
            {CONSENT_STATES.map((state) => (
              <MenuItem key={state} value={state}>
                {t(`consentRegistry.status.${getConsentStateLabelKey(state)}`)}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <Button
          variant="text"
          aria-label={t('consentRegistry.filters.clearAriaLabel')}
          onClick={onClear}
        >
          {t('consentRegistry.filters.clear')}
        </Button>
      </Stack>
    </Box>
  )
}

export default ConsentRegistryFilters
