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
  Chip,
  IconButton,
  MenuItem,
  Paper,
  Popover,
  SearchBar,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@wso2/oxygen-ui'
import { CircleSlash, ListFilter, Plus, RefreshCw, Search } from '@wso2/oxygen-ui-icons-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import useAuthorization from '../auth/useAuthorization'
import type { ElementFilters, ElementType } from '../../types/catalog'
import { formatEpochTimestamp } from '../../utils/dateTime'
import { PORTAL_SCOPES } from '../../utils/portalScopes'
import ElementFormDialog from './components/ElementFormDialog'
import ElementTypeChip from './components/ElementTypeChip'
import { useCreateElementMutation, useElementsQuery } from './hooks/useCatalogQueries'
import { ELEMENT_TYPE_PRESENTATION } from './utils/elementTypePresentation'

const ROW_OPTIONS = [10, 25, 50]
const EMPTY_FILTERS: ElementFilters = { name: '', namespace: '', type: 'All', version: '' }

function getPage(searchParams: URLSearchParams): number {
  const page = Number(searchParams.get('page') ?? '1')
  return Number.isInteger(page) && page > 0 ? page - 1 : 0
}

function getRowsPerPage(searchParams: URLSearchParams): number {
  const rows = Number(searchParams.get('rowsPerPage') ?? '10')
  return ROW_OPTIONS.includes(rows) ? rows : 10
}

function getFilters(searchParams: URLSearchParams): ElementFilters {
  const type = searchParams.get('type')
  return {
    name: searchParams.get('name') ?? '',
    namespace: searchParams.get('namespace') ?? '',
    type: type === 'basic' || type === 'json' || type === 'xml' ? type : 'All',
    version: searchParams.get('version') ?? '',
  }
}

interface ElementFiltersPanelProps {
  initialFilters: ElementFilters
  onApply: (filters: ElementFilters) => void
  onClear: () => void
}

