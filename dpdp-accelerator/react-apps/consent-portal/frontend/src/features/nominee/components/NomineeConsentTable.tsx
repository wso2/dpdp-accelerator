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
  Checkbox,
  Chip,
  IconButton,
  ListingTable,
  Skeleton,
  TablePagination,
  Tooltip,
} from '@wso2/oxygen-ui'
import { ShieldX } from '@wso2/oxygen-ui-icons-react'
import { Fragment, type MouseEvent, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import type { NomineeConsentRecord } from '../../../types/nominee'
import { formatIsoDateTime } from '../../../utils/dateTime'
import {
  getConsentStatusChipColor,
  getConsentStatusLabelKey,
  isConsentRevokableStatus,
} from '../../consent-registry/utils/statusChip'

interface NomineeConsentTableProps {
  rows: NomineeConsentRecord[]
  totalCount: number
  isLoading: boolean
  page: number
  rowsPerPage: number
  selectedIds: string[]
  isMutating: boolean
  onPageChange: (page: number) => void
  onRowsPerPageChange: (rowsPerPage: number) => void
  onSelectionChange: (ids: string[]) => void
  onRevoke: (consentID: string) => void
  onRowClick: (consentID: string) => void
}

const DATE_TIME_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
}

function NomineeConsentTable({
  rows,
  totalCount,
  isLoading,
  page,
  rowsPerPage,
  selectedIds,
  isMutating,
  onPageChange,
  onRowsPerPageChange,
  onSelectionChange,
  onRevoke,
  onRowClick,
}: NomineeConsentTableProps): React.JSX.Element {
  const { t } = useTranslation('common')

  const groupedRows = useMemo(() => {
    const groupedMap = new Map<string, NomineeConsentRecord[]>()

    rows.forEach((row) => {
      const existingRows = groupedMap.get(row.clientName)
      groupedMap.set(row.clientName, existingRows ? [...existingRows, row] : [row])
    })

    return Array.from(groupedMap.entries()).map(([clientName, clientRows]) => ({
      clientName,
      clientRows,
    }))
  }, [rows])

  // Only ACTIVE consents can be revoked; others are shown for visibility but not selectable.
  const revokableIds = useMemo(
    () => rows.filter((row) => isConsentRevokableStatus(row.status)).map((row) => row.id),
    [rows],
  )
  const allSelected = revokableIds.length > 0 && selectedIds.length === revokableIds.length
  const someSelected = selectedIds.length > 0 && !allSelected

  const handleRevokeClick = (event: MouseEvent<HTMLElement>): void => {
    event.stopPropagation()
    const consentID = event.currentTarget.dataset.consentId

    if (consentID) {
      onRevoke(consentID)
    }
  }

  const stopPropagation = (event: MouseEvent<HTMLElement>): void => {
    event.stopPropagation()
  }

  return (
    <ListingTable.Container sx={{ minWidth: 960 }}>
      <ListingTable
        density="standard"
        variant="table"
        aria-label={t('nominee.manage.table.ariaLabel', 'Nominee-managed consent table')}
        sx={{ tableLayout: 'fixed' }}
      >
        <ListingTable.Head>
          <ListingTable.Row>
            <ListingTable.Cell sx={{ width: '5%' }}>
              <Checkbox
                indeterminate={someSelected}
                checked={allSelected}
                disabled={revokableIds.length === 0 || isMutating}
                onChange={(event) => {
                  onSelectionChange(event.target.checked ? revokableIds : [])
                }}
              />
            </ListingTable.Cell>
            <ListingTable.Cell sx={{ width: '34%' }}>
              {t('nominee.manage.table.purposes', 'Purposes')}
            </ListingTable.Cell>
            <ListingTable.Cell sx={{ width: '15%' }}>
              {t('nominee.manage.table.type', 'Type')}
            </ListingTable.Cell>
            <ListingTable.Cell sx={{ width: '15%' }}>
              {t('nominee.manage.table.status', 'Status')}
            </ListingTable.Cell>
            <ListingTable.Cell sx={{ width: '20%' }}>
              {t('nominee.manage.table.updated', 'Updated')}
            </ListingTable.Cell>
            <ListingTable.Cell align="center" sx={{ width: '11%' }}>
              {t('nominee.manage.table.actions', 'Actions')}
            </ListingTable.Cell>
          </ListingTable.Row>
        </ListingTable.Head>

        <ListingTable.Body>
          {isLoading
            ? Array.from({ length: rowsPerPage }, (_, rowIndex) => (
                <ListingTable.Row key={`skeleton-row-${rowIndex}`} variant="table">
                  <ListingTable.Cell sx={{ width: '5%' }}>
                    <Skeleton variant="rounded" width={20} height={20} />
                  </ListingTable.Cell>
                  <ListingTable.Cell sx={{ width: '34%' }}>
                    <Skeleton variant="rounded" width={140} height={24} />
                  </ListingTable.Cell>
                  <ListingTable.Cell sx={{ width: '15%' }}>
                    <Skeleton variant="text" width="70%" />
                  </ListingTable.Cell>
                  <ListingTable.Cell sx={{ width: '15%' }}>
                    <Skeleton variant="rounded" width={72} height={24} />
                  </ListingTable.Cell>
                  <ListingTable.Cell sx={{ width: '20%' }}>
                    <Skeleton variant="text" width="86%" />
                  </ListingTable.Cell>
                  <ListingTable.Cell align="center" sx={{ width: '11%' }}>
                    <Skeleton variant="circular" width={24} height={24} />
                  </ListingTable.Cell>
                </ListingTable.Row>
              ))
            : groupedRows.map((group) => (
                <Fragment key={group.clientName}>
                  <ListingTable.Row variant="table" sx={{ bgcolor: 'action.hover' }}>
                    <ListingTable.Cell colSpan={6} sx={{ fontWeight: 700 }}>
                      {t('nominee.manage.table.clientLabel', 'Organization: {{client}}', {
                        client: group.clientName,
                      })}
                    </ListingTable.Cell>
                  </ListingTable.Row>

                  {group.clientRows.map((row) => {
                    const canRevoke = isConsentRevokableStatus(row.status)
                    return (
                      <ListingTable.Row
                        key={row.id}
                        hover
                        variant="table"
                        sx={{ cursor: 'pointer' }}
                        onClick={() => {
                          onRowClick(row.id)
                        }}
                      >
                        <ListingTable.Cell sx={{ width: '5%' }} onClick={stopPropagation}>
                          <Checkbox
                            checked={selectedIds.includes(row.id)}
                            disabled={isMutating || !canRevoke}
                            onChange={(event) => {
                              onSelectionChange(
                                event.target.checked
                                  ? [...selectedIds, row.id]
                                  : selectedIds.filter((id) => id !== row.id),
                              )
                            }}
                          />
                        </ListingTable.Cell>
                        <ListingTable.Cell sx={{ width: '34%', fontWeight: 500 }}>
                          <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap' }}>
                            {row.purposes.map((purpose) => (
                              <Chip
                                key={`${row.id}-${purpose}`}
                                size="small"
                                label={purpose}
                                variant="outlined"
                              />
                            ))}
                          </Box>
                        </ListingTable.Cell>
                        <ListingTable.Cell sx={{ width: '15%' }}>{row.type}</ListingTable.Cell>
                        <ListingTable.Cell sx={{ width: '15%' }}>
                          <Chip
                            size="small"
                            color={getConsentStatusChipColor(row.status)}
                            label={t(
                              `consentRegistry.status.${getConsentStatusLabelKey(row.status)}`,
                            )}
                            variant="outlined"
                          />
                        </ListingTable.Cell>
                        <ListingTable.Cell sx={{ width: '20%' }}>
                          {formatIsoDateTime(row.updatedAt, DATE_TIME_FORMAT_OPTIONS)}
                        </ListingTable.Cell>
                        <ListingTable.Cell align="center" sx={{ width: '11%' }}>
                          <Tooltip
                            title={
                              canRevoke
                                ? t('nominee.manage.table.revoke', 'Revoke')
                                : t(
                                    'nominee.manage.table.notRevokable',
                                    'Only active consents can be revoked',
                                  )
                            }
                          >
                            <span>
                              <IconButton
                                size="small"
                                color="error"
                                disabled={isMutating || !canRevoke}
                                aria-label={t('nominee.manage.table.revoke', 'Revoke')}
                                data-consent-id={row.id}
                                onClick={handleRevokeClick}
                              >
                                <ShieldX size={16} />
                              </IconButton>
                            </span>
                          </Tooltip>
                        </ListingTable.Cell>
                      </ListingTable.Row>
                    )
                  })}
                </Fragment>
              ))}
        </ListingTable.Body>
      </ListingTable>

      <TablePagination
        component="div"
        count={totalCount}
        page={page}
        rowsPerPage={rowsPerPage}
        rowsPerPageOptions={[10, 25, 50]}
        onPageChange={(_, nextPage) => {
          onPageChange(nextPage)
        }}
        onRowsPerPageChange={(event) => {
          onRowsPerPageChange(Number(event.target.value))
        }}
      />
    </ListingTable.Container>
  )
}

export default NomineeConsentTable
