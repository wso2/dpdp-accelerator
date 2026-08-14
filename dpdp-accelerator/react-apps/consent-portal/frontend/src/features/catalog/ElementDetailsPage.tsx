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
  Button,
  Card,
  CardContent,
  CardHeader,
  Divider,
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
import { AlignLeft, Fingerprint, Tag, Trash2, Type as TypeIcon } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import CopyableText from '../../components/CopyableText'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import { APIError } from '../../utils/apiClient'
import { PORTAL_SCOPES } from '../../utils/portalScopes'
import useAuthorization from '../auth/useAuthorization'
import DetailGrid from './components/DetailGrid'
import ElementDeleteDialog from './components/ElementDeleteDialog'
import { useDeleteElementMutation, useElementQuery } from './hooks/useCatalogQueries'

/**
 * Translates the delete failure into a message a user can act on. A 409 here
 * has one well-known cause -- the element is still referenced by a purpose --
 * so it gets a precise message regardless of the upstream's own wording;
 * anything else falls back to a plain apology rather than surfacing raw
 * server text.
 */
function deleteErrorMessage(error: Error | null, t: (key: string) => string): string | undefined {
  if (!error) {
    return undefined
  }
  if (error instanceof APIError && error.status === 409) {
    return t('catalog.elementDelete.conflict')
  }
  return t('catalog.elementDelete.deleteFailed')
}

function ElementDetailsPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const detailQuery = useElementQuery(id)
  const detail = detailQuery.data
  const { hasScope } = useAuthorization()
  const canWrite = hasScope(PORTAL_SCOPES.ELEMENTS_WRITE)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const deleteMutation = useDeleteElementMutation()
  const propertyEntries = Object.entries(detail?.properties ?? {})

  if (detailQuery.isLoading) {
    return (
      <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
        <Stack spacing={3}>
          <HeaderBreadcrumbs />
          <Skeleton width={300} height={48} />
          <Skeleton variant="rounded" height={220} />
        </Stack>
      </Box>
    )
  }

  if (!id || detailQuery.isError || !detail) {
    return (
      <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
        <Stack spacing={2}>
          <Typography color="error.main">{t('catalog.elements.loadFailed')}</Typography>
          <Box>
            <Button variant="outlined" onClick={() => navigate('/elements')}>
              {t('catalog.elements.back')}
            </Button>
          </Box>
        </Stack>
      </Box>
    )
  }

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={3}>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
          <Stack spacing={1} minWidth={0}>
            <HeaderBreadcrumbs currentLabel={detail.name} />
            <Typography variant="h4" fontWeight={700} sx={{ overflowWrap: 'anywhere' }}>
              {detail.displayName ?? detail.name}
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
                  {t('catalog.fields.elementId')}:
                </Typography>
                <CopyableText
                  value={detail.id}
                  monospace
                  textAriaLabel={t('copyableText.valueAriaLabel', {
                    label: t('catalog.fields.elementId'),
                    value: detail.id,
                  })}
                  copyTooltip={t('copyableText.copyLabel', {
                    label: t('catalog.fields.elementId'),
                  })}
                  copyAriaLabel={t('copyableText.copyValue', {
                    label: t('catalog.fields.elementId'),
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
                  icon: <TypeIcon size={14} />,
                  label: t('catalog.fields.displayName'),
                  value: detail.displayName ?? '-',
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
      </Stack>

      <ElementDeleteDialog
        open={deleteOpen}
        elementName={detail.displayName ?? detail.name}
        loading={deleteMutation.isPending}
        error={deleteErrorMessage(deleteMutation.error, t)}
        onClose={() => {
          setDeleteOpen(false)
          deleteMutation.reset()
        }}
        onConfirm={() => {
          deleteMutation.mutate(detail.id, {
            onSuccess: () => navigate('/elements'),
          })
        }}
      />
    </Box>
  )
}

export default ElementDetailsPage
