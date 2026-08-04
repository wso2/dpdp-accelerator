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
import ConsentMetadataCard from './components/details/ConsentMetadataCard'
import ConsentPurposesSection from './components/details/ConsentPurposesSection'
import {
  useApproveConsentMutation,
  useConsentDetailQuery,
  useRejectConsentMutation,
  useRevokeConsentMutation,
} from './hooks/useConsentQueries'
import {
  isConsentApprovableState,
  isConsentRejectableState,
  isConsentRevokableState,
} from './utils/statusChip'
import { PORTAL_SCOPES } from '../../utils/portalScopes'
import {
  useAdminConsentDetailQuery,
  useAdminRevokeConsentMutation,
} from '../admin-consents/hooks/useAdminConsentQueries'

interface ConsentDetailsPageProps {
  variant?: 'self' | 'admin'
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

      {['metadata', 'purposes', 'authorizations'].map((section) => (
        <Card key={`details-${section}-skeleton`} sx={{ boxShadow: 1 }}>
          <CardHeader title={<Skeleton variant="text" width={220} />} sx={{ pb: 1 }} />
          <Divider />
          <CardContent sx={{ pt: 3 }}>
            <Stack spacing={1}>
              <Skeleton variant="text" width="60%" />
              <Skeleton variant="text" width="80%" />
              <Skeleton variant="text" width="45%" />
            </Stack>
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
  const { hasScope } = useAuthorization()
  const canWriteSelf = hasScope(PORTAL_SCOPES.CONSENTS_WRITE_SELF)
  const canWriteAny = hasScope(PORTAL_SCOPES.CONSENTS_WRITE_ANY)
  const consentDetailQuery = variant === 'admin' ? adminConsentDetailQuery : selfConsentDetailQuery
  const backPath = variant === 'admin' ? '/administration/consents' : '/consents'
  const revokePending =
    variant === 'admin' ? adminRevokeMutation.isPending : revokeMutation.isPending
  const revokeError =
    variant === 'admin' ? adminRevokeMutation.error?.message : revokeMutation.error?.message

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
    variant === 'self' && detail ? canWriteSelf && isConsentApprovableState(detail.state) : false
  const canReject =
    variant === 'self' && detail ? canWriteSelf && isConsentRejectableState(detail.state) : false
  const canRevoke = detail
    ? (variant === 'admin' ? canWriteAny : canWriteSelf) && isConsentRevokableState(detail.state)
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
            {t('consentRegistry.details.title')}
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
              onClick={() => setApprovalDialogOpen(true)}
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
              onClick={() => setRejectionDialogOpen(true)}
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
              onClick={() => setRevocationDialogOpen(true)}
            >
              {t('consentRegistry.actions.revoke')}
            </Button>
          ) : null}
        </Stack>
      </Stack>

      <ConsentMetadataCard consentId={id} detail={detail} />
      <ConsentPurposesSection purposes={detail.purposes} />
      <ConsentAuthorizationsSection authorizations={detail.authorizations ?? []} />

      <ConsentApprovalDialog
        open={approvalDialogOpen}
        consentId={id}
        loading={approveMutation.isPending}
        error={approveMutation.error?.message}
        onClose={() => {
          setApprovalDialogOpen(false)
          approveMutation.reset()
        }}
        onConfirm={() => {
          approveMutation.mutate(id, { onSuccess: () => setApprovalDialogOpen(false) })
        }}
      />

      <ConsentRejectionDialog
        open={rejectionDialogOpen}
        consentId={id}
        loading={rejectMutation.isPending}
        error={rejectMutation.error?.message}
        onClose={() => {
          setRejectionDialogOpen(false)
          rejectMutation.reset()
        }}
        onConfirm={() => {
          rejectMutation.mutate(id, { onSuccess: () => setRejectionDialogOpen(false) })
        }}
      />

      <ConsentRevocationDialog
        open={revocationDialogOpen}
        consentId={id}
        loading={revokePending}
        error={revokeError}
        onClose={() => {
          setRevocationDialogOpen(false)
          adminRevokeMutation.reset()
          revokeMutation.reset()
        }}
        onConfirm={() => {
          const options = { onSuccess: () => setRevocationDialogOpen(false) }

          if (variant === 'admin') {
            adminRevokeMutation.mutate(id, options)
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
