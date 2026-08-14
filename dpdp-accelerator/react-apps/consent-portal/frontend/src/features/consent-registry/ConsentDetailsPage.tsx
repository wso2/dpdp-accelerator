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
  Typography,
} from '@wso2/oxygen-ui'
import { Ban, CircleCheckBig } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import useAuthorization from '../auth/useAuthorization'
import ConsentApprovalDialog from './components/ConsentApprovalDialog'
import ConsentRejectionDialog from './components/ConsentRejectionDialog'
import ConsentRevocationDialog from './components/ConsentRevocationDialog'
import ConsentAuthorizationsSection from './components/details/ConsentAuthorizationsSection'
import ConsentLifecycleSection from './components/details/ConsentLifecycleSection'
import ConsentMetadataCard from './components/details/ConsentMetadataCard'
import ConsentPurposesSection from './components/details/ConsentPurposesSection'
import ConsentResourcesModal from './components/details/ConsentResourcesModal'
import {
  useApproveConsentMutation,
  useConsentDetailQuery,
  useRejectConsentMutation,
  useRevokeConsentMutation,
} from './hooks/useConsentQueries'
import {
  isConsentApprovableStatus,
  isConsentRejectableStatus,
  isConsentRevokableStatus,
} from './utils/statusChip'
import { PORTAL_SCOPES } from '../../utils/portalScopes'
import {
  useAdminConsentDetailQuery,
  useAdminRevokeConsentMutation,
} from '../admin-consents/hooks/useAdminConsentQueries'
import { useActingAs } from '../nominee/actingAs/actingAsContext'

interface ConsentDetailsPageProps {
  variant?: 'self' | 'admin'
}

function formatResourcesForModal(resources: unknown): string {
  if (!resources) {
    return '-'
  }

  if (typeof resources === 'string') {
    try {
      return JSON.stringify(JSON.parse(resources), null, 2)
    } catch {
      return resources
    }
  }

  try {
    return JSON.stringify(resources, null, 2)
  } catch {
    return String(resources)
  }
}

function ConsentDetailsLoading(): React.JSX.Element {
  return (
    <Box
      component="main"
      sx={{ p: { xs: 2, md: 4 }, display: 'flex', flexDirection: 'column', gap: 3 }}
    >
      <Stack spacing={1}>
        <HeaderBreadcrumbs />
        <Skeleton variant="text" width={220} height={48} />
      </Stack>

      <Card sx={{ boxShadow: 1 }}>
        <CardHeader
          title={<Skeleton variant="text" width={280} />}
          action={
            <Stack direction="row" spacing={1}>
              <Skeleton variant="rounded" width={84} height={24} />
              <Skeleton variant="rounded" width={84} height={24} />
            </Stack>
          }
          sx={{ pb: 1 }}
        />
        <Divider />
        <CardContent sx={{ pt: 3 }}>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(3, 1fr)' },
              gap: { xs: 3, md: 4 },
            }}
          >
            {Array.from({ length: 6 }).map((_, index) => (
              <Box key={`detail-skeleton-${String(index)}`}>
                <Skeleton variant="text" width="45%" />
                <Skeleton variant="text" width="85%" />
              </Box>
            ))}
          </Box>
        </CardContent>
      </Card>

      {['purpose', 'table', 'lifecycle'].map((section) => (
        <Card key={`details-${section}-skeleton`} sx={{ boxShadow: 1 }}>
          <CardHeader title={<Skeleton variant="text" width={180} />} sx={{ pb: 1 }} />
          <Divider />
          <CardContent sx={{ p: 2 }}>
            {Array.from({ length: section === 'purpose' ? 2 : 4 }).map((_, index) => (
              <Box
                key={`${section}-row-skeleton-${String(index)}`}
                sx={{
                  display: section === 'purpose' ? 'block' : 'grid',
                  gridTemplateColumns:
                    section === 'table' ? '1.2fr 1fr 1fr 0.8fr' : '1fr 1fr 1fr 2fr',
                  gap: 2,
                  mb: index === (section === 'purpose' ? 1 : 3) ? 0 : 1,
                }}
              >
                {section === 'purpose' ? (
                  <Skeleton variant="rounded" height={42} />
                ) : (
                  <>
                    <Skeleton variant="text" width="70%" />
                    <Skeleton variant="text" width="80%" />
                    <Skeleton variant="text" width="80%" />
                    <Skeleton variant={section === 'table' ? 'rounded' : 'text'} width="95%" />
                  </>
                )}
              </Box>
            ))}
          </CardContent>
        </Card>
      ))}
    </Box>
  )
}

