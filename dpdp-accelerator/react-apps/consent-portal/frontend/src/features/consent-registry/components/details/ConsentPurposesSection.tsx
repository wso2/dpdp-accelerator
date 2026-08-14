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
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Card,
  CardContent,
  CardHeader,
  Chip,
  Divider,
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
import { CheckCircle, ChevronRight, XCircle } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import { useCatalogText } from '../../../../i18n/catalogText'
import type { ConsentPurposeItem } from '../../../../types/consent'

interface ConsentPurposesSectionProps {
  purposes: ConsentPurposeItem[]
}

const PURPOSE_ELEMENTS_COLUMN_WIDTHS = {
  element: '28%',
  approved: '14%',
  required: '18%',
  description: '40%',
} as const

const ELEMENT_NAME_MAX_DISPLAY_LENGTH = 28

function truncateElementLabel(elementLabel: string, maxLength: number): string {
  if (elementLabel.length <= maxLength) {
    return elementLabel
  }

  return `${elementLabel.slice(0, Math.max(maxLength - 3, 1))}...`
}

function ConsentPurposesSection({ purposes }: ConsentPurposesSectionProps): React.JSX.Element {
  const { t } = useTranslation('common')
  // Purpose and element wording is authored by administrators, so it comes from
  // resources/<lang>/catalog.ts rather than common.ts, falling back to the
  // English the server returns.
  const catalogText = useCatalogText()

  return (
    <Card sx={{ boxShadow: 1 }}>
      <CardHeader
        title={
          <Typography variant="h5" fontWeight={600}>
            {t('consentRegistry.details.section.purposes', 'Consent Purposes')}
          </Typography>
        }
        sx={{ pb: 1 }}
      />
      <Divider />
      <CardContent sx={{ p: 2 }}>
        {purposes.map((purpose) => {
          const approved = purpose.elements.filter((element) => element.approved).length
          const total = purpose.elements.length

          return (
            <Accordion
              key={`${purpose.purposeId}::${purpose.version}`}
              disableGutters
              elevation={0}
              sx={{
                mb: 1,
                border: 1,
                borderColor: 'divider',
                borderRadius: 1,
                overflow: 'hidden',
                '&:before': { display: 'none' },
                '&.Mui-expanded': { mt: 0, mb: 1 },
                '&:last-of-type': { mb: 0 },
                '&.Mui-expanded:last-of-type': { mb: 0 },
              }}
            >
              <AccordionSummary
                expandIcon={<ChevronRight />}
                sx={{ '&:hover': { bgcolor: 'action.hover' } }}
              >
                <Stack direction="row" spacing={1.5} alignItems="center">
                  <Chip
                    label={t('consentRegistry.details.approvedCount', { approved, total })}
                    color="primary"
                    size="small"
                    sx={{
                      height: 20,
                      '& .MuiChip-label': { px: 0.75, fontSize: '0.6875rem', fontWeight: 500 },
                    }}
                  />
                  <Typography variant="body2" fontWeight={600}>
                    {catalogText('purposes', purpose).displayName}
                  </Typography>
                </Stack>
              </AccordionSummary>
              <AccordionDetails sx={{ p: 0 }}>
                <TableContainer>
                  <Table
                    size="small"
                    sx={{
                      tableLayout: 'fixed',
                      '& tbody tr:hover': { bgcolor: 'action.hover' },
                    }}
                  >
                    <TableHead>
                      <TableRow>
                        <TableCell
                          sx={{ fontWeight: 700, width: PURPOSE_ELEMENTS_COLUMN_WIDTHS.element }}
                        >
                          {t('consentRegistry.details.table.element', 'Element')}
                        </TableCell>
                        <TableCell
                          sx={{ fontWeight: 700, width: PURPOSE_ELEMENTS_COLUMN_WIDTHS.approved }}
                        >
                          {t('consentRegistry.details.table.approved', 'Approved')}
                        </TableCell>
                        <TableCell
                          sx={{ fontWeight: 700, width: PURPOSE_ELEMENTS_COLUMN_WIDTHS.required }}
                        >
                          {t('consentRegistry.details.table.required', 'Required')}
                        </TableCell>
                        <TableCell
                          sx={{
                            fontWeight: 700,
                            width: PURPOSE_ELEMENTS_COLUMN_WIDTHS.description,
                          }}
                        >
                          {t('consentRegistry.details.table.description', 'Description')}
                        </TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {purpose.elements.map((element) => {
                        const elementText = catalogText('elements', element)

                        return (
                          <TableRow key={`${element.elementId}::${element.version}`}>
                            <TableCell sx={{ width: PURPOSE_ELEMENTS_COLUMN_WIDTHS.element }}>
                              <Tooltip
                                title={elementText.displayName}
                                disableHoverListener={
                                  elementText.displayName.length <= ELEMENT_NAME_MAX_DISPLAY_LENGTH
                                }
                              >
                                <Box
                                  component="code"
                                  sx={{
                                    display: 'inline-block',
                                    maxWidth: '100%',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                    verticalAlign: 'bottom',
                                  }}
                                >
                                  {truncateElementLabel(
                                    elementText.displayName,
                                    ELEMENT_NAME_MAX_DISPLAY_LENGTH,
                                  )}
                                </Box>
                              </Tooltip>
                            </TableCell>
                            <TableCell>
                              {element.approved ? (
                                <Box
                                  role="img"
                                  aria-label={t('consentRegistry.details.approved', 'Approved')}
                                  sx={{ color: 'success.main', display: 'inline-flex' }}
                                >
                                  <CheckCircle size={16} />
                                </Box>
                              ) : (
                                <Box
                                  role="img"
                                  aria-label={t(
                                    'consentRegistry.details.notApproved',
                                    'Not approved',
                                  )}
                                  sx={{ color: 'error.main', display: 'inline-flex' }}
                                >
                                  <XCircle size={16} />
                                </Box>
                              )}
                            </TableCell>
                            <TableCell>
                              <Chip
                                label={
                                  element.mandatory
                                    ? t('consentRegistry.details.values.required', 'Required')
                                    : t('consentRegistry.details.values.optional', 'Optional')
                                }
                                size="small"
                                color={element.mandatory ? 'error' : 'default'}
                                variant="outlined"
                              />
                            </TableCell>
                            <TableCell>{elementText.description ?? '-'}</TableCell>
                          </TableRow>
                        )
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              </AccordionDetails>
            </Accordion>
          )
        })}
      </CardContent>
    </Card>
  )
}

export default ConsentPurposesSection
