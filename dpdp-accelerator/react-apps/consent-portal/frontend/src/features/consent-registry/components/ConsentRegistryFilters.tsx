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
  IconButton,
  InputLabel,
  MenuItem,
  Popover,
  SearchBar,
  Select,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@wso2/oxygen-ui'
import { ListFilter } from '@wso2/oxygen-ui-icons-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ConsentRegistryFilters as ConsentRegistryFiltersModel } from '../../../types/consent'

interface ConsentRegistryFiltersProps {
  filters: ConsentRegistryFiltersModel
  onFilterChange: (nextFilters: ConsentRegistryFiltersModel) => void
  onClear: () => void
}

const MAIN_FILTER_HEIGHT = 40

function parseDate(value: string): Date | null {
  if (!value) return null

  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(year, month - 1, day)

  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
    ? date
    : null
}

function formatDate(value: Date | null): string {
  if (!value) return ''

  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

function ConsentRegistryFilters({
  filters,
  onFilterChange,
  onClear,
}: ConsentRegistryFiltersProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [draft, setDraft] = useState(filters)
  const [filtersAnchor, setFiltersAnchor] = useState<HTMLElement | null>(null)
  const filtersOpen = Boolean(filtersAnchor)
  const advancedFilterCount = [filters.groupIds, filters.startDate, filters.endDate].filter(
    Boolean,
  ).length

  useEffect(() => {
    setDraft(filters)
  }, [filters])

  const applyPurposeSearch = (): void => {
    onFilterChange({ ...filters, purposeName: draft.purposeName })
  }

  const cancelAdvancedChanges = (): void => {
    setDraft(filters)
    setFiltersAnchor(null)
  }

  return (
    <Box component="section" aria-label={t('consentRegistry.filters.sectionAriaLabel')}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
        <Box sx={{ position: 'relative', flex: 1, height: MAIN_FILTER_HEIGHT }}>
          <SearchBar
            size="small"
            fullWidth
            value={draft.purposeName}
            placeholder={t('consentRegistry.filters.purposeSearchPlaceholder')}
            onChange={(event) => {
              setDraft({ ...draft, purposeName: event.target.value })
            }}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                applyPurposeSearch()
              }
            }}
            sx={{ '& .MuiInputBase-root': { height: MAIN_FILTER_HEIGHT, pr: 6 } }}
          />
          <Box
            sx={{
              position: 'absolute',
              top: '50%',
              right: 4,
              transform: 'translateY(-50%)',
              pl: 0.5,
              borderLeft: 1,
              borderColor: 'divider',
              display: 'flex',
            }}
          >
            <Tooltip title={t('consentRegistry.filters.advanced')}>
              <IconButton
                size="small"
                color={filtersOpen ? 'primary' : 'default'}
                aria-label={t('consentRegistry.filters.advanced')}
                aria-haspopup="dialog"
                aria-expanded={filtersOpen}
                onClick={(event) => setFiltersAnchor(event.currentTarget)}
              >
                <ListFilter size={17} />
              </IconButton>
            </Tooltip>
            {advancedFilterCount > 0 ? (
              <Box
                component="span"
                sx={{
                  position: 'absolute',
                  top: -3,
                  right: -3,
                  minWidth: 16,
                  height: 16,
                  px: 0.4,
                  borderRadius: 8,
                  bgcolor: 'primary.main',
                  color: 'primary.contrastText',
                  fontSize: '0.625rem',
                  lineHeight: '16px',
                  textAlign: 'center',
                  pointerEvents: 'none',
                }}
              >
                {advancedFilterCount}
              </Box>
            ) : null}
          </Box>
        </Box>

        <FormControl
          size="small"
          sx={{
            width: { xs: '100%', sm: 220 },
            height: MAIN_FILTER_HEIGHT,
            flexShrink: 0,
          }}
        >
          <InputLabel id="consent-status-label">{t('consentRegistry.filters.status')}</InputLabel>
          <Select
            labelId="consent-status-label"
            id="consent-status"
            value={filters.status}
            label={t('consentRegistry.filters.status')}
            sx={{ height: MAIN_FILTER_HEIGHT }}
            onChange={(event) => {
              onFilterChange({
                ...filters,
                purposeName: draft.purposeName,
                status: event.target.value as ConsentRegistryFiltersModel['status'],
              })
            }}
          >
            <MenuItem value="All">{t('consentRegistry.status.all')}</MenuItem>
            <MenuItem value="Active">{t('consentRegistry.status.active')}</MenuItem>
            <MenuItem value="Pending">{t('consentRegistry.status.pending')}</MenuItem>
            <MenuItem value="Rejected">{t('consentRegistry.status.rejected')}</MenuItem>
            <MenuItem value="Revoked">{t('consentRegistry.status.revoked')}</MenuItem>
            <MenuItem value="Expired">{t('consentRegistry.status.expired')}</MenuItem>
          </Select>
        </FormControl>
      </Stack>

      <Popover
        open={filtersOpen}
        anchorEl={filtersAnchor}
        onClose={cancelAdvancedChanges}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{
          paper: {
            sx: {
              width: { xs: 'calc(100vw - 32px)', sm: 680 },
              maxWidth: 'calc(100vw - 32px)',
              mt: 1,
              p: 2.5,
            },
          },
        }}
      >
        <Stack spacing={2.5}>
          <Stack sx={{ pb: 1.5, borderBottom: 1, borderColor: 'divider' }}>
            <Typography variant="subtitle2" fontWeight={600}>
              {t('consentRegistry.filters.advanced')}
            </Typography>
          </Stack>

          <TextField
            size="small"
            fullWidth
            label={t('consentRegistry.filters.groupIds')}
            value={draft.groupIds}
            onChange={(event) => setDraft({ ...draft, groupIds: event.target.value })}
          />

          <DatePickers.LocalizationProvider dateAdapter={AdapterDateFns}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <DatePickers.DatePicker
                label={t('consentRegistry.filters.startDate')}
                value={parseDate(draft.startDate)}
                onChange={(value) => setDraft({ ...draft, startDate: formatDate(value) })}
                slotProps={{
                  textField: {
                    size: 'small',
                    fullWidth: true,
                    inputProps: {
                      'aria-label': t('consentRegistry.filters.startDateAriaLabel'),
                    },
                  },
                }}
              />
              <DatePickers.DatePicker
                label={t('consentRegistry.filters.endDate')}
                value={parseDate(draft.endDate)}
                onChange={(value) => setDraft({ ...draft, endDate: formatDate(value) })}
                slotProps={{
                  textField: {
                    size: 'small',
                    fullWidth: true,
                    inputProps: {
                      'aria-label': t('consentRegistry.filters.endDateAriaLabel'),
                    },
                  },
                }}
              />
            </Stack>
          </DatePickers.LocalizationProvider>

          <Stack
            direction="row"
            spacing={1}
            justifyContent="space-between"
            sx={{ pt: 2, borderTop: 1, borderColor: 'divider' }}
          >
            <Button
              variant="text"
              aria-label={t('consentRegistry.filters.clearAriaLabel')}
              onClick={() => {
                setFiltersAnchor(null)
                onClear()
              }}
            >
              {t('consentRegistry.filters.clear')}
            </Button>
            <Stack direction="row" spacing={1}>
              <Button onClick={cancelAdvancedChanges}>{t('consentRegistry.filters.cancel')}</Button>
              <Button
                variant="contained"
                onClick={() => {
                  onFilterChange(draft)
                  setFiltersAnchor(null)
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
