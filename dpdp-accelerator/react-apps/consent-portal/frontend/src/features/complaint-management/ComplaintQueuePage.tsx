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

import { Box, Stack, StatCard, Typography } from '@wso2/oxygen-ui'
import { AlertTriangle, CheckCircle2, Clock3, Inbox } from '@wso2/oxygen-ui-icons-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import { COMPLAINT_QUEUE_ROWS_PER_PAGE_OPTIONS } from '../complaints/constants'
import {
  useManagedComplaintListQuery,
  useManagedComplaintQueueStatsQuery,
} from '../complaints/hooks/useComplaintQueries'
import ComplaintQueueFilters from './components/ComplaintQueueFilters'
import ComplaintQueueTable from './components/ComplaintQueueTable'
import type { ComplaintQueueFiltersState } from './types'

const DEFAULT_FILTERS: ComplaintQueueFiltersState = {
  status: 'All',
  priority: 'All',
  search: '',
}

const DEFAULT_PAGE = 0
const DEFAULT_ROWS_PER_PAGE = 10

function ComplaintQueuePage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const [filters, setFilters] = useState<ComplaintQueueFiltersState>(DEFAULT_FILTERS)
  const [page, setPage] = useState(DEFAULT_PAGE)
  const [rowsPerPage, setRowsPerPage] = useState(DEFAULT_ROWS_PER_PAGE)

  // Server-computed and always unfiltered - the tiles summarize the whole queue regardless of
  // whatever filter is currently applied to the paginated table beside them.
  const statsQuery = useManagedComplaintQueueStatsQuery()
  const stats = statsQuery.data ?? {
    openCount: 0,
    awaitingInternalReviewCount: 0,
    resolvedCount: 0,
    slaBreachedCount: 0,
  }

  const listQuery = useManagedComplaintListQuery({
    status: filters.status === 'All' ? undefined : filters.status,
    priority: filters.priority === 'All' ? undefined : filters.priority,
    limit: rowsPerPage,
    offset: page * rowsPerPage,
  })
  const pageComplaints = useMemo(() => listQuery.data?.rows ?? [], [listQuery.data])
  const total = listQuery.data?.total ?? 0

  const rows = useMemo(() => {
    const search = filters.search.trim().toLowerCase()

    return pageComplaints.filter(
      (complaint) =>
        !(
          search &&
          !complaint.referenceId.toLowerCase().includes(search) &&
          !complaint.dataPrincipalName.toLowerCase().includes(search)
        ),
    )
  }, [pageComplaints, filters])

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack spacing={1}>
          <HeaderBreadcrumbs currentLabel="" />
          <Typography variant="h4" fontWeight={700}>
            {t('complaints.management.queue.title')}
          </Typography>
        </Stack>

        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(4, 1fr)' },
            gap: 2,
          }}
        >
          <StatCard
            value={stats.openCount}
            label={t('complaints.management.queue.stats.open')}
            icon={<Inbox size={22} />}
            iconColor="info"
          />
          <StatCard
            value={stats.awaitingInternalReviewCount}
            label={t('complaints.status.waitingOnDpo')}
            icon={<Clock3 size={22} />}
            iconColor="warning"
          />
          <StatCard
            value={stats.resolvedCount}
            label={t('complaints.management.queue.stats.resolved')}
            icon={<CheckCircle2 size={22} />}
            iconColor="success"
          />
          <StatCard
            value={stats.slaBreachedCount}
            label={t('complaints.management.queue.stats.slaBreached')}
            icon={<AlertTriangle size={22} />}
            iconColor="error"
          />
        </Box>

        <ComplaintQueueFilters
          filters={filters}
          onFilterChange={(nextFilters) => {
            setFilters(nextFilters)
            setPage(DEFAULT_PAGE)
          }}
          onClear={() => {
            setFilters(DEFAULT_FILTERS)
            setPage(DEFAULT_PAGE)
          }}
        />

        <ComplaintQueueTable
          rows={rows}
          onViewCase={(id) => navigate(`/complaint-management/${encodeURIComponent(id)}`)}
          isLoading={listQuery.isPending || listQuery.isPlaceholderData}
          isError={listQuery.isError}
          onRetry={() => listQuery.refetch()}
          rowsPerPage={rowsPerPage}
          rowsPerPageOptions={COMPLAINT_QUEUE_ROWS_PER_PAGE_OPTIONS}
          hasPreviousPage={page > DEFAULT_PAGE}
          hasNextPage={(page + 1) * rowsPerPage < total}
          disabled={listQuery.isPlaceholderData}
          onRowsPerPageChange={(nextRowsPerPage) => {
            setRowsPerPage(nextRowsPerPage)
            setPage(DEFAULT_PAGE)
          }}
          onPreviousPage={() => setPage((previousPage) => previousPage - 1)}
          onNextPage={() => setPage((previousPage) => previousPage + 1)}
        />
      </Stack>
    </Box>
  )
}

export default ComplaintQueuePage