function ConsentDetailsPage({ variant = 'self' }: ConsentDetailsPageProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const selfConsentDetailQuery = useConsentDetailQuery(variant === 'self' ? id : undefined)
  const adminConsentDetailQuery = useAdminConsentDetailQuery(variant === 'admin' ? id : undefined)
  const approveMutation = useApproveConsentMutation()
  const rejectMutation = useRejectConsentMutation()
  const revokeMutation = useRevokeConsentMutation()
  const adminRevokeMutation = useAdminRevokeConsentMutation()
  const [approvalDialogOpen, setApprovalDialogOpen] = useState<boolean>(false)
  const [rejectionDialogOpen, setRejectionDialogOpen] = useState<boolean>(false)
  const [revocationDialogOpen, setRevocationDialogOpen] = useState<boolean>(false)
  const [resourcesModalOpen, setResourcesModalOpen] = useState<boolean>(false)
  const [selectedResourcesJson, setSelectedResourcesJson] = useState<string>('')
  const { currentUser, hasScope } = useAuthorization()
  const { session } = useActingAs()

  // While acting for an owner, what may be done is what THAT OWNER granted this
  // nominee - not what the nominee holds on their own account. Approving and
  // revoking are granted separately, because they are opposite acts: revoking
  // withdraws processing already chosen, approving authorises new processing.
  //
  // This only hides controls the delegated token could never exercise; the
  // server re-checks the token scope and the live nomination on every call.
  const canWriteSelf = session
    ? session.scope.includes('CONSENT_REVOKE')
    : hasScope(PORTAL_SCOPES.CONSENTS_WRITE_SELF)
  const canApproveSelf = session
    ? session.scope.includes('CONSENT_APPROVE')
    : hasScope(PORTAL_SCOPES.CONSENTS_APPROVE_SELF)
  const canWriteAny = hasScope(PORTAL_SCOPES.CONSENTS_WRITE_ANY)
  const consentDetailQuery = variant === 'admin' ? adminConsentDetailQuery : selfConsentDetailQuery
  const backPath = variant === 'admin' ? '/administration/consents' : '/consents'
  const revokePending =
    variant === 'admin' ? adminRevokeMutation.isPending : revokeMutation.isPending

  if (!id) {
    return (
      <Box
        component="main"
        sx={{ p: { xs: 2, md: 4 }, display: 'flex', flexDirection: 'column', gap: 2 }}
      >
        <Typography variant="h5">{t('consentRegistry.details.notFound')}</Typography>
        <Box>
          <Button variant="outlined" onClick={() => navigate(backPath)}>
            {t('consentRegistry.details.back')}
          </Button>
        </Box>
      </Box>
    )
  }

  const detail = consentDetailQuery.data
  const canApprove =
    variant === 'self' && detail
      ? canApproveSelf && isConsentApprovableStatus(detail.status)
      : false
  const canReject =
    variant === 'self' && detail && !session
      ? canWriteSelf && isConsentRejectableStatus(detail.status)
      : false
  const canRevoke = detail
    ? (variant === 'admin' ? canWriteAny : canWriteSelf) && isConsentRevokableStatus(detail.status)
    : false

  if (consentDetailQuery.isLoading) {
    return <ConsentDetailsLoading />
  }

  if (consentDetailQuery.isError || !detail) {
    return (
      <Box
        component="main"
        sx={{ p: { xs: 2, md: 4 }, display: 'flex', flexDirection: 'column', gap: 2 }}
      >
        <Typography color="error.main">{t('consentRegistry.messages.loadFailed')}</Typography>
        <Box>
          <Button variant="outlined" onClick={() => navigate(backPath)}>
            {t('consentRegistry.details.back')}
          </Button>
        </Box>
      </Box>
    )
  }

  return (
    <Box
      component="main"
      sx={{ p: { xs: 2, md: 4 }, display: 'flex', flexDirection: 'column', gap: 3 }}
    >
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        justifyContent="space-between"
        alignItems={{ md: 'flex-end' }}
        spacing={2}
      >
        <Stack spacing={1} minWidth={0}>
          <HeaderBreadcrumbs />
          <Typography variant="h4" fontWeight={700} sx={{ overflowWrap: 'anywhere' }}>
            {t('consentRegistry.details.title', 'Consent Details')}
          </Typography>
        </Stack>
        <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ sm: 'center' }} spacing={1}>
          {canApprove ? (
            <Button
              variant="contained"
              color="warning"
              size="small"
              startIcon={<CircleCheckBig size={16} />}
              disabled={approveMutation.isPending}
              onClick={() => {
                setApprovalDialogOpen(true)
              }}
            >
              {t('consentRegistry.actions.approve')}
            </Button>
          ) : null}
          {canReject ? (
            <Button
              variant="contained"
              color="error"
              size="small"
              startIcon={<Ban size={16} />}
              disabled={rejectMutation.isPending}
              onClick={() => {
                setRejectionDialogOpen(true)
              }}
            >
              {t('consentRegistry.actions.reject')}
            </Button>
          ) : null}
          {canRevoke ? (
            <Button
              variant="contained"
              color="error"
              size="small"
              startIcon={<Ban size={16} />}
              disabled={revokePending}
              onClick={() => {
                setRevocationDialogOpen(true)
              }}
            >
              {t('consentRegistry.actions.revoke')}
            </Button>
          ) : null}
        </Stack>
      </Stack>

      <ConsentMetadataCard consentId={id} detail={detail} />
      <ConsentPurposesSection purposes={detail.purposes} />
      <ConsentAuthorizationsSection
        authorizations={detail.authorizations ?? []}
        onViewResources={(resources) => {
          setSelectedResourcesJson(formatResourcesForModal(resources))
          setResourcesModalOpen(true)
        }}
      />
      <ConsentLifecycleSection statusHistory={detail.statusHistory ?? []} />

      <ConsentResourcesModal
        open={resourcesModalOpen}
        resourcesJson={selectedResourcesJson}
        onClose={() => {
          setResourcesModalOpen(false)
        }}
      />

      <ConsentApprovalDialog
        key={`approval-${id}-${String(approvalDialogOpen)}`}
        open={approvalDialogOpen}
        consentId={id}
        purposes={detail.purposes}
        loading={approveMutation.isPending}
        onClose={() => {
          setApprovalDialogOpen(false)
        }}
        onConfirm={(selectedOptionalElements) => {
          approveMutation.mutate(
            { consentID: id, selectedOptionalElements },
            {
              onSuccess: () => {
                setApprovalDialogOpen(false)
              },
            },
          )
        }}
      />

      <ConsentRejectionDialog
        key={`rejection-${id}-${String(rejectionDialogOpen)}`}
        open={rejectionDialogOpen}
        consentId={id}
        loading={rejectMutation.isPending}
        onClose={() => {
          setRejectionDialogOpen(false)
        }}
        onConfirm={() => {
          rejectMutation.mutate(id, {
            onSuccess: () => {
              setRejectionDialogOpen(false)
            },
          })
        }}
      />

      <ConsentRevocationDialog
        key={`revocation-${id}-${String(revocationDialogOpen)}`}
        open={revocationDialogOpen}
        consentId={id}
        loading={revokePending}
        onClose={() => {
          setRevocationDialogOpen(false)
        }}
        onConfirm={() => {
          const options = {
            onSuccess: () => {
              setRevocationDialogOpen(false)
            },
          }
          if (variant === 'admin') {
            adminRevokeMutation.mutate({ consentID: id, actionBy: currentUser.userId }, options)
          } else {
            revokeMutation.mutate(id, options)
          }
        }}
      />
    </Box>
  )
}

ConsentDetailsPage.defaultProps = {
  variant: 'self',
}

export default ConsentDetailsPage
