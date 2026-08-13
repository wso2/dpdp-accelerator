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
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
} from '@wso2/oxygen-ui'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { COMPLAINT_PRIORITIES, type ComplaintRecord } from '../../../types/complaint'
import { formatIsoDateTime } from '../../../utils/dateTime'
import CursorPaginationFooter from '../../../components/CursorPaginationFooter'
import ComplaintPriorityChip from '../../complaints/components/ComplaintPriorityChip'
import ComplaintSlaIndicator from '../../complaints/components/ComplaintSlaIndicator'
import ComplaintStatusChip from '../../complaints/components/ComplaintStatusChip'

interface ComplaintQueueTableProps {
  rows: ComplaintRecord[]
  onViewCase: (id: string) => void
  rowsPerPage: number
  rowsPerPageOptions: readonly number[]
  hasPreviousPage: boolean
  hasNextPage: boolean
  disabled?: boolean
  onRowsPerPageChange: (rowsPerPage: number) => void
  onPreviousPage: () => void
  onNextPage: () => void
}

type SortableField = 'priority' | 'sla'
type SortDirection = 'asc' | 'desc'

const DATE_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
}

function sortRows(
  rows: ComplaintRecord[],
  sortField: SortableField | null,
  sortDirection: SortDirection,
): ComplaintRecord[] {
  if (!sortField) {
    return rows
  }

  const sortedRows = [...rows].sort((left, right) => {
    if (sortField === 'priority') {
      return (
        COMPLAINT_PRIORITIES.indexOf(left.priority) - COMPLAINT_PRIORITIES.indexOf(right.priority)
      )
    }

    return new Date(left.statutoryDueDate).getTime() - new Date(right.statutoryDueDate).getTime()
  })

  return sortDirection === 'asc' ? sortedRows : sortedRows.reverse()
}

function ComplaintQueueTable({
  rows,
  onViewCase,
  rowsPerPage,
  rowsPerPageOptions,
  hasPreviousPage,
  hasNextPage,
  disabled = false,
  onRowsPerPageChange,
  onPreviousPage,
  onNextPage,
}: ComplaintQueueTableProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [sortField, setSortField] = useState<SortableField | null>(null)
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc')

  const sortedRows = useMemo(
    () => sortRows(rows, sortField, sortDirection),
    [rows, sortField, sortDirection],
  )

  const handleSortClick = (field: SortableField): void => {
    if (sortField !== field) {
      setSortField(field)
      setSortDirection('asc')
      return
    }

    setSortDirection((previousDirection) => (previousDirection === 'asc' ? 'desc' : 'asc'))
  }

  return (
    <TableContainer component={Paper} elevation={1}>
      <Table aria-label={t('complaints.management.queue.table.tableAriaLabel')}>
        <TableHead>
          <TableRow>
            <TableCell>{t('complaints.management.queue.table.headers.referenceId')}</TableCell>
            <TableCell>{t('complaints.management.queue.table.headers.dataPrincipal')}</TableCell>
            <TableCell>{t('complaints.management.queue.table.headers.category')}</TableCell>
            <TableCell sortDirection={sortField === 'priority' ? sortDirection : false}>
              <TableSortLabel
                active={sortField === 'priority'}
                direction={sortField === 'priority' ? sortDirection : 'asc'}
                onClick={() => handleSortClick('priority')}
                sx={{ '& .MuiTableSortLabel-icon': { opacity: 1 } }}
              >
                {t('complaints.management.queue.table.headers.priority')}
              </TableSortLabel>
            </TableCell>
            <TableCell>{t('complaints.management.queue.table.headers.status')}</TableCell>
            <TableCell sortDirection={sortField === 'sla' ? sortDirection : false}>
              <TableSortLabel
                active={sortField === 'sla'}
                direction={sortField === 'sla' ? sortDirection : 'asc'}
                onClick={() => handleSortClick('sla')}
                sx={{ '& .MuiTableSortLabel-icon': { opacity: 1 } }}
              >
                {t('complaints.management.queue.table.headers.sla')}
              </TableSortLabel>
            </TableCell>
            <TableCell>{t('complaints.management.queue.table.headers.updated')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {sortedRows.map((row) => (
            <TableRow
              key={row.id}
              hover
              onClick={() => onViewCase(row.id)}
              sx={{ cursor: 'pointer' }}
            >
              <TableCell sx={{ fontWeight: 600 }}>{row.referenceId}</TableCell>
              <TableCell>{row.dataPrincipalName}</TableCell>
              <TableCell>{t(`complaints.categories.${row.category}`)}</TableCell>
              <TableCell>
                <ComplaintPriorityChip priority={row.priority} />
              </TableCell>
              <TableCell>
                <ComplaintStatusChip status={row.status} viewerRole="ComplaintOfficer" />
              </TableCell>
              <TableCell>
                <ComplaintSlaIndicator
                  statutoryDueDate={row.statutoryDueDate}
                  status={row.status}
                />
              </TableCell>
              <TableCell>{formatIsoDateTime(row.updatedAt, DATE_FORMAT_OPTIONS)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <CursorPaginationFooter
        rowsPerPage={rowsPerPage}
        rowsPerPageOptions={rowsPerPageOptions}
        hasPreviousPage={hasPreviousPage}
        hasNextPage={hasNextPage}
        disabled={disabled}
        onRowsPerPageChange={onRowsPerPageChange}
        onPreviousPage={onPreviousPage}
        onNextPage={onNextPage}
      />
    </TableContainer>
  )
}

ComplaintQueueTable.defaultProps = {
  disabled: false,
}

export default ComplaintQueueTable
