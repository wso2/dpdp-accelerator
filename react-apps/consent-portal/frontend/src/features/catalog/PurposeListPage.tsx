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
import { CircleSlash, RefreshCw, Search } from '@wso2/oxygen-ui-icons-react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'
import CursorPaginationFooter from '../../components/CursorPaginationFooter'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import type { CursorPageParams } from '../../types/catalog'
import { getNextCursor, getPreviousCursor } from '../../utils/cursorPagination'
import { CATALOG_ROWS_PER_PAGE_OPTIONS } from './constants'
import { usePurposesQuery } from './hooks/useCatalogQueries'
import { getCursorPageParams, toCatalogSearchParams } from './utils/catalogSearchParams'

function PurposeListPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const params = useMemo(() => getCursorPageParams(searchParams), [searchParams])
  const query = usePurposesQuery(params)
  const rows = query.data?.Purposes ?? []

  const updateParams = (nextParams: CursorPageParams): void => {
    setSearchParams(toCatalogSearchParams(nextParams), { replace: true })
  }

  const openPurpose = (purposeId: string): void => {
    navigate(`/purposes/${encodeURIComponent(purposeId)}`)
  }

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack spacing={1}>
          <HeaderBreadcrumbs />
          <Typography variant="h4" fontWeight={700}>
            {t('catalog.purposes.title')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('catalog.purposes.subtitle')}
          </Typography>
        </Stack>

        <TableContainer component={Paper} elevation={1}>
          <Table aria-label={t('catalog.purposes.tableLabel')} sx={{ tableLayout: 'fixed' }}>
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
                <TableCell sx={{ width: '28%' }}>{t('catalog.fields.purpose')}</TableCell>
                <TableCell sx={{ width: '14%' }}>{t('catalog.fields.type')}</TableCell>
                <TableCell sx={{ width: '14%' }}>{t('catalog.fields.latestVersion')}</TableCell>
                <TableCell sx={{ width: '44%' }}>{t('catalog.fields.description')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {query.isPending
                ? Array.from({ length: 5 }).map((_, index) => (
                    <TableRow key={`purpose-skeleton-${String(index)}`}>
                      {Array.from({ length: 4 }).map((__, cell) => (
                        <TableCell key={`purpose-skeleton-${String(index)}-${String(cell)}`}>
                          <Skeleton />
                        </TableCell>
                      ))}
                    </TableRow>
                  ))
                : rows.map((purpose) => (
                    <TableRow
                      hover
                      key={purpose.id}
                      tabIndex={0}
                      sx={{ cursor: 'pointer' }}
                      onClick={() => openPurpose(purpose.id)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') openPurpose(purpose.id)
                      }}
                    >
                      <TableCell>
                        <Typography component="code" variant="body2" fontWeight={600} noWrap>
                          {purpose.name}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip size="small" variant="outlined" label={purpose.type} />
                      </TableCell>
                      <TableCell>
                        {purpose.latestVersion ? (
                          <Chip
                            size="small"
                            color="primary"
                            label={purpose.latestVersion.version}
                          />
                        ) : (
                          '-'
                        )}
                      </TableCell>
                      <TableCell>
                        <Typography
                          variant="body2"
                          color={purpose.description ? 'text.primary' : 'text.secondary'}
                          title={purpose.description}
                        >
                          {purpose.description ?? t('catalog.values.noDescription')}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
              {query.isError ? (
                <TableRow>
                  <TableCell colSpan={4} align="center" sx={{ py: 8 }}>
                    <Stack spacing={1} alignItems="center">
                      <CircleSlash size={28} aria-hidden="true" />
                      <Typography fontWeight={500}>{t('catalog.purposes.loadFailed')}</Typography>
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
                  <TableCell colSpan={4} align="center" sx={{ py: 8 }}>
                    <Stack spacing={1} alignItems="center">
                      <Search size={28} aria-hidden="true" />
                      <Typography fontWeight={600}>{t('catalog.purposes.emptyTitle')}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {t('catalog.purposes.empty')}
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
    </Box>
  )
}

export default PurposeListPage
