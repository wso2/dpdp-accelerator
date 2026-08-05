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
import { CONSENT_STATES } from '../../../types/consent'
import { getConsentStateLabelKey } from '../../consent-registry/utils/statusChip'
import {
  EMPTY_ADMIN_CONSENT_FILTERS,
  normalizeAdminConsentFilters,
} from '../utils/adminConsentFilters'

interface AdminConsentFiltersProps {
  filters: AdminConsentRegistryFilters
  onFilterChange: (filters: AdminConsentRegistryFilters) => void
  onClear: () => void
}

const MAIN_FILTER_HEIGHT = 40

export default function AdminConsentFilters({
  filters,
  onFilterChange,
  onClear,
}: AdminConsentFiltersProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [draft, setDraft] = useState(filters)
  const [filtersAnchor, setFiltersAnchor] = useState<HTMLElement | null>(null)
  const filtersOpen = Boolean(filtersAnchor)
  const advancedFilterCount = [filters.subjectId, filters.serviceId].filter(Boolean).length

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
          title={filters.consentId ? t('adminConsents.filters.removeConsentIdForState') : ''}
        >
          <Box component="span" sx={{ width: { xs: '100%', sm: 220 }, flexShrink: 0 }}>
            <FormControl size="small" fullWidth disabled={Boolean(filters.consentId)}>
              <InputLabel id="admin-consent-state-label">
                {t('consentRegistry.filters.state')}
              </InputLabel>
              <Select
                labelId="admin-consent-state-label"
                value={filters.state}
                label={t('consentRegistry.filters.state')}
                sx={{ height: MAIN_FILTER_HEIGHT }}
                onChange={(event) =>
                  applyFilters({
                    ...filters,
                    consentId: draft.consentId,
                    state: event.target.value as AdminConsentRegistryFilters['state'],
                  })
                }
              >
                <MenuItem value="All">{t('consentRegistry.status.all')}</MenuItem>
                {CONSENT_STATES.map((state) => (
                  <MenuItem key={state} value={state}>
                    {t(`consentRegistry.status.${getConsentStateLabelKey(state)}`)}
                  </MenuItem>
                ))}
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
              width: { xs: 'calc(100vw - 32px)', sm: 560 },
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
              label={t('adminConsents.filters.subjectId')}
              helperText={t('adminConsents.filters.subjectIdHelp')}
              value={draft.subjectId}
              onChange={(event) => setDraft({ ...draft, subjectId: event.target.value })}
            />
            <TextField
              size="small"
              fullWidth
              label={t('adminConsents.filters.serviceId')}
              value={draft.serviceId}
              onChange={(event) => setDraft({ ...draft, serviceId: event.target.value })}
            />
          </Stack>
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
