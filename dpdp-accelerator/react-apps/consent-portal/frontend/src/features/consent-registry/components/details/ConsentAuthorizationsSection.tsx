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
  Typography,
} from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'
import type { ConsentAuthorization } from '../../../../types/consent'
import { formatEpochTimestamp } from '../../../../utils/dateTime'
import { getConsentStateChipColor, getConsentStateLabelKey } from '../../utils/statusChip'

interface ConsentAuthorizationsSectionProps {
  authorizations: ConsentAuthorization[]
}

function ConsentAuthorizationsSection({
  authorizations,
}: ConsentAuthorizationsSectionProps): React.JSX.Element {
  const { t } = useTranslation('common')

  return (
    <Card sx={{ boxShadow: 1 }}>
      <CardHeader
        title={
          <Typography variant="h5" fontWeight={600}>
            {t('consentRegistry.details.section.authorizations')}
          </Typography>
        }
        sx={{ pb: 1 }}
      />
      <Divider />
      <TableContainer>
        <Table
          aria-label={t('consentRegistry.details.section.authorizations')}
          sx={{ '& tbody tr:hover': { bgcolor: 'action.hover' } }}
        >
          <TableHead>
            <TableRow sx={{ bgcolor: 'action.default' }}>
              <TableCell sx={{ fontWeight: 700 }}>
                {t('consentRegistry.details.table.user')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>
                {t('consentRegistry.details.table.state')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>
                {t('consentRegistry.details.table.updated')}
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {authorizations.length === 0 ? (
              <TableRow>
                <TableCell colSpan={3}>
                  <Typography variant="body2" color="text.secondary" align="center">
                    {t('consentRegistry.details.noAuthorizations')}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : null}
            {authorizations.map((authorization) => (
              <TableRow key={`${authorization.userId}-${String(authorization.updatedTime)}`}>
                <TableCell>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Avatar sx={{ width: 24, height: 24, fontSize: '0.75rem' }}>
                      {authorization.userId.charAt(0).toUpperCase()}
                    </Avatar>
                    <Typography variant="body2">{authorization.userId}</Typography>
                  </Stack>
                </TableCell>
                <TableCell>
                  <Chip
                    label={t(
                      `consentRegistry.status.${getConsentStateLabelKey(
                        authorization.state,
                        'authorization',
                      )}`,
                    )}
                    color={getConsentStateChipColor(authorization.state)}
                    size="small"
                    variant="outlined"
                  />
                </TableCell>
                <TableCell>
                  <Typography variant="body2">
                    {formatEpochTimestamp(authorization.updatedTime)}
                  </Typography>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Card>
  )
}

export default ConsentAuthorizationsSection
