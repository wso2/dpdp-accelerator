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
  Avatar,
  Button,
  Card,
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
import { Eye } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import type { ConsentAuthorizationResource } from '../../../../types/consent'
import { formatEpochTimestamp } from '../../../../utils/dateTime'
import { getConsentStatusChipColor, getConsentStatusLabelKey } from '../../utils/statusChip'

interface ConsentAuthorizationsSectionProps {
  authorizations: ConsentAuthorizationResource[]
  onViewResources: (resources: unknown) => void
}

function isEmptyAuthorizationResources(resources: unknown): boolean {
  if (resources == null) {
    return true
  }

  if (typeof resources === 'string') {
    return resources.trim().length === 0
  }

  if (Array.isArray(resources)) {
    return resources.length === 0
  }

  if (typeof resources === 'object') {
    return Object.keys(resources as Record<string, unknown>).length === 0
  }

  return false
}

function ConsentAuthorizationsSection({
  authorizations,
  onViewResources,
}: ConsentAuthorizationsSectionProps): React.JSX.Element {
  const { t } = useTranslation('common')

  return (
    <Card sx={{ boxShadow: 1 }}>
      <CardHeader
        title={
          <Typography variant="h5" fontWeight={600}>
            {t('consentRegistry.details.section.authorizations', 'Authorizations')}
          </Typography>
        }
        sx={{ pb: 1 }}
      />
      <Divider />
      <TableContainer>
        <Table sx={{ '& tbody tr:hover': { bgcolor: 'action.hover' } }}>
          <TableHead>
            <TableRow sx={{ bgcolor: 'action.default' }}>
              <TableCell sx={{ fontWeight: 700 }}>
                {t('consentRegistry.details.table.user', 'User')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>
                {t('consentRegistry.details.table.status', 'Status')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>
                {t('consentRegistry.details.table.updated', 'Updated')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>
                {t('consentRegistry.details.table.resources', 'Resources')}
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {authorizations.map((authorization) => {
              const resourcesEmpty = isEmptyAuthorizationResources(authorization.resources)

              return (
                <TableRow key={authorization.id}>
                  <TableCell>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Avatar sx={{ width: 24, height: 24, fontSize: '0.75rem' }}>
                        {(authorization.userId ?? 'U').charAt(0).toUpperCase()}
                      </Avatar>
                      <Typography variant="body2">{authorization.userId ?? '-'}</Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={t(
                        `consentRegistry.status.${getConsentStatusLabelKey(
                          authorization.status,
                          'authorization',
                        )}`,
                      )}
                      color={getConsentStatusChipColor(authorization.status)}
                      size="small"
                      variant="outlined"
                    />
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">
                      {formatEpochTimestamp(authorization.updatedTime)}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Tooltip
                      title={t(
                        'consentRegistry.details.actions.noResourcesTooltip',
                        'No resources available',
                      )}
                      disableHoverListener={!resourcesEmpty}
                    >
                      <span>
                        <Button
                          size="small"
                          variant="contained"
                          color="secondary"
                          startIcon={<Eye size={14} />}
                          disabled={resourcesEmpty}
                          onClick={() => {
                            onViewResources(authorization.resources)
                          }}
                        >
                          {t('consentRegistry.details.actions.viewResources', 'View Resources')}
                        </Button>
                      </span>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </TableContainer>
    </Card>
  )
}

export default ConsentAuthorizationsSection
