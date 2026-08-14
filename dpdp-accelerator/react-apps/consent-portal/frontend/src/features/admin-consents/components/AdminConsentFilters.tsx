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
  Autocomplete,
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
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { AdminConsentRegistryFilters } from '../../../types/consent'
import type { ElementSummary } from '../../../types/catalog'
import ElementVersionSelect from '../../catalog/components/ElementVersionSelect'
import { useElementOptionsQuery } from '../../catalog/hooks/useCatalogQueries'
import {
  EMPTY_ADMIN_CONSENT_FILTERS,
  normalizeAdminConsentFilters,
} from '../utils/adminConsentFilters'

interface AdminConsentFiltersProps {
  filters: AdminConsentRegistryFilters
  canReadElements: boolean
  onFilterChange: (filters: AdminConsentRegistryFilters) => void
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
  return [
    value.getFullYear(),
    String(value.getMonth() + 1).padStart(2, '0'),
    String(value.getDate()).padStart(2, '0'),
  ].join('-')
}

function elementLabel(element: ElementSummary): string {
  return `${element.displayName ?? element.name} (${element.namespace})`
}

export default function AdminConsentFilters({
  filters,
  canReadElements,
  onFilterChange,
  onClear,
}: AdminConsentFiltersProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [draft, setDraft] = useState(filters)
  const [filtersAnchor, setFiltersAnchor] = useState<HTMLElement | null>(null)
  const filtersOpen = Boolean(filtersAnchor)
  const elementOptionsQuery = useElementOptionsQuery(filtersOpen && canReadElements)
  const elementOptions = elementOptionsQuery.data?.data ?? []
  const selectedElement =
    elementOptions.find(
      (element) =>
        element.name === draft.elementName && element.namespace === draft.elementNamespace,
    ) ?? null
  const advancedFilterCount = [
    filters.purposeName,
    filters.userIds,
    filters.groupIds,
    filters.purposeVersion,
    filters.elementName,
    filters.elementNamespace,
    filters.elementVersion,
    filters.startDate,
    filters.endDate,
  ].filter(Boolean).length

  const applyFilters = (next: AdminConsentRegistryFilters): void => {
    const normalized = normalizeAdminConsentFilters(next)
    setDraft(normalized)
    onFilterChange(normalized)
  }

  const cancelAdvancedChanges = (): void => {
    setDraft(filters)
    setFiltersAnchor(null)
  }

  return (
    <Box component="section" aria-label={t('adminConsents.filters.sectionAriaLabel')}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
        <Box sx={{ position: 'relative', flex: 1, height: MAIN_FILTER_HEIGHT }}>
          <SearchBar
            size="small"
            fullWidth
            value={draft.consentId}
            placeholder={t('adminConsents.filters.consentIdSearchPlaceholder')}
            onChange={(event) => setDraft({ ...draft, consentId: event.target.value })}
            onKeyDown={(event) => {
              if (event.key === 'Enter') applyFilters(draft)
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
            <Tooltip
              title={
                filters.consentId
                  ? t('adminConsents.filters.removeConsentIdForAdvanced')
                  : t('consentRegistry.filters.advanced')
              }
            >
              <Box component="span" sx={{ display: 'inline-flex' }}>
                <IconButton
                  size="small"
                  color={filtersOpen ? 'primary' : 'default'}
                  disabled={Boolean(filters.consentId)}
                  aria-label={t('consentRegistry.filters.advanced')}
                  aria-haspopup="dialog"
                  aria-expanded={filtersOpen}
                  onClick={(event) => {
                    setDraft(filters)
                    setFiltersAnchor(event.currentTarget)
                  }}
                >
                  <ListFilter size={17} />
                </IconButton>
              </Box>
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
        <Tooltip
          title={filters.consentId ? t('adminConsents.filters.removeConsentIdForStatus') : ''}
        >
          <Box component="span" sx={{ width: { xs: '100%', sm: 220 }, flexShrink: 0 }}>
            <FormControl size="small" fullWidth disabled={Boolean(filters.consentId)}>
              <InputLabel id="admin-consent-status-label">
                {t('consentRegistry.filters.status')}
              </InputLabel>
              <Select
                labelId="admin-consent-status-label"
                value={filters.status}
                label={t('consentRegistry.filters.status')}
                sx={{ height: MAIN_FILTER_HEIGHT }}
                onChange={(event) =>
                  applyFilters({
                    ...filters,
                    consentId: draft.consentId,
                    status: event.target.value as AdminConsentRegistryFilters['status'],
                  })
                }
              >
                {(['All', 'Active', 'Pending', 'Rejected', 'Revoked', 'Expired'] as const).map(
                  (status) => (
                    <MenuItem key={status} value={status}>
                      {t(
                        `consentRegistry.status.${status === 'All' ? 'all' : status.toLowerCase()}`,
                      )}
                    </MenuItem>
                  ),
                )}
              </Select>
            </FormControl>
          </Box>
        </Tooltip>
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
              width: { xs: 'calc(100vw - 32px)', sm: 720 },
              maxWidth: 'calc(100vw - 32px)',
              mt: 1,
              p: 2.5,
            },
          },
        }}
      >
        <Stack spacing={2.5}>
          <Typography variant="subtitle2" fontWeight={600}>
            {t('consentRegistry.filters.advanced')}
          </Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              size="small"
              fullWidth
              label={t('adminConsents.filters.userIds')}
              helperText={t('adminConsents.filters.commaSeparatedUsers')}
              value={draft.userIds}
              onChange={(event) => setDraft({ ...draft, userIds: event.target.value })}
            />
            <TextField
              size="small"
              fullWidth
              label={t('adminConsents.filters.groupIds')}
              helperText={t('catalog.help.commaSeparatedGroups')}
              value={draft.groupIds}
              onChange={(event) => setDraft({ ...draft, groupIds: event.target.value })}
            />
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              size="small"
              fullWidth
              label={t('catalog.fields.purposeName')}
              value={draft.purposeName}
              onChange={(event) => {
                const purposeName = event.target.value
                setDraft({
                  ...draft,
                  purposeName,
                  purposeVersion: purposeName.trim() ? draft.purposeVersion : '',
                })
              }}
            />
            <Tooltip
              arrow
              title={t('catalog.help.purposeVersionRequiresName')}
              disableHoverListener={Boolean(draft.purposeName.trim())}
            >
              <Box sx={{ width: '100%' }}>
                <TextField
                  size="small"
                  fullWidth
                  disabled={!draft.purposeName.trim()}
                  label={t('catalog.fields.purposeVersion')}
                  value={draft.purposeVersion}
                  onChange={(event) => setDraft({ ...draft, purposeVersion: event.target.value })}
                />
              </Box>
            </Tooltip>
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            {canReadElements ? (
              <Autocomplete
                size="small"
                fullWidth
                options={elementOptions}
                value={selectedElement}
                loading={elementOptionsQuery.isLoading}
                getOptionLabel={elementLabel}
                isOptionEqualToValue={(option, value) => option.elementId === value.elementId}
                onChange={(_, selected) =>
                  setDraft({
                    ...draft,
                    elementName: selected?.name ?? '',
                    elementNamespace: selected?.namespace ?? '',
                    elementVersion: '',
                  })
                }
                renderInput={(params) => (
                  // Oxygen UI Autocomplete requires forwarding its generated input props.
                  // eslint-disable-next-line react/jsx-props-no-spreading
                  <TextField {...params} label={t('catalog.fields.elementName')} />
                )}
              />
            ) : (
              <TextField
                size="small"
                fullWidth
                label={t('catalog.fields.elementName')}
                value={draft.elementName}
                onChange={(event) => {
                  const elementName = event.target.value
                  setDraft({
                    ...draft,
                    elementName,
                    elementVersion:
                      elementName.trim() || draft.elementNamespace.trim()
                        ? draft.elementVersion
                        : '',
                  })
                }}
              />
            )}
            <TextField
              size="small"
              fullWidth
              label={t('catalog.fields.elementNamespace')}
              value={draft.elementNamespace}
              onChange={(event) => {
                const elementNamespace = event.target.value
                setDraft({
                  ...draft,
                  elementNamespace,
                  elementVersion:
                    draft.elementName.trim() || elementNamespace.trim() ? draft.elementVersion : '',
                })
              }}
            />
            {canReadElements && selectedElement ? (
              <ElementVersionSelect
                elementId={selectedElement.elementId}
                latestVersion={selectedElement.version}
                value={draft.elementVersion}
                label={t('catalog.fields.elementVersion')}
                allowAny
                onChange={(elementVersion) => setDraft({ ...draft, elementVersion })}
              />
            ) : (
              <Tooltip
                arrow
                title={t('catalog.help.elementVersionRequiresIdentity')}
                disableHoverListener={Boolean(
                  draft.elementName.trim() || draft.elementNamespace.trim(),
                )}
              >
                <Box sx={{ width: '100%' }}>
                  <TextField
                    size="small"
                    fullWidth
                    disabled={!draft.elementName.trim() && !draft.elementNamespace.trim()}
                    label={t('catalog.fields.elementVersion')}
                    value={draft.elementVersion}
                    onChange={(event) => setDraft({ ...draft, elementVersion: event.target.value })}
                  />
                </Box>
              </Tooltip>
            )}
          </Stack>
          <DatePickers.LocalizationProvider dateAdapter={AdapterDateFns}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <DatePickers.DatePicker
                label={t('consentRegistry.filters.startDate')}
                value={parseDate(draft.startDate)}
                onChange={(value) => setDraft({ ...draft, startDate: formatDate(value) })}
                slotProps={{ textField: { size: 'small', fullWidth: true } }}
              />
              <DatePickers.DatePicker
                label={t('consentRegistry.filters.endDate')}
                value={parseDate(draft.endDate)}
                onChange={(value) => setDraft({ ...draft, endDate: formatDate(value) })}
                slotProps={{ textField: { size: 'small', fullWidth: true } }}
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
                setDraft(EMPTY_ADMIN_CONSENT_FILTERS)
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
                  applyFilters(draft)
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
