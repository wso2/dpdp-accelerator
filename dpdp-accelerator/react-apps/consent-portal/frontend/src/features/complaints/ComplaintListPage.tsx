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
  Alert,
  Box,
  Button,
  CircularProgress,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@wso2/oxygen-ui'
import { Plus } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import CursorPaginationFooter from '../../components/CursorPaginationFooter'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import { COMPLAINT_STATUSES, type ComplaintStatus } from '../../types/complaint'
import { formatIsoDateTime } from '../../utils/dateTime'
import ComplaintStatusChip from './components/ComplaintStatusChip'
import ComplaintSubmitDialog from './components/ComplaintSubmitDialog'
import { COMPLAINT_LIST_ROWS_PER_PAGE_OPTIONS } from './constants'
import { useMyComplaintListQuery } from './hooks/useComplaintQueries'
import { getComplaintStatusLabelKey } from './utils/complaintDisplay'

type StatusFilter = ComplaintStatus | 'All'

const DEFAULT_PAGE = 0
const DEFAULT_ROWS_PER_PAGE = 10

const DATE_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
}

function ComplaintListPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const [isSubmitDialogOpen, setIsSubmitDialogOpen] = useState<boolean>(false)
  const [submittedComplaint, setSubmittedComplaint] = useState<{
    referenceId: string
    attachmentUploadFailed: boolean
  } | null>(null)
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('All')
  const [page, setPage] = useState(DEFAULT_PAGE)
  const [rowsPerPage, setRowsPerPage] = useState(DEFAULT_ROWS_PER_PAGE)

  const listQuery = useMyComplaintListQuery({
    status: statusFilter === 'All' ? undefined : statusFilter,
    limit: rowsPerPage,
    offset: page * rowsPerPage,
  })
  const rows = listQuery.data?.rows ?? []
  const total = listQuery.data?.total ?? 0

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          alignItems={{ sm: 'center' }}
          justifyContent="space-between"
        >
          <Stack spacing={1}>
            <HeaderBreadcrumbs currentLabel="" />
            <Typography variant="h4" fontWeight={700}>
              {t('complaints.list.title')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t('complaints.list.subtitle')}
            </Typography>
          </Stack>
          <Button
            variant="contained"
            startIcon={<Plus size={16} />}
            onClick={() => {
              setIsSubmitDialogOpen(true)
            }}
          >
            {t('complaints.list.submitNew')}
          </Button>
        </Stack>

        {submittedComplaint ? (
          <Alert
            severity={submittedComplaint.attachmentUploadFailed ? 'warning' : 'success'}
            onClose={() => setSubmittedComplaint(null)}
          >
            {submittedComplaint.attachmentUploadFailed
              ? t('complaints.submit.success.attachmentFailedMessage', {
                  referenceId: submittedComplaint.referenceId,
                })
              : t('complaints.submit.success.message', {
                  referenceId: submittedComplaint.referenceId,
                })}
          </Alert>
        ) : null}

        <FormControl size="small" sx={{ width: { xs: '100%', sm: 'auto' }, minWidth: { sm: 200 } }}>
          <InputLabel id="complaint-list-status-label">
            {t('complaints.list.filters.status')}
          </InputLabel>
          <Select
            labelId="complaint-list-status-label"
            id="complaint-list-status"
            value={statusFilter}
            label={t('complaints.list.filters.status')}
            onChange={(event) => {
              setStatusFilter(event.target.value as StatusFilter)
              setPage(DEFAULT_PAGE)
            }}
          >
            <MenuItem value="All">{t('complaints.list.filters.all')}</MenuItem>
            {COMPLAINT_STATUSES.map((status) => (
              <MenuItem key={status} value={status}>
                {t(`complaints.status.${getComplaintStatusLabelKey(status)}`)}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        {listQuery.isPending ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress size={28} />
          </Box>
        ) : null}

        {listQuery.isError ? (
          <Alert severity="error">{t('complaints.list.loadFailed')}</Alert>
        ) : null}

        {!listQuery.isPending && !listQuery.isError && rows.length === 0 ? (
          <Typography>
            {statusFilter === 'All'
              ? t('complaints.list.empty')
              : t('complaints.list.emptyFiltered')}
          </Typography>
        ) : null}

        {!listQuery.isPending && !listQuery.isError && rows.length > 0 ? (
          <TableContainer component={Paper} elevation={1}>
            <Table aria-label={t('complaints.list.table.tableAriaLabel')}>
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
                  <TableCell>{t('complaints.list.table.headers.referenceId')}</TableCell>
                  <TableCell>{t('complaints.list.table.headers.category')}</TableCell>
                  <TableCell>{t('complaints.list.table.headers.status')}</TableCell>
                  <TableCell>{t('complaints.list.table.headers.submitted')}</TableCell>
                  <TableCell>{t('complaints.list.table.headers.updated')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => (
                  <TableRow
                    key={row.id}
                    hover
                    onClick={() => {
                      navigate(`/complaints/${encodeURIComponent(row.id)}`)
                    }}
                    sx={{ cursor: 'pointer' }}
                  >
                    <TableCell sx={{ fontWeight: 600 }}>{row.referenceId}</TableCell>
                    <TableCell>{t(`complaints.categories.${row.category}`)}</TableCell>
                    <TableCell>
                      <ComplaintStatusChip status={row.status} viewerRole="DataPrincipal" />
                    </TableCell>
                    <TableCell>{formatIsoDateTime(row.submittedAt, DATE_FORMAT_OPTIONS)}</TableCell>
                    <TableCell>{formatIsoDateTime(row.updatedAt, DATE_FORMAT_OPTIONS)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            <CursorPaginationFooter
              rowsPerPage={rowsPerPage}
              rowsPerPageOptions={COMPLAINT_LIST_ROWS_PER_PAGE_OPTIONS}
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
          </TableContainer>
        ) : null}
      </Stack>

      <ComplaintSubmitDialog
        open={isSubmitDialogOpen}
        onClose={() => setIsSubmitDialogOpen(false)}
        onSubmitted={(referenceId, attachmentUploadFailed) => {
          setIsSubmitDialogOpen(false)
          setSubmittedComplaint({ referenceId, attachmentUploadFailed })
        }}
      />
    </Box>
  )
}

export default ComplaintListPage
