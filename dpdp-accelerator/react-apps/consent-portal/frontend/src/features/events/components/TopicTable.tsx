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
  Button,
  Chip,
  IconButton,
  LinearProgress,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@wso2/oxygen-ui'
import { Trash2 } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import CopyableText from '../../../components/CopyableText'
import CursorPaginationFooter from '../../../components/CursorPaginationFooter'
import type { TopicRecord } from '../../../types/topic'

const ROWS_PER_PAGE_OPTIONS = [10, 20, 50] as const

interface TopicTableProps {
  rows: TopicRecord[]
  isLoading: boolean
  isError: boolean
  rowsPerPage: number
  hasPreviousPage: boolean
  hasNextPage: boolean
  canWrite: boolean
  isMutating: boolean
  onPreviousPage: () => void
  onNextPage: () => void
  onRowsPerPageChange: (rowsPerPage: number) => void
  onRetry: () => void
  onDelete: (topic: TopicRecord) => void
}

function getStatusChipColor(status: string): 'success' | 'default' {
  return status.toUpperCase() === 'ACTIVE' ? 'success' : 'default'
}

export default function TopicTable({
  rows,
  isLoading,
  isError,
  rowsPerPage,
  hasPreviousPage,
  hasNextPage,
  canWrite,
  isMutating,
  onPreviousPage,
  onNextPage,
  onRowsPerPageChange,
  onRetry,
  onDelete,
}: TopicTableProps): React.JSX.Element {
  const { t } = useTranslation('common')

  if (isError) {
    return (
      <Paper sx={{ p: 3, textAlign: 'center' }}>
        <Alert severity="error" sx={{ mb: 2 }}>
          {t('topics.loadFailed')}
        </Alert>
        <Button variant="outlined" onClick={onRetry}>
          {t('authorization.tryAgain')}
        </Button>
      </Paper>
    )
  }

  return (
    <Paper sx={{ width: '100%', overflow: 'hidden', boxShadow: 1 }}>
      {isLoading ? <LinearProgress /> : null}
      <TableContainer>
        <Table aria-label={t('topics.table.ariaLabel')}>
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
              <TableCell>{t('topics.table.name')}</TableCell>
              <TableCell>{t('topics.table.topicId')}</TableCell>
              <TableCell>{t('topics.table.description')}</TableCell>
              <TableCell>{t('topics.table.status')}</TableCell>
              {canWrite ? <TableCell align="right">{t('topics.table.actions')}</TableCell> : null}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 && !isLoading ? (
              <TableRow>
                <TableCell colSpan={canWrite ? 5 : 4} align="center" sx={{ py: 4 }}>
                  <Typography color="text.secondary">{t('topics.table.empty')}</Typography>
                </TableCell>
              </TableRow>
            ) : null}
            {rows.map((topic) => {
              const isActive = topic.status.toUpperCase() === 'ACTIVE'
              const isDeleted =
                topic.status.toUpperCase() === 'DELETED' ||
                topic.status.toUpperCase() === 'DEREGISTERED'
              const isSystemTopic = topic.initiatedBy?.toLowerCase() === 'system'
              const initiator = topic.initiatedBy?.toUpperCase() || 'USER'
              let deleteActionTitle = t('topics.actions.delete')
              if (isSystemTopic) {
                deleteActionTitle = initiator
              } else if (isDeleted) {
                deleteActionTitle = t('topics.actions.alreadyDeleted')
              }

              return (
                <TableRow key={topic.topicId} hover>
                  <TableCell>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Typography variant="body2" fontWeight={600}>
                        {topic.name}
                      </Typography>
                      <Chip size="small" variant="outlined" label={initiator} />
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <CopyableText value={topic.topicId} truncateAt={14} monospace />
                  </TableCell>
                  <TableCell>
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      noWrap
                      sx={{ maxWidth: 300 }}
                    >
                      {topic.description || '-'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      color={getStatusChipColor(topic.status)}
                      label={t(`topics.status.${topic.status.toLowerCase()}`, topic.status)}
                    />
                  </TableCell>
                  {canWrite ? (
                    <TableCell align="right">
                      <Tooltip title={deleteActionTitle}>
                        <span>
                          <IconButton
                            size="small"
                            color="error"
                            disabled={!isActive || isSystemTopic || isMutating}
                            onClick={() => onDelete(topic)}
                            aria-label={t('topics.actions.delete')}
                          >
                            <Trash2 size={16} />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </TableCell>
                  ) : null}
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </TableContainer>
      <CursorPaginationFooter
        rowsPerPage={rowsPerPage}
        rowsPerPageOptions={ROWS_PER_PAGE_OPTIONS}
        hasPreviousPage={hasPreviousPage}
        hasNextPage={hasNextPage}
        disabled={isLoading || isMutating}
        onRowsPerPageChange={onRowsPerPageChange}
        onPreviousPage={onPreviousPage}
        onNextPage={onNextPage}
      />
    </Paper>
  )
}
