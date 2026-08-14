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
  IconButton,
  MenuItem,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@wso2/oxygen-ui'
import {
  AlignLeft,
  Boxes,
  CalendarClock,
  Eye,
  Fingerprint,
  GitBranch,
  KeyRound,
  Plus,
  Shapes,
  Tag,
  Trash2,
  Type as TypeIcon,
} from '@wso2/oxygen-ui-icons-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import CopyableText from '../../components/CopyableText'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import useAuthorization from '../auth/useAuthorization'
import type { ElementVersionItem } from '../../types/catalog'
import { formatEpochTimestamp } from '../../utils/dateTime'
import { PORTAL_SCOPES } from '../../utils/portalScopes'
import DeleteVersionDialog from './components/DeleteVersionDialog'
import ElementFormDialog from './components/ElementFormDialog'
import ElementTypeChip from './components/ElementTypeChip'
import {
  useCreateElementVersionMutation,
  useDeleteElementVersionMutation,
  useElementQuery,
  useElementVersionsQuery,
} from './hooks/useCatalogQueries'

interface DetailField {
  icon: React.ReactNode
  label: string
  value: React.ReactNode
}

function DetailGrid({ fields }: { fields: DetailField[] }): React.JSX.Element {
  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' },
        gap: { xs: 2, md: 3 },
      }}
    >
      {fields.map((field) => (
        <Stack key={field.label} spacing={0} minWidth={0}>
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
            {field.icon}
            {field.label}
          </Typography>
          <Typography
            component="div"
            variant="body2"
            fontWeight={500}
            sx={{ overflowWrap: 'anywhere' }}
          >
            {field.value || '-'}
          </Typography>
        </Stack>
      ))}
    </Box>
  )
}

function ElementDetailsPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const detailQuery = useElementQuery(id)
  const versionsQuery = useElementVersionsQuery(id)
  const createVersionMutation = useCreateElementVersionMutation()
  const deleteMutation = useDeleteElementVersionMutation()
  const [selectedVersion, setSelectedVersion] = useState<string>()
  const [versionDialogOpen, setVersionDialogOpen] = useState(false)
  const [deleteVersion, setDeleteVersion] = useState<string>()
  const { hasScope } = useAuthorization()
  const canWriteElements = hasScope(PORTAL_SCOPES.ELEMENTS_WRITE)

  const detail = detailQuery.data
  const latestVersion = useMemo<ElementVersionItem | undefined>(
    () =>
      detail
        ? {
            version: detail.version,
            displayName: detail.displayName,
            description: detail.description,
            schema: detail.schema,
            properties: detail.properties,
            createdTime: detail.createdTime,
          }
        : undefined,
    [detail],
  )
  const versions = useMemo(() => versionsQuery.data?.versions ?? [], [versionsQuery.data?.versions])
  const versionOptions = useMemo(() => {
    if (!latestVersion || versions.some((version) => version.version === latestVersion.version)) {
      return versions
    }
    return [latestVersion, ...versions]
  }, [latestVersion, versions])
  const displayedVersion =
    versionOptions.find((version) => version.version === selectedVersion) ?? latestVersion

  if (detailQuery.isLoading) {
    return (
      <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
        <Stack spacing={3}>
          <HeaderBreadcrumbs />
          <Skeleton width={300} height={48} />
          <Skeleton variant="rounded" height={190} />
          <Skeleton variant="rounded" height={240} />
          <Skeleton variant="rounded" height={260} />
        </Stack>
      </Box>
    )
  }

  if (!id || detailQuery.isError || !detail || !displayedVersion) {
    return (
      <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
        <Stack spacing={2}>
          <Typography color="error.main">{t('catalog.elements.loadFailed')}</Typography>
          <Button variant="outlined" onClick={() => navigate('/elements')}>
            {t('catalog.elements.back')}
          </Button>
        </Stack>
      </Box>
    )
  }

  const propertyEntries = Object.entries(displayedVersion.properties ?? {})

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          justifyContent="space-between"
          alignItems={{ md: 'flex-end' }}
          spacing={2}
        >
          <Stack spacing={1} minWidth={0}>
            <HeaderBreadcrumbs currentLabel={`${detail.namespace}:${detail.name}`} />
            <Typography variant="h4" fontWeight={700} sx={{ overflowWrap: 'anywhere' }}>
              {displayedVersion.displayName ?? detail.name}
            </Typography>
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ sm: 'center' }} spacing={1}>
            <TextField
              select
              size="small"
              value={displayedVersion.version}
              disabled={versionsQuery.isLoading || versionOptions.length === 0}
              slotProps={{
                select: {
                  inputProps: {
                    'aria-label': t('catalog.fields.elementVersion'),
                  },
                },
              }}
              onChange={(event) => {
                const version = event.target.value
                setSelectedVersion(version === detail.version ? undefined : version)
              }}
              sx={{ width: 80 }}
            >
              {versionOptions.map((version) => (
                <MenuItem key={version.version} value={version.version}>
                  {version.version}
                </MenuItem>
              ))}
            </TextField>
            {canWriteElements ? (
              <Button
                variant="contained"
                startIcon={<Plus size={18} />}
                onClick={() => setVersionDialogOpen(true)}
              >
                {t('catalog.elements.newVersion')}
              </Button>
            ) : null}
          </Stack>
        </Stack>

        <Card sx={{ boxShadow: 1 }}>
          <CardHeader
            title={
              <Stack direction="row" spacing={1} alignItems="center">
                <Fingerprint size={15} />
                <Typography variant="body2" fontWeight={400}>
                  {t('catalog.fields.elementId')}:
                </Typography>
                <CopyableText
                  value={detail.elementId}
                  monospace
                  textAriaLabel={t('copyableText.valueAriaLabel', {
                    label: t('catalog.fields.elementId'),
                    value: detail.elementId,
                  })}
                  copyTooltip={t('copyableText.copyLabel', {
                    label: t('catalog.fields.elementId'),
                  })}
                  copyAriaLabel={t('copyableText.copyValue', {
                    label: t('catalog.fields.elementId'),
                    value: detail.elementId,
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
                  icon: <Boxes size={14} />,
                  label: t('catalog.fields.namespace'),
                  value: detail.namespace || '-',
                },
                {
                  icon: <Shapes size={14} />,
                  label: t('catalog.fields.type'),
                  value: <ElementTypeChip type={detail.type} />,
                },
              ]}
            />
          </CardContent>
        </Card>

        <Card sx={{ boxShadow: 1 }}>
          <CardHeader
            title={
              <Typography variant="h5" fontWeight={600}>
                {t('catalog.details.versionDetails')}
              </Typography>
            }
            sx={{ pb: 1 }}
          />
          <Divider />
          <CardContent>
            <DetailGrid
              fields={[
                {
                  icon: <GitBranch size={14} />,
                  label: t('catalog.fields.version'),
                  value: <Chip size="small" color="primary" label={displayedVersion.version} />,
                },
                {
                  icon: <TypeIcon size={14} />,
                  label: t('catalog.fields.displayName'),
                  value: displayedVersion.displayName ?? '-',
                },
                {
                  icon: <CalendarClock size={14} />,
                  label: t('catalog.fields.created'),
                  value: formatEpochTimestamp(displayedVersion.createdTime),
                },
                {
                  icon: <AlignLeft size={14} />,
                  label: t('catalog.fields.description'),
                  value: displayedVersion.description ?? '-',
                },
              ]}
            />
          </CardContent>
        </Card>

        {detail.type !== 'basic' ? (
          <Card sx={{ boxShadow: 1 }}>
            <CardHeader
              title={
                <Typography variant="h5" fontWeight={600}>
                  {t('catalog.fields.schema')}
                </Typography>
              }
              sx={{ pb: 1 }}
            />
            <Divider />
            <CardContent>
              {displayedVersion.schema ? (
                <Box
                  component="pre"
                  sx={{
                    m: 0,
                    p: 2,
                    borderRadius: 1,
                    bgcolor: 'action.hover',
                    overflow: 'auto',
                    whiteSpace: 'pre-wrap',
                    overflowWrap: 'anywhere',
                  }}
                >
                  {displayedVersion.schema}
                </Box>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  {t('catalog.messages.noSchema')}
                </Typography>
              )}
            </CardContent>
          </Card>
        ) : null}

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
                <Skeleton height={36} />
              </Stack>
            </CardContent>
          ) : null}
          {versionsQuery.isError ? (
            <CardContent>
              <Alert severity="error">
                {versionsQuery.error?.message || t('catalog.messages.versionsLoadFailed')}
              </Alert>
            </CardContent>
          ) : null}
          {!versionsQuery.isLoading && !versionsQuery.isError ? (
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>{t('catalog.fields.version')}</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>
                      {t('catalog.fields.displayName')}
                    </TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>
                      {t('catalog.fields.description')}
                    </TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>{t('catalog.fields.created')}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>
                      {t('catalog.fields.actions')}
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {versionOptions.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={5} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                        {t('catalog.messages.noVersions')}
                      </TableCell>
                    </TableRow>
                  ) : null}
                  {versionOptions.map((version) => (
                    <TableRow
                      hover
                      key={version.version}
                      selected={displayedVersion.version === version.version}
                      sx={{ cursor: 'pointer' }}
                      onClick={() =>
                        setSelectedVersion(
                          version.version === detail.version ? undefined : version.version,
                        )
                      }
                    >
                      <TableCell>
                        <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap">
                          <Chip size="small" color="primary" label={version.version} />
                          {displayedVersion.version === version.version ? (
                            <Chip
                              size="small"
                              icon={<Eye size={14} />}
                              label={t('catalog.values.viewing')}
                            />
                          ) : null}
                        </Stack>
                      </TableCell>
                      <TableCell>{version.displayName ?? '-'}</TableCell>
                      <TableCell>{version.description ?? '-'}</TableCell>
                      <TableCell>{formatEpochTimestamp(version.createdTime)}</TableCell>
                      <TableCell align="right">
                        {canWriteElements ? (
                          <Tooltip title={t('catalog.actions.deleteVersion')}>
                            <IconButton
                              size="small"
                              aria-label={t('catalog.actions.deleteVersion')}
                              onClick={(event) => {
                                event.stopPropagation()
                                setDeleteVersion(version.version)
                              }}
                            >
                              <Trash2 size={17} />
                            </IconButton>
                          </Tooltip>
                        ) : null}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ) : null}
        </Card>
      </Stack>

      <ElementFormDialog
        key={`element-version-${String(versionDialogOpen)}`}
        open={versionDialogOpen}
        initialValue={detail}
        loading={createVersionMutation.isPending}
        error={createVersionMutation.error?.message}
        onClose={() => {
          setVersionDialogOpen(false)
          createVersionMutation.reset()
        }}
        onCreate={() => {}}
        onCreateVersion={(payload) => {
          createVersionMutation.mutate(
            { elementId: id, payload },
            {
              onSuccess: (version) => {
                setVersionDialogOpen(false)
                setSelectedVersion(version.version)
              },
            },
          )
        }}
      />
      <DeleteVersionDialog
        open={Boolean(deleteVersion)}
        version={deleteVersion ?? ''}
        loading={deleteMutation.isPending}
        error={deleteMutation.error?.message}
        onClose={() => {
          setDeleteVersion(undefined)
          deleteMutation.reset()
        }}
        onConfirm={() => {
          if (!deleteVersion) return
          deleteMutation.mutate(
            { id, version: deleteVersion },
            {
              onSuccess: () => {
                setDeleteVersion(undefined)
                setSelectedVersion(undefined)
                if (versions.length === 1) navigate('/elements')
              },
            },
          )
        }}
      />
    </Box>
  )
}

export default ElementDetailsPage
