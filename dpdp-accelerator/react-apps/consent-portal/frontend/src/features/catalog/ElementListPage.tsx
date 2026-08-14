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
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@wso2/oxygen-ui'
import { CircleSlash, Plus, RefreshCw, Search } from '@wso2/oxygen-ui-icons-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'
import CursorPaginationFooter from '../../components/CursorPaginationFooter'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import type { CursorPageParams } from '../../types/catalog'
import { APIError } from '../../utils/apiClient'
import { getNextCursor, getPreviousCursor } from '../../utils/cursorPagination'
import { PORTAL_SCOPES } from '../../utils/portalScopes'
import useAuthorization from '../auth/useAuthorization'
import { buildElementNameFilter } from './api/catalogApi'
import ElementFormDialog from './components/ElementFormDialog'
import ElementSearchFilter from './components/ElementSearchFilter'
import { useCreateElementMutation, useElementsQuery } from './hooks/useCatalogQueries'
import { CATALOG_ROWS_PER_PAGE_OPTIONS } from './constants'
import { getCursorPageParams, toCatalogSearchParams } from './utils/catalogSearchParams'

function ElementListPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const params = useMemo(() => getCursorPageParams(searchParams), [searchParams])
  const nameSearch = searchParams.get('name') ?? ''
  const query = useElementsQuery({ ...params, filter: buildElementNameFilter(nameSearch) })
  const rows = query.data?.Elements ?? []
  const { hasScope } = useAuthorization()
  const canWrite = hasScope(PORTAL_SCOPES.ELEMENTS_WRITE)
  const [createOpen, setCreateOpen] = useState(false)
  const createMutation = useCreateElementMutation()

  // A 409 here has one well-known cause -- a duplicate name -- so it gets a
  // precise, actionable message regardless of the upstream's own wording.
  let createErrorMessage: string | undefined
  if (createMutation.error) {
    createErrorMessage =
      createMutation.error instanceof APIError && createMutation.error.status === 409
        ? t('catalog.elementForm.duplicateName', { name: createMutation.variables?.name ?? '' })
        : t('catalog.elementForm.createFailed')
  }

  // Paging must keep the active search; only a new search resets to page one.
  const updateParams = (nextParams: CursorPageParams): void => {
    const next = toCatalogSearchParams(nextParams)
    if (nameSearch) {
      next.set('name', nameSearch)
    }
    setSearchParams(next, { replace: true })
  }

  const applySearch = (nextName: string): void => {
    const next = toCatalogSearchParams({ limit: params.limit })
    const trimmed = nextName.trim()
    if (trimmed) {
      next.set('name', trimmed)
    }
    setSearchParams(next, { replace: true })
  }

  const openElement = (elementId: string): void => {
    navigate(`/elements/${encodeURIComponent(elementId)}`)
  }

  const closeCreateDialog = (): void => {
    setCreateOpen(false)
    createMutation.reset()
  }

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
          <Stack spacing={1}>
            <HeaderBreadcrumbs />
            <Typography variant="h4" fontWeight={700}>
              {t('catalog.elements.title')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t('catalog.elements.subtitle')}
            </Typography>
          </Stack>
          {canWrite ? (
            <Button
              variant="contained"
              startIcon={<Plus size={16} />}
              sx={{ flexShrink: 0 }}
              onClick={() => setCreateOpen(true)}
            >
              {t('catalog.actions.addElement')}
            </Button>
          ) : null}
        </Stack>

        <ElementSearchFilter key={nameSearch} value={nameSearch} onSearch={applySearch} />

        <TableContainer component={Paper} elevation={1}>
          <Table aria-label={t('catalog.elements.tableLabel')} sx={{ tableLayout: 'fixed' }}>
            <TableHead
              sx={(theme) => ({
                '& .MuiTableCell-head': {
                  fontWeight: 600,
                  ...theme.applyStyles('light', { backgroundColor: theme.palette.grey[50] }),
                  ...theme.applyStyles('dark', { backgroundColor: 'rgba(255, 255, 255, 0.04)' }),
                },
              })}
            >
              <TableRow>
                <TableCell sx={{ width: '30%' }}>{t('catalog.fields.element')}</TableCell>
                <TableCell sx={{ width: '25%' }}>{t('catalog.fields.displayName')}</TableCell>
                <TableCell sx={{ width: '45%' }}>{t('catalog.fields.description')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {query.isPending
                ? Array.from({ length: 5 }).map((_, index) => (
                    <TableRow key={`element-skeleton-${String(index)}`}>
                      {Array.from({ length: 3 }).map((__, cell) => (
                        <TableCell key={`element-skeleton-${String(index)}-${String(cell)}`}>
                          <Skeleton />
                        </TableCell>
                      ))}
                    </TableRow>
                  ))
                : rows.map((element) => (
                    <TableRow
                      hover
                      key={element.id}
                      tabIndex={0}
                      sx={{ cursor: 'pointer' }}
                      onClick={() => openElement(element.id)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') openElement(element.id)
                      }}
                    >
                      <TableCell>
                        <Typography component="code" variant="body2" fontWeight={600} noWrap>
                          {element.name}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" noWrap>
                          {element.displayName ?? '-'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography
                          variant="body2"
                          color={element.description ? 'text.primary' : 'text.secondary'}
                          title={element.description}
                        >
                          {element.description ?? t('catalog.values.noDescription')}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
              {query.isError ? (
                <TableRow>
                  <TableCell colSpan={3} align="center" sx={{ py: 8 }}>
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
                  <TableCell colSpan={3} align="center" sx={{ py: 8 }}>
                    <Stack spacing={1} alignItems="center">
                      <Search size={28} aria-hidden="true" />
                      <Typography fontWeight={600}>{t('catalog.elements.emptyTitle')}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {nameSearch
                          ? t('catalog.elements.emptySearch', { term: nameSearch })
                          : t('catalog.elements.empty')}
                      </Typography>
                    </Stack>
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
          <CursorPaginationFooter
            rowsPerPage={params.limit}
            rowsPerPageOptions={CATALOG_ROWS_PER_PAGE_OPTIONS}
            hasPreviousPage={Boolean(getPreviousCursor(query.data?.links))}
            hasNextPage={Boolean(getNextCursor(query.data?.links))}
            disabled={query.isPending || query.isPlaceholderData}
            onRowsPerPageChange={(limit) => updateParams({ limit })}
            onPreviousPage={() =>
              updateParams({
                limit: params.limit,
                before: getPreviousCursor(query.data?.links),
              })
            }
            onNextPage={() =>
              updateParams({
                limit: params.limit,
                after: getNextCursor(query.data?.links),
              })
            }
          />
        </TableContainer>
      </Stack>

      <ElementFormDialog
        open={createOpen}
        loading={createMutation.isPending}
        error={createErrorMessage}
        onClose={closeCreateDialog}
        onSubmit={(payload) => {
          createMutation.mutate(payload, {
            onSuccess: (created) => {
              setCreateOpen(false)
              openElement(created.id)
            },
          })
        }}
      />
    </Box>
  )
}

export default ElementListPage
