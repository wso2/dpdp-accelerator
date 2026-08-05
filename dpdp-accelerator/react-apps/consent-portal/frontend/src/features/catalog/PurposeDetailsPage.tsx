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
  Card,
  CardContent,
  CardHeader,
  Chip,
  Divider,
  Link,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@wso2/oxygen-ui'
import {
  AlignLeft,
  Building2,
  Fingerprint,
  GitBranch,
  KeyRound,
  Shapes,
  Tag,
} from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import CopyableText from '../../components/CopyableText'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import DetailGrid from './components/DetailGrid'
import { usePurposeQuery, usePurposeVersionsQuery } from './hooks/useCatalogQueries'

function PurposeDetailsPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const detailQuery = usePurposeQuery(id)
  const versionsQuery = usePurposeVersionsQuery(id)
  const detail = detailQuery.data
  const versions = versionsQuery.data?.Versions ?? []

  if (detailQuery.isLoading) {
    return (
      <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
        <Stack spacing={3}>
          <HeaderBreadcrumbs />
          <Skeleton width={300} height={48} />
          <Skeleton variant="rounded" height={190} />
          <Skeleton variant="rounded" height={220} />
          <Skeleton variant="rounded" height={260} />
        </Stack>
      </Box>
    )
  }

  if (!id || detailQuery.isError || !detail) {
    return (
      <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
        <Stack spacing={2}>
          <Typography color="error.main">{t('catalog.purposes.loadFailed')}</Typography>
          <Box>
            <Button variant="outlined" onClick={() => navigate('/purposes')}>
              {t('catalog.purposes.back')}
            </Button>
          </Box>
        </Stack>
      </Box>
    )
  }

  const propertyEntries = Object.entries(detail.properties ?? {})

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack spacing={1} minWidth={0}>
          <HeaderBreadcrumbs currentLabel={detail.name} />
          <Typography variant="h4" fontWeight={700} sx={{ overflowWrap: 'anywhere' }}>
            {detail.name}
          </Typography>
        </Stack>

        <Card sx={{ boxShadow: 1 }}>
          <CardHeader
            title={
              <Stack direction="row" spacing={1} alignItems="center">
                <Fingerprint size={15} />
                <Typography variant="body2" fontWeight={400}>
                  {t('catalog.fields.purposeId')}:
                </Typography>
                <CopyableText
                  value={detail.id}
                  monospace
                  textAriaLabel={t('copyableText.valueAriaLabel', {
                    label: t('catalog.fields.purposeId'),
                    value: detail.id,
                  })}
                  copyTooltip={t('copyableText.copyLabel', {
                    label: t('catalog.fields.purposeId'),
                  })}
                  copyAriaLabel={t('copyableText.copyValue', {
                    label: t('catalog.fields.purposeId'),
                    value: detail.id,
                  })}
                />
              </Stack>
            }
            sx={{ pb: 1 }}
          />
          <Divider />
          <CardContent sx={{ pt: 3 }}>
            <DetailGrid
              fields={[
                {
                  icon: <Tag size={14} />,
                  label: t('catalog.fields.name'),
                  value: <Box component="code">{detail.name}</Box>,
                },
                {
                  icon: <Shapes size={14} />,
                  label: t('catalog.fields.type'),
                  value: <Chip size="small" variant="outlined" label={detail.type} />,
                },
                {
                  icon: <GitBranch size={14} />,
                  label: t('catalog.fields.latestVersion'),
                  value: detail.latestVersion ? (
                    <Chip size="small" color="primary" label={detail.latestVersion.version} />
                  ) : (
                    '-'
                  ),
                },
                {
                  icon: <Building2 size={14} />,
                  label: t('catalog.fields.tenantDomain'),
                  value: detail.tenantDomain ?? '-',
                },
                {
                  icon: <AlignLeft size={14} />,
                  label: t('catalog.fields.description'),
                  value: detail.description ?? '-',
                },
              ]}
            />
          </CardContent>
        </Card>

        <Card sx={{ boxShadow: 1 }}>
          <CardHeader
            title={
              <Typography variant="h5" fontWeight={600}>
                {t('catalog.fields.properties')}
              </Typography>
            }
            sx={{ pb: 1 }}
          />
          <Divider />
          <CardContent>
            {propertyEntries.length > 0 ? (
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
                  gap: 2,
                }}
              >
                {propertyEntries.map(([key, value]) => (
                  <Stack key={key} spacing={0}>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      fontWeight={700}
                      sx={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 0.5,
                        mb: 1,
                        textTransform: 'uppercase',
                        letterSpacing: 0.5,
                      }}
                    >
                      <KeyRound size={13} />
                      {key}
                    </Typography>
                    <Typography variant="body2" fontWeight={500} sx={{ overflowWrap: 'anywhere' }}>
                      {value}
                    </Typography>
                  </Stack>
                ))}
              </Box>
            ) : (
              <Typography variant="body2" color="text.secondary">
                {t('catalog.messages.noProperties')}
              </Typography>
            )}
          </CardContent>
        </Card>

        <Card sx={{ boxShadow: 1 }}>
          <CardHeader
            title={
              <Typography variant="h5" fontWeight={600}>
                {t('catalog.details.elements')}
              </Typography>
            }
            sx={{ pb: 1 }}
          />
          <Divider />
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>{t('catalog.fields.element')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('catalog.fields.description')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('catalog.fields.requirement')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {detail.elements.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={3}>
                      <Typography
                        variant="body2"
                        color="text.secondary"
                        align="center"
                        sx={{ py: 3 }}
                      >
                        {t('catalog.messages.noElements')}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : null}
                {detail.elements.map((element) => {
                  const elementPath = `/elements/${encodeURIComponent(element.id)}`

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
                            {element.displayName ?? element.name}
                          </Link>
                          <Typography variant="caption" color="text.secondary">
                            <Box component="code">{element.name}</Box>
                          </Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>{element.description ?? '-'}</TableCell>
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
        </Card>

        <Card sx={{ boxShadow: 1 }}>
          <CardHeader
            title={
              <Typography variant="h5" fontWeight={600}>
                {t('catalog.details.versions')}
              </Typography>
            }
            sx={{ pb: 1 }}
          />
          <Divider />
          {versionsQuery.isLoading ? (
            <CardContent>
              <Stack spacing={1}>
                <Skeleton height={36} />
                <Skeleton height={36} />
              </Stack>
            </CardContent>
          ) : null}
          {versionsQuery.isError ? (
            <CardContent>
              <Alert severity="error">{t('catalog.messages.versionsLoadFailed')}</Alert>
            </CardContent>
          ) : null}
          {!versionsQuery.isLoading && !versionsQuery.isError ? (
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>{t('catalog.fields.version')}</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>
                      {t('catalog.fields.description')}
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {versions.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={2} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                        {t('catalog.messages.noVersions')}
                      </TableCell>
                    </TableRow>
                  ) : null}
                  {versions.map((version) => (
                    <TableRow hover key={version.id}>
                      <TableCell>
                        <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap">
                          <Chip size="small" color="primary" label={version.version} />
                          {detail.latestVersion?.id === version.id ? (
                            <Chip size="small" label={t('catalog.values.latest')} />
                          ) : null}
                        </Stack>
                      </TableCell>
                      <TableCell>{version.description ?? '-'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ) : null}
        </Card>
      </Stack>
    </Box>
  )
}

export default PurposeDetailsPage
