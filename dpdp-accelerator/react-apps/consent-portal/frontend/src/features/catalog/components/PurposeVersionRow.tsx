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
  Chip,
  Collapse,
  IconButton,
  Link,
  Paper,
  Skeleton,
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
import { ChevronDown, ChevronRight, Star, Trash2 } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router-dom'
import { useCatalogText } from '../../../i18n/catalogText'
import type { PurposeVersionSummary } from '../../../types/catalog'
import { usePurposeVersionQuery } from '../hooks/useCatalogQueries'

interface PurposeVersionRowProps {
  purposeId: string
  purposeName: string
  version: PurposeVersionSummary
  isLatest: boolean
  canWrite: boolean
  isSettingLatest: boolean
  onSetLatest: (versionId: string) => void
  onDelete: (version: { id: string; version: string }) => void
}

export default function PurposeVersionRow({
  purposeId,
  purposeName,
  version,
  isLatest,
  canWrite,
  isSettingLatest,
  onSetLatest,
  onDelete,
}: PurposeVersionRowProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const catalogText = useCatalogText()
  const [expanded, setExpanded] = useState(false)

  const versionQuery = usePurposeVersionQuery(purposeId, version.id, expanded)
  const versionDetail = versionQuery.data
  const elements = versionDetail?.elements ?? []

  const { description: versionDescription } = catalogText('purposes', {
    name: purposeName,
    version: version.version,
    description: version.description,
  })

  const colSpan = canWrite ? 4 : 3

  return (
    <>
      <TableRow
        hover
        sx={{
          '& > *': {
            borderBottom: expanded ? 'unset' : undefined,
          },
        }}
      >
        <TableCell sx={{ width: 44, px: 1 }}>
          <IconButton
            size="small"
            aria-expanded={expanded}
            aria-label={
              expanded
                ? t('catalog.actions.collapseVersion', { version: version.version })
                : t('catalog.actions.expandVersion', { version: version.version })
            }
            onClick={() => setExpanded((prev) => !prev)}
          >
            {expanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
          </IconButton>
        </TableCell>
        <TableCell>
          <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap">
            <Chip size="small" color="primary" label={version.version} />
            {isLatest ? <Chip size="small" label={t('catalog.values.latest')} /> : null}
          </Stack>
        </TableCell>
        <TableCell>{versionDescription ?? '-'}</TableCell>
        {canWrite ? (
          <TableCell align="right">
            <Stack direction="row" spacing={0.5} justifyContent="flex-end">
              {!isLatest ? (
                <Tooltip title={t('catalog.actions.setLatest')}>
                  <IconButton
                    size="small"
                    disabled={isSettingLatest}
                    aria-label={t('catalog.actions.setLatest')}
                    onClick={() => onSetLatest(version.id)}
                  >
                    <Star size={16} />
                  </IconButton>
                </Tooltip>
              ) : null}
              <Tooltip
                title={
                  isLatest
                    ? t('catalog.purposes.versionDelete.latestBlocked')
                    : t('catalog.actions.delete')
                }
              >
                <span>
                  <IconButton
                    size="small"
                    disabled={isLatest}
                    aria-label={t('catalog.actions.delete')}
                    onClick={() => onDelete({ id: version.id, version: version.version })}
                  >
                    <Trash2 size={16} />
                  </IconButton>
                </span>
              </Tooltip>
            </Stack>
          </TableCell>
        ) : null}
      </TableRow>
      {expanded ? (
        <TableRow>
          <TableCell colSpan={colSpan} sx={{ py: 0, px: 2, bgcolor: 'background.default' }}>
            <Collapse in={expanded} timeout="auto" unmountOnExit>
              <Box sx={{ py: 2, pl: 5, pr: 2 }}>
                {versionQuery.isLoading ? (
                  <Stack spacing={1}>
                    <Skeleton height={32} />
                    <Skeleton height={32} />
                  </Stack>
                ) : null}
                {versionQuery.isError ? (
                  <Alert severity="error">{t('catalog.messages.versionElementsLoadFailed')}</Alert>
                ) : null}
                {!versionQuery.isLoading && !versionQuery.isError ? (
                  elements.length === 0 ? (
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      sx={{ py: 1.5, fontStyle: 'italic' }}
                    >
                      {t('catalog.messages.noElements')}
                    </Typography>
                  ) : (
                    <TableContainer component={Paper} variant="outlined" sx={{ my: 1 }}>
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell sx={{ fontWeight: 700 }}>
                              {t('catalog.fields.element')}
                            </TableCell>
                            <TableCell sx={{ fontWeight: 700 }}>
                              {t('catalog.fields.description')}
                            </TableCell>
                            <TableCell sx={{ fontWeight: 700 }}>
                              {t('catalog.fields.requirement')}
                            </TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {elements.map((element) => {
                            const elementPath = `/elements/${encodeURIComponent(element.id)}`
                            const {
                              displayName: elementDisplayName,
                              description: elementDescription,
                            } = catalogText('elements', element)

                            return (
                              <TableRow key={element.id} hover>
                                <TableCell>
                                  <Stack spacing={0.25}>
                                    <Link
                                      component={RouterLink}
                                      to={elementPath}
                                      fontWeight={600}
                                      underline="none"
                                    >
                                      {elementDisplayName}
                                    </Link>
                                    <Typography variant="caption" color="text.secondary">
                                      <Box component="code">{element.name}</Box>
                                    </Typography>
                                  </Stack>
                                </TableCell>
                                <TableCell>{elementDescription ?? '-'}</TableCell>
                                <TableCell>
                                  <Chip
                                    size="small"
                                    color={element.mandatory ? 'error' : 'default'}
                                    variant="outlined"
                                    label={
                                      element.mandatory
                                        ? t('catalog.values.mandatory')
                                        : t('catalog.values.optional')
                                    }
                                  />
                                </TableCell>
                              </TableRow>
                            )
                          })}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  )
                ) : null}
              </Box>
            </Collapse>
          </TableCell>
        </TableRow>
      ) : null}
    </>
  )
}