function ElementFiltersPanel({
  initialFilters,
  onApply,
  onClear,
}: ElementFiltersPanelProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [draft, setDraft] = useState(initialFilters)
  const [filtersAnchor, setFiltersAnchor] = useState<HTMLElement | null>(null)
  const filtersOpen = Boolean(filtersAnchor)
  const advancedFilterCount = [
    draft.namespace,
    draft.type === 'All' ? '' : draft.type,
    draft.version,
  ].filter(Boolean).length

  const applySearch = (name: string): void => {
    const nextFilters = { ...draft, name }
    setDraft(nextFilters)
    onApply(nextFilters)
  }
  const cancelAdvancedChanges = (): void => {
    setDraft({ ...initialFilters, name: draft.name })
    setFiltersAnchor(null)
  }

  return (
    <Box sx={{ position: 'relative', width: '100%' }}>
      <SearchBar
        size="small"
        fullWidth
        value={draft.name}
        placeholder={t('catalog.elements.searchPlaceholder')}
        onChange={(event) => {
          const name = event.target.value
          setDraft({
            ...draft,
            name,
            version: name.trim() || draft.namespace.trim() ? draft.version : '',
          })
        }}
        onKeyDown={(event) => {
          if (event.key === 'Enter') applySearch(draft.name)
        }}
        sx={{ '& .MuiInputBase-root': { pr: 6 } }}
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
        <Tooltip title={t('catalog.actions.filters')}>
          <IconButton
            size="small"
            color={filtersOpen ? 'primary' : 'default'}
            aria-label={t('catalog.actions.filters')}
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

      <Popover
        open={filtersOpen}
        anchorEl={filtersAnchor}
        onClose={cancelAdvancedChanges}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{
          paper: {
            sx: {
              width: { xs: 'calc(100vw - 32px)', sm: 620 },
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
              {t('catalog.elements.filterTitle')}
            </Typography>
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              size="small"
              fullWidth
              label={t('catalog.fields.namespace')}
              value={draft.namespace}
              onChange={(event) => {
                const namespace = event.target.value
                setDraft({
                  ...draft,
                  namespace,
                  version: draft.name.trim() || namespace.trim() ? draft.version : '',
                })
              }}
            />
            <TextField
              select
              size="small"
              fullWidth
              label={t('catalog.fields.type')}
              value={draft.type}
              onChange={(event) =>
                setDraft({ ...draft, type: event.target.value as ElementType | 'All' })
              }
            >
              <MenuItem value="All">{t('catalog.values.all')}</MenuItem>
              {(['basic', 'json', 'xml'] as const).map((elementType) => (
                <MenuItem key={elementType} value={elementType}>
                  {ELEMENT_TYPE_PRESENTATION[elementType].label}
                </MenuItem>
              ))}
            </TextField>
            <Tooltip
              arrow
              title={t('catalog.help.elementVersionRequiresIdentity')}
              disableHoverListener={Boolean(draft.name.trim() || draft.namespace.trim())}
            >
              <Box sx={{ width: '100%' }}>
                <TextField
                  size="small"
                  fullWidth
                  disabled={!draft.name.trim() && !draft.namespace.trim()}
                  label={t('catalog.fields.version')}
                  value={draft.version}
                  onChange={(event) => setDraft({ ...draft, version: event.target.value })}
                />
              </Box>
            </Tooltip>
          </Stack>
          <Stack
            direction="row"
            spacing={1}
            justifyContent="space-between"
            sx={{ pt: 2, borderTop: 1, borderColor: 'divider' }}
          >
            <Button
              variant="text"
              onClick={() => {
                setDraft(EMPTY_FILTERS)
                setFiltersAnchor(null)
                onClear()
              }}
            >
              {t('catalog.actions.clearAll')}
            </Button>
            <Stack direction="row" spacing={1}>
              <Button onClick={cancelAdvancedChanges}>{t('catalog.actions.cancel')}</Button>
              <Button
                variant="contained"
                onClick={() => {
                  onApply(draft)
                  setFiltersAnchor(null)
                }}
              >
                {t('catalog.actions.apply')}
              </Button>
            </Stack>
          </Stack>
        </Stack>
      </Popover>
    </Box>
  )
}

function ElementListPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [createOpen, setCreateOpen] = useState(false)
  const filters = useMemo(() => getFilters(searchParams), [searchParams])
  const page = useMemo(() => getPage(searchParams), [searchParams])
  const rowsPerPage = useMemo(() => getRowsPerPage(searchParams), [searchParams])
  const query = useElementsQuery(filters, page, rowsPerPage)
  const createMutation = useCreateElementMutation()
  const { hasScope } = useAuthorization()
  const canWriteElements = hasScope(PORTAL_SCOPES.ELEMENTS_WRITE)

  const updateParams = (
    nextFilters: ElementFilters,
    nextPage = 0,
    nextRowsPerPage = rowsPerPage,
  ): void => {
    const params = new URLSearchParams()
    if (nextFilters.name.trim()) params.set('name', nextFilters.name.trim())
    if (nextFilters.namespace.trim()) params.set('namespace', nextFilters.namespace.trim())
    if (nextFilters.type !== 'All') params.set('type', nextFilters.type)
    if (nextFilters.version.trim()) params.set('version', nextFilters.version.trim())
    if (nextPage > 0) params.set('page', String(nextPage + 1))
    if (nextRowsPerPage !== 10) params.set('rowsPerPage', String(nextRowsPerPage))
    setSearchParams(params, { replace: true })
  }

  const rows = query.data?.data ?? []
  const activeFilters = Object.entries(filters).filter(([, value]) =>
    Boolean(value && value !== 'All'),
  ) as Array<[keyof ElementFilters, string]>
  const filterLabels: Record<keyof ElementFilters, string> = {
    name: t('catalog.fields.name'),
    namespace: t('catalog.fields.namespace'),
    type: t('catalog.fields.type'),
    version: t('catalog.fields.version'),
  }
  const removeFilter = (key: keyof ElementFilters): void => {
    const nextFilters: ElementFilters = {
      ...filters,
      [key]: key === 'type' ? 'All' : '',
    }
    if (key === 'name' && !filters.namespace) nextFilters.version = ''
    if (key === 'namespace' && !filters.name) nextFilters.version = ''
    updateParams(nextFilters)
  }

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          justifyContent="space-between"
          alignItems={{ sm: 'flex-end' }}
          spacing={2}
        >
          <Stack spacing={1}>
            <HeaderBreadcrumbs />
            <Typography variant="h4" fontWeight={700}>
              {t('catalog.elements.title')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t('catalog.elements.subtitle')}
            </Typography>
          </Stack>
          {canWriteElements ? (
            <Button
              variant="contained"
              startIcon={<Plus size={18} />}
              onClick={() => setCreateOpen(true)}
            >
              {t('catalog.elements.add')}
            </Button>
          ) : null}
        </Stack>

        <ElementFiltersPanel
          key={`element-filters-${searchParams.toString()}`}
          initialFilters={filters}
          onApply={(nextFilters) => updateParams(nextFilters)}
          onClear={() => setSearchParams({}, { replace: true })}
        />

        {activeFilters.length > 0 ? (
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
            <Typography variant="caption" color="text.secondary" sx={{ mr: 0.5 }}>
              {t('catalog.elements.activeFilters')}
            </Typography>
            {activeFilters.map(([key, value]) => (
              <Chip
                key={key}
                size="small"
                variant="outlined"
                label={`${filterLabels[key]}: ${value}`}
                onDelete={() => removeFilter(key)}
              />
            ))}
            <Button
              size="small"
              sx={{ height: 24, minWidth: 0, py: 0 }}
              onClick={() => setSearchParams({}, { replace: true })}
            >
              {t('catalog.actions.clearAll')}
            </Button>
          </Stack>
        ) : null}

        <TableContainer component={Paper} elevation={1}>
          <Box
            sx={{
              px: 2,
              py: 1.5,
              borderBottom: 1,
              borderColor: 'divider',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <Typography variant="subtitle2" fontWeight={600}>
              {t('catalog.elements.results', { count: query.data?.metadata.total ?? 0 })}
            </Typography>
            {query.isFetching && !query.isPending ? (
              <Typography variant="caption" color="text.secondary">
                {t('catalog.elements.refreshing')}
              </Typography>
            ) : null}
          </Box>
          <Table aria-label={t('catalog.elements.tableLabel')} sx={{ tableLayout: 'fixed' }}>
            <TableHead
              sx={(theme) => ({
                '& .MuiTableCell-head': {
                  fontWeight: 600,
                  ...theme.applyStyles('light', {
                    backgroundColor: theme.palette.grey[50],
                  }),
                  ...theme.applyStyles('dark', {
                    backgroundColor: 'rgba(255, 255, 255, 0.04)',
                  }),
                },
              })}
            >
              <TableRow>
                <TableCell sx={{ width: '20%' }}>{t('catalog.fields.element')}</TableCell>
                <TableCell sx={{ width: '14%' }}>{t('catalog.fields.namespace')}</TableCell>
                <TableCell sx={{ width: '11%' }}>{t('catalog.fields.type')}</TableCell>
                <TableCell sx={{ width: '10%' }}>{t('catalog.fields.version')}</TableCell>
                <TableCell sx={{ width: '28%' }}>{t('catalog.fields.description')}</TableCell>
                <TableCell sx={{ width: '17%' }}>{t('catalog.fields.created')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {query.isPending
                ? Array.from({ length: 5 }).map((_, index) => (
                    <TableRow key={`element-skeleton-${String(index)}`}>
                      {Array.from({ length: 6 }).map((__, cell) => (
                        <TableCell key={`element-skeleton-${String(index)}-${String(cell)}`}>
                          <Skeleton />
                        </TableCell>
                      ))}
                    </TableRow>
                  ))
                : rows.map((element) => (
                    <TableRow
                      hover
                      key={element.elementId}
                      tabIndex={0}
                      sx={{ cursor: 'pointer' }}
                      onClick={() => navigate(`/elements/${encodeURIComponent(element.elementId)}`)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          navigate(`/elements/${encodeURIComponent(element.elementId)}`)
                        }
                      }}
                    >
                      <TableCell>
                        <Stack spacing={0.35}>
                          <Typography variant="body2" fontWeight={600} noWrap>
                            {element.displayName ?? element.name}
                          </Typography>
                          <Typography
                            component="code"
                            variant="caption"
                            color="text.secondary"
                            noWrap
                          >
                            {element.name}
                          </Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" noWrap title={element.namespace}>
                          {element.namespace}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <ElementTypeChip type={element.type} />
                      </TableCell>
                      <TableCell>
                        <Chip size="small" color="primary" label={element.version} />
                      </TableCell>
                      <TableCell>
                        <Typography
                          variant="body2"
                          color={element.description ? 'text.primary' : 'text.secondary'}
                          title={element.description}
                          sx={{
                            display: '-webkit-box',
                            WebkitLineClamp: 2,
                            WebkitBoxOrient: 'vertical',
                            overflow: 'hidden',
                          }}
                        >
                          {element.description ?? t('catalog.values.noDescription')}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography fontFamily="monospace" variant="body2">
                          {formatEpochTimestamp(element.createdTime)}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
              {query.isError ? (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 8 }}>
                    <Stack spacing={1} alignItems="center">
                      <CircleSlash size={28} aria-hidden="true" />
                      <Typography fontWeight={500}>{t('catalog.elements.loadFailed')}</Typography>
                      <Button
                        size="small"
                        variant="outlined"
                        startIcon={<RefreshCw size={16} />}
                        onClick={() => query.refetch()}
                      >
                        {t('catalog.actions.retry')}
                      </Button>
                    </Stack>
                  </TableCell>
                </TableRow>
              ) : null}
              {!query.isPending && !query.isError && rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 8 }}>
                    <Stack spacing={1} alignItems="center">
                      <Search size={28} aria-hidden="true" />
                      <Typography fontWeight={600}>{t('catalog.elements.emptyTitle')}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {t('catalog.elements.empty')}
                      </Typography>
                    </Stack>
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
          <TablePagination
            component="div"
            count={query.data?.metadata.total ?? 0}
            page={page}
            rowsPerPage={rowsPerPage}
            rowsPerPageOptions={ROW_OPTIONS}
            onPageChange={(_, nextPage) => updateParams(filters, nextPage)}
            onRowsPerPageChange={(event) => updateParams(filters, 0, Number(event.target.value))}
          />
        </TableContainer>
      </Stack>

      <ElementFormDialog
        key={`create-element-${String(createOpen)}`}
        open={createOpen}
        initialValue={undefined}
        loading={createMutation.isPending}
        error={createMutation.error?.message}
        onClose={() => {
          setCreateOpen(false)
          createMutation.reset()
        }}
        onCreate={(payload) => {
          createMutation.mutate(payload, {
            onSuccess: (element) => {
              setCreateOpen(false)
              navigate(`/elements/${encodeURIComponent(element.elementId)}`)
            },
          })
        }}
        onCreateVersion={undefined}
      />
    </Box>
  )
}

export default ElementListPage
