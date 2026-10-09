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
  AdapterDateFns,
  Box,
  Button,
  DatePickers,
  FormControl,
  InputLabel,
  MenuItem,
  Popover,
  SearchBar,
  Select,
  Stack,
  Typography,
} from '@wso2/oxygen-ui'
import { ListFilter } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CONSENT_RELATIONS, CONSENT_STATES } from '../../../types/consent'
import type { ConsentRegistryFilters as ConsentRegistryFiltersModel } from '../../../types/consent'
import { parseDateOnly } from '../../../utils/dateTime'
import { getConsentStateLabelKey } from '../utils/statusChip'

interface ConsentRegistryFiltersProps {
  filters: ConsentRegistryFiltersModel
  isPendingView: boolean
  onFilterChange: (nextFilters: ConsentRegistryFiltersModel) => void
  onClear: () => void
}

const MAIN_FILTER_HEIGHT = 40

function toDateOnly(date: Date | null): string {
  if (!date || Number.isNaN(date.getTime())) {
    return ''
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${String(year)}-${month}-${day}`
}

function ConsentRegistryFilters({
  filters,
  isPendingView,
  onFilterChange,
  onClear,
}: ConsentRegistryFiltersProps): React.JSX.Element {
  const { t } = useTranslation('common')
  // The parent remounts this component when the applied filters change, so the
  // draft is seeded from the props rather than synchronised in an effect.
  const [serviceIdDraft, setServiceIdDraft] = useState(filters.serviceId)
  const [dateDraft, setDateDraft] = useState({
    createdAfter: filters.createdAfter,
    createdBefore: filters.createdBefore,
  })
  const [dateAnchor, setDateAnchor] = useState<HTMLElement | null>(null)
  const dateFilterOpen = Boolean(dateAnchor)
  const hasDateFilter = Boolean(filters.createdAfter || filters.createdBefore)

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
          disabled={isPendingView}
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

        <FormControl
          size="small"
          sx={{ width: { xs: '100%', sm: 200 }, height: MAIN_FILTER_HEIGHT, flexShrink: 0 }}
        >
          <InputLabel id="consent-relation-label">
            {t('consentRegistry.filters.relation')}
          </InputLabel>
          <Select
            labelId="consent-relation-label"
            id="consent-relation"
            value={filters.relation}
            label={t('consentRegistry.filters.relation')}
            sx={{ height: MAIN_FILTER_HEIGHT }}
            onChange={(event) => {
              onFilterChange({
                ...filters,
                serviceId: serviceIdDraft,
                relation: event.target.value as ConsentRegistryFiltersModel['relation'],
              })
            }}
          >
            {CONSENT_RELATIONS.map((relation) => (
              <MenuItem key={relation} value={relation}>
                {t(`consentRegistry.filters.relationOptions.${relation.toLowerCase()}`)}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <Box component="span" sx={{ position: 'relative', flexShrink: 0 }}>
          <Button
            variant={hasDateFilter ? 'contained' : 'outlined'}
            color={dateFilterOpen || hasDateFilter ? 'primary' : 'inherit'}
            startIcon={<ListFilter size={16} />}
            aria-haspopup="dialog"
            aria-expanded={dateFilterOpen}
            sx={{
              height: MAIN_FILTER_HEIGHT,
              width: { xs: '100%', sm: 'auto' },
              whiteSpace: 'nowrap',
            }}
            onClick={(event) => {
              setDateDraft({
                createdAfter: filters.createdAfter,
                createdBefore: filters.createdBefore,
              })
              setDateAnchor(event.currentTarget)
            }}
          >
            {t('consentRegistry.filters.advanced')}
          </Button>
        </Box>

        <Button
          variant="text"
          aria-label={t('consentRegistry.filters.clearAriaLabel')}
          onClick={onClear}
        >
          {t('consentRegistry.filters.clear')}
        </Button>
      </Stack>

      <Popover
        open={dateFilterOpen}
        anchorEl={dateAnchor}
        onClose={() => setDateAnchor(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        transformOrigin={{ vertical: 'top', horizontal: 'left' }}
        slotProps={{
          paper: { sx: { width: { xs: 'calc(100vw - 32px)', sm: 420 }, mt: 1, p: 2.5 } },
        }}
      >
        <Stack spacing={2.5}>
          <Typography variant="subtitle2" fontWeight={600}>
            {t('consentRegistry.filters.advanced')}
          </Typography>
          <DatePickers.LocalizationProvider dateAdapter={AdapterDateFns}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <DatePickers.DatePicker
                label={t('consentRegistry.filters.startDate')}
                value={parseDateOnly(dateDraft.createdAfter) ?? null}
                onChange={(date) => setDateDraft({ ...dateDraft, createdAfter: toDateOnly(date) })}
                slotProps={{
                  textField: {
                    size: 'small',
                    fullWidth: true,
                    inputProps: { 'aria-label': t('consentRegistry.filters.startDateAriaLabel') },
                  },
                }}
              />
              <DatePickers.DatePicker
                label={t('consentRegistry.filters.endDate')}
                value={parseDateOnly(dateDraft.createdBefore) ?? null}
                onChange={(date) => setDateDraft({ ...dateDraft, createdBefore: toDateOnly(date) })}
                slotProps={{
                  textField: {
                    size: 'small',
                    fullWidth: true,
                    inputProps: { 'aria-label': t('consentRegistry.filters.endDateAriaLabel') },
                  },
                }}
              />
            </Stack>
          </DatePickers.LocalizationProvider>

          <Stack
            direction="row"
            justifyContent="space-between"
            sx={{ pt: 2, borderTop: 1, borderColor: 'divider' }}
          >
            <Button
              variant="text"
              onClick={() => {
                setDateDraft({ createdAfter: '', createdBefore: '' })
                setDateAnchor(null)
                onFilterChange({
                  ...filters,
                  serviceId: serviceIdDraft,
                  createdAfter: '',
                  createdBefore: '',
                })
              }}
            >
              {t('consentRegistry.filters.clear')}
            </Button>
            <Stack direction="row" spacing={1}>
              <Button onClick={() => setDateAnchor(null)}>
                {t('consentRegistry.filters.cancel')}
              </Button>
              <Button
                variant="contained"
                onClick={() => {
                  onFilterChange({ ...filters, serviceId: serviceIdDraft, ...dateDraft })
                  setDateAnchor(null)
                }}
              >
                {t('consentRegistry.filters.apply')}
              </Button>
            </Stack>
          </Stack>
        </Stack>
      </Popover>
    </Box>
  )
}

export default ConsentRegistryFilters
