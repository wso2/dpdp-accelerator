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

import { Alert, Box, CircularProgress, Stack, StatCard, Typography } from '@wso2/oxygen-ui'
import { AlertTriangle, CheckCircle2, Clock3, Inbox } from '@wso2/oxygen-ui-icons-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import type { ComplaintStatus } from '../../types/complaint'
import { COMPLAINT_QUEUE_ROWS_PER_PAGE_OPTIONS } from '../complaints/constants'
import { useManagedComplaintListQuery } from '../complaints/hooks/useComplaintQueries'
import { getComplaintSlaState } from '../complaints/utils/complaintDisplay'
import ComplaintQueueFilters from './components/ComplaintQueueFilters'
import ComplaintQueueTable from './components/ComplaintQueueTable'
import type { ComplaintQueueFiltersState } from './types'

const OPEN_STATUSES: ComplaintStatus[] = ['OPEN', 'IN_PROGRESS', 'AWAITING_INTERNAL_REVIEW']

const DEFAULT_FILTERS: ComplaintQueueFiltersState = {
  status: 'All',
  priority: 'All',
  search: '',
}

const CLOSED_OUT_STATUSES: ComplaintStatus[] = ['RESOLVED']

const DEFAULT_PAGE = 0
const DEFAULT_ROWS_PER_PAGE = 10

/**
 * The stat tiles summarize the whole queue, not just the current page, so they're
 * computed from their own unfiltered scan capped at this many complaints - accurate
 * for any org below the cap, an undercount above it. There is no dedicated
 * aggregate/count endpoint to ask the server for exact totals per bucket instead.
 */
const STATS_SCAN_LIMIT = 500

function ComplaintQueuePage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const [filters, setFilters] = useState<ComplaintQueueFiltersState>(DEFAULT_FILTERS)
  const [page, setPage] = useState(DEFAULT_PAGE)
  const [rowsPerPage, setRowsPerPage] = useState(DEFAULT_ROWS_PER_PAGE)

  const statsQuery = useManagedComplaintListQuery({ limit: STATS_SCAN_LIMIT })
  const statsComplaints = useMemo(() => statsQuery.data?.rows ?? [], [statsQuery.data])

  const stats = useMemo(() => {
    const openCount = statsComplaints.filter((complaint) =>
      OPEN_STATUSES.includes(complaint.status),
    ).length
    const awaitingInfoCount = statsComplaints.filter(
      (complaint) => complaint.status === 'WAITING_ON_CLIENT',
    ).length
    const resolvedCount = statsComplaints.filter(
      (complaint) => complaint.status === 'RESOLVED',
    ).length
    const slaBreachedCount = statsComplaints.filter(
      (complaint) =>
        getComplaintSlaState(complaint.statutoryDueDate, complaint.status) === 'breached',
    ).length

    return { openCount, awaitingInfoCount, resolvedCount, slaBreachedCount }
  }, [statsComplaints])

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

    // Both narrowings below apply only within the current, already server-paginated
    // page - hiding resolved complaints by default and the reference/name search can
    // each make a page render fewer than rowsPerPage rows. Previous/Next stay correct
    // regardless, since they're driven by the server's offset and total, not by what's
    // left standing here.
    return pageComplaints.filter((complaint) => {
      if (filters.status === 'All' && CLOSED_OUT_STATUSES.includes(complaint.status)) {
        return false
      }

      return !(
        search &&
        !complaint.referenceId.toLowerCase().includes(search) &&
        !complaint.dataPrincipalName.toLowerCase().includes(search)
      )
    })
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
            value={stats.awaitingInfoCount}
            label={t('complaints.management.queue.stats.awaitingInfo')}
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

        {listQuery.isPending ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress size={28} />
          </Box>
        ) : null}

        {listQuery.isError ? (
          <Alert severity="error">{t('complaints.management.queue.loadFailed')}</Alert>
        ) : null}

        {!listQuery.isPending && !listQuery.isError && rows.length === 0 ? (
          <Typography>{t('complaints.management.queue.empty')}</Typography>
        ) : null}

        {!listQuery.isPending && !listQuery.isError && rows.length > 0 ? (
          <ComplaintQueueTable
            rows={rows}
            onViewCase={(id) => navigate(`/complaint-management/${encodeURIComponent(id)}`)}
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
        ) : null}
      </Stack>
    </Box>
  )
}

export default ComplaintQueuePage
