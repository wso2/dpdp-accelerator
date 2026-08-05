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
  Typography,
} from '@wso2/oxygen-ui'
import { ChevronRight } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import type { ConsentPurpose } from '../../../../types/consent'

interface ConsentPurposesSectionProps {
  purposes: ConsentPurpose[]
}

function ConsentPurposesSection({ purposes }: ConsentPurposesSectionProps): React.JSX.Element {
  const { t } = useTranslation('common')

  return (
    <Card sx={{ boxShadow: 1 }}>
      <CardHeader
        title={
          <Typography variant="h5" fontWeight={600}>
            {t('consentRegistry.details.section.purposes')}
          </Typography>
        }
        sx={{ pb: 1 }}
      />
      <Divider />
      <CardContent sx={{ p: 2 }}>
        {purposes.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {t('consentRegistry.details.noPurposes')}
          </Typography>
        ) : null}
        {purposes.map((purpose) => (
          <Accordion
            key={`${purpose.id}::${purpose.versionId}`}
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
              <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap">
                <Typography variant="body2" fontWeight={600}>
                  {purpose.name}
                </Typography>
                <Chip size="small" variant="outlined" label={purpose.type} />
                <Chip size="small" color="primary" label={purpose.version} />
                <Chip
                  size="small"
                  variant="outlined"
                  label={t('consentRegistry.details.elementCount', {
                    count: purpose.elements.length,
                  })}
                />
              </Stack>
            </AccordionSummary>
            <AccordionDetails sx={{ p: 0 }}>
              <TableContainer>
                <Table
                  size="small"
                  sx={{ tableLayout: 'fixed', '& tbody tr:hover': { bgcolor: 'action.hover' } }}
                >
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 700, width: '40%' }}>
                        {t('consentRegistry.details.table.element')}
                      </TableCell>
                      <TableCell sx={{ fontWeight: 700, width: '60%' }}>
                        {t('consentRegistry.details.table.displayName')}
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {purpose.elements.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={2}>
                          <Typography variant="body2" color="text.secondary" align="center">
                            {t('consentRegistry.details.noElements')}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ) : null}
                    {purpose.elements.map((element) => (
                      <TableRow key={element.id}>
                        <TableCell>
                          <Box component="code">{element.name}</Box>
                        </TableCell>
                        <TableCell>{element.displayName ?? '-'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </AccordionDetails>
          </Accordion>
        ))}
      </CardContent>
    </Card>
  )
}

export default ConsentPurposesSection
