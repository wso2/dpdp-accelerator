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
  Link,
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
import {
  AlignLeft,
  Fingerprint,
  GitBranch,
  Plus,
  Shapes,
  Star,
  Tag,
  Trash2,
} from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import CopyableText from '../../components/CopyableText'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import { APIError } from '../../utils/apiClient'
import { PORTAL_SCOPES } from '../../utils/portalScopes'
import useAuthorization from '../auth/useAuthorization'
import DetailGrid from './components/DetailGrid'
import PurposeDeleteDialog from './components/PurposeDeleteDialog'
import PurposeVersionDeleteDialog from './components/PurposeVersionDeleteDialog'
import PurposeVersionFormDialog from './components/PurposeVersionFormDialog'
import {
  useCreatePurposeVersionMutation,
  useDeletePurposeMutation,
  useDeletePurposeVersionMutation,
  usePurposeQuery,
  usePurposeVersionsQuery,
  useSetLatestPurposeVersionMutation,
} from './hooks/useCatalogQueries'

function PurposeDetailsPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const detailQuery = usePurposeQuery(id)
  const versionsQuery = usePurposeVersionsQuery(id)
  const detail = detailQuery.data
  const versions = versionsQuery.data?.Versions ?? []
  const { hasScope } = useAuthorization()
  const canWrite = hasScope(PORTAL_SCOPES.PURPOSES_WRITE)

  const [deleteOpen, setDeleteOpen] = useState(false)
  const [versionFormOpen, setVersionFormOpen] = useState(false)
  const [versionToDelete, setVersionToDelete] = useState<{ id: string; version: string }>()

  const deletePurposeMutation = useDeletePurposeMutation()
  const createVersionMutation = useCreatePurposeVersionMutation()
  const setLatestMutation = useSetLatestPurposeVersionMutation()
  const deleteVersionMutation = useDeletePurposeVersionMutation()

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
  // The purpose's own description/elements/properties always mirror whichever
  // version is currently latest -- verified live, not documented behaviour --
  // so a new version starts from these rather than an empty form.
  const versionFormSource = {
    description: detail.description,
    elements: detail.elements.map((element) => ({
      id: element.id,
      name: element.name,
      displayName: element.displayName,
      mandatory: element.mandatory,
    })),
    properties: detail.properties,
  }
  const existingVersions = versions.map((version) => version.version)

  // A 409 here has one well-known cause -- the purpose is still referenced by
  // a consent -- so it gets a precise, actionable message regardless of the
  // upstream's own wording; anything else falls back to a plain apology
  // rather than surfacing raw server text.
  let deleteErrorMessage: string | undefined
  if (deletePurposeMutation.error) {
    deleteErrorMessage =
      deletePurposeMutation.error instanceof APIError && deletePurposeMutation.error.status === 409
        ? t('catalog.purposeDelete.conflict')
        : t('catalog.purposeDelete.deleteFailed')
  }
  // Duplicate version names are rejected before submit (see
  // PurposeVersionFormDialog), so any server error here is unexpected.
  const createVersionErrorMessage = createVersionMutation.error
    ? t('catalog.purposeVersionForm.createFailed')
    : undefined
  // Deleting the latest version is disabled in the table below, so any
  // server error here is unexpected.
  const deleteVersionErrorMessage = deleteVersionMutation.error
    ? t('catalog.purposeVersionDelete.deleteFailed')
    : undefined

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
          <Stack spacing={1} minWidth={0}>
            <HeaderBreadcrumbs currentLabel={detail.name} />
            <Typography variant="h4" fontWeight={700} sx={{ overflowWrap: 'anywhere' }}>
              {detail.name}
            </Typography>
          </Stack>
          {canWrite ? (
            <Button
              variant="outlined"
              color="error"
              startIcon={<Trash2 size={16} />}
              sx={{ flexShrink: 0 }}
              onClick={() => setDeleteOpen(true)}
            >
              {t('catalog.actions.delete')}
            </Button>
          ) : null}
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
          {propertyEntries.length > 0 ? (
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700, width: '35%' }}>
                      {t('catalog.elementForm.propertyKeyLabel')}
                    </TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>
                      {t('catalog.elementForm.propertyValueLabel')}
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {propertyEntries.map(([key, value]) => (
                    <TableRow key={key} hover>
                      <TableCell>
                        <Box component="code">{key}</Box>
                      </TableCell>
                      <TableCell sx={{ overflowWrap: 'anywhere' }}>{value}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ) : (
            <CardContent>
              <Typography variant="body2" color="text.secondary">
                {t('catalog.messages.noProperties')}
              </Typography>
            </CardContent>
          )}
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
            action={
              canWrite ? (
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<Plus size={16} />}
                  onClick={() => setVersionFormOpen(true)}
                >
                  {t('catalog.actions.addVersion')}
                </Button>
              ) : null
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
          {setLatestMutation.isError ? (
            <CardContent>
              <Alert severity="error">{t('catalog.messages.setLatestFailed')}</Alert>
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
                    {canWrite ? (
                      <TableCell sx={{ fontWeight: 700 }} align="right">
                        {t('catalog.fields.actions')}
                      </TableCell>
                    ) : null}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {versions.length === 0 ? (
                    <TableRow>
                      <TableCell
                        colSpan={canWrite ? 3 : 2}
                        align="center"
                        sx={{ py: 4, color: 'text.secondary' }}
                      >
                        {t('catalog.messages.noVersions')}
                      </TableCell>
                    </TableRow>
                  ) : null}
                  {versions.map((version) => {
                    const isLatest = detail.latestVersion?.id === version.id

                    return (
                      <TableRow hover key={version.id}>
                        <TableCell>
                          <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap">
                            <Chip size="small" color="primary" label={version.version} />
                            {isLatest ? (
                              <Chip size="small" label={t('catalog.values.latest')} />
                            ) : null}
                          </Stack>
                        </TableCell>
                        <TableCell>{version.description ?? '-'}</TableCell>
                        {canWrite ? (
                          <TableCell align="right">
                            <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                              {!isLatest ? (
                                <Tooltip title={t('catalog.actions.setLatest')}>
                                  <IconButton
                                    size="small"
                                    disabled={setLatestMutation.isPending}
                                    aria-label={t('catalog.actions.setLatest')}
                                    onClick={() =>
                                      setLatestMutation.mutate({
                                        purposeId: id,
                                        versionId: version.id,
                                      })
                                    }
                                  >
                                    <Star size={16} />
                                  </IconButton>
                                </Tooltip>
                              ) : null}
                              <Tooltip
                                title={
                                  isLatest
                                    ? t('catalog.purposeVersionDelete.latestBlocked')
                                    : t('catalog.actions.delete')
                                }
                              >
                                <span>
                                  <IconButton
                                    size="small"
                                    disabled={isLatest}
                                    aria-label={t('catalog.actions.delete')}
                                    onClick={() =>
                                      setVersionToDelete({
                                        id: version.id,
                                        version: version.version,
                                      })
                                    }
                                  >
                                    <Trash2 size={16} />
                                  </IconButton>
                                </span>
                              </Tooltip>
                            </Stack>
                          </TableCell>
                        ) : null}
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          ) : null}
        </Card>
      </Stack>

      <PurposeDeleteDialog
        open={deleteOpen}
        purposeName={detail.name}
        loading={deletePurposeMutation.isPending}
        error={deleteErrorMessage}
        onClose={() => {
          setDeleteOpen(false)
          deletePurposeMutation.reset()
        }}
        onConfirm={() => {
          deletePurposeMutation.mutate(detail.id, {
            onSuccess: () => navigate('/purposes'),
          })
        }}
      />

      <PurposeVersionFormDialog
        open={versionFormOpen}
        loading={createVersionMutation.isPending}
        error={createVersionErrorMessage}
        existingVersions={existingVersions}
        source={versionFormSource}
        onClose={() => {
          setVersionFormOpen(false)
          createVersionMutation.reset()
        }}
        onSubmit={(payload) => {
          createVersionMutation.mutate(
            { purposeId: id, payload },
            { onSuccess: () => setVersionFormOpen(false) },
          )
        }}
      />

      <PurposeVersionDeleteDialog
        open={Boolean(versionToDelete)}
        version={versionToDelete?.version ?? ''}
        loading={deleteVersionMutation.isPending}
        error={deleteVersionErrorMessage}
        onClose={() => {
          setVersionToDelete(undefined)
          deleteVersionMutation.reset()
        }}
        onConfirm={() => {
          if (!versionToDelete) {
            return
          }
          deleteVersionMutation.mutate(
            { purposeId: id, versionId: versionToDelete.id },
            { onSuccess: () => setVersionToDelete(undefined) },
          )
        }}
      />
    </Box>
  )
}

export default PurposeDetailsPage
