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
  Card,
  CardContent,
  CardHeader,
  Divider,
  Skeleton,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@wso2/oxygen-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router-dom'
import DetailBackButton from '../../components/layout/main-layout/DetailBackButton'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import { formatEpochTimestamp } from '../../utils/dateTime'
import ComplaintActivityFeed from '../complaints/components/ComplaintActivityFeed'
import ComplaintAttachmentsPanel from '../complaints/components/ComplaintAttachmentsPanel'
import ComplaintPriorityChip from '../complaints/components/ComplaintPriorityChip'
import ComplaintReplyComposer from '../complaints/components/ComplaintReplyComposer'
import ComplaintDeadline from '../complaints/components/ComplaintDeadline'
import ComplaintStatusChip from '../complaints/components/ComplaintStatusChip'
import { COMPLAINT_NEXT_STATUSES } from '../complaints/constants'
import {
  useManagedComplaintDetailQuery,
  useSendManagedComplaintMessageMutation,
} from '../complaints/hooks/useComplaintQueries'
import { collectComplaintAttachments } from '../complaints/utils/complaintAttachments'
import { getComplaintStatusLabelKey } from '../complaints/utils/complaintDisplay'
import ComplaintResolveConfirmDialog from './components/ComplaintResolveConfirmDialog'

const DATE_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
}

interface PendingResolveSend {
  message: string
  files: File[]
  isPublic: boolean
  onSent: () => void
}

function resolveErrorMessage(error: Error | null, t: (key: string) => string): string | undefined {
  if (!error) {
    return undefined
  }
  return t('complaints.management.case.resolveDialog.resolveFailed')
}

function ComplaintCaseDetailLoading(): React.JSX.Element {
  return (
    <Box
      component="main"
      sx={{ p: { xs: 2, md: 4 }, display: 'flex', flexDirection: 'column', gap: 3 }}
    >
      <Stack spacing={1}>
        <DetailBackButton to="/complaint-management" />
        <HeaderBreadcrumbs />
        <Skeleton variant="text" width={220} height={48} />
      </Stack>

      {['summary', 'activity'].map((section) => (
        <Card key={`complaint-case-detail-${section}-skeleton`} sx={{ boxShadow: 1 }}>
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

function ComplaintCaseDetailPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { id } = useParams<{ id: string }>()
  const detailQuery = useManagedComplaintDetailQuery(id)
  const sendMessageMutation = useSendManagedComplaintMessageMutation()
  const [activeTab, setActiveTab] = useState<'activity' | 'attachments'>('activity')
  const [pendingResolve, setPendingResolve] = useState<PendingResolveSend | null>(null)

  if (detailQuery.isPending) {
    return <ComplaintCaseDetailLoading />
  }

  if (detailQuery.isError) {
    return (
      <Box
        component="main"
        sx={{ p: { xs: 2, md: 4 }, display: 'flex', flexDirection: 'column', gap: 2 }}
      >
        <Stack spacing={1}>
          <DetailBackButton to="/complaint-management" />
          <HeaderBreadcrumbs />
        </Stack>
        <Typography variant="h5">{t('complaints.management.case.notFound')}</Typography>
      </Box>
    )
  }

  const complaint = detailQuery.data
  const allowedNextStatuses = COMPLAINT_NEXT_STATUSES[complaint.status]
  const attachmentCount = collectComplaintAttachments(complaint, 'ComplaintOfficer').length

  return (
    <Box
      component="main"
      sx={{ p: { xs: 2, md: 4 }, display: 'flex', flexDirection: 'column', gap: 3 }}
    >
      <Stack spacing={1}>
        <DetailBackButton to="/complaint-management" />
        <HeaderBreadcrumbs currentLabel={complaint.referenceId} />
        <Typography variant="h4" fontWeight={700}>
          {complaint.referenceId}
        </Typography>
        <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap>
          <ComplaintPriorityChip priority={complaint.priority} />
          <ComplaintStatusChip status={complaint.status} viewerRole="ComplaintOfficer" />
        </Stack>
      </Stack>

      <Card sx={{ boxShadow: 1 }}>
        <CardHeader
          title={
            <Typography variant="h6" fontWeight={700}>
              {t(`complaints.categories.${complaint.category}`)}
            </Typography>
          }
          sx={{ pb: 1 }}
        />
        <Divider />
        <CardContent>
          <Stack spacing={2}>
            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(3, 1fr)' },
                gap: 2,
              }}
            >
              <Box>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  fontWeight={600}
                  sx={{ display: 'block', textTransform: 'uppercase' }}
                >
                  {t('complaints.management.case.dataPrincipal')}
                </Typography>
                <Typography variant="body2">{complaint.dataPrincipalName}</Typography>
              </Box>
              <Box>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  fontWeight={600}
                  sx={{ display: 'block', textTransform: 'uppercase' }}
                >
                  {t('complaints.detail.submittedOnLabel')}
                </Typography>
                <Typography variant="body2" sx={{ fontSize: '0.75rem' }}>
                  {formatEpochTimestamp(complaint.submittedAt, DATE_FORMAT_OPTIONS)}
                </Typography>
              </Box>
              <ComplaintDeadline
                submittedAt={complaint.submittedAt}
                statutoryDueDate={complaint.statutoryDueDate}
                status={complaint.status}
                audience="officer"
              />
            </Box>

            <Box>
              <Typography
                variant="caption"
                color="text.secondary"
                fontWeight={600}
                sx={{ display: 'block', textTransform: 'uppercase' }}
              >
                {t('complaints.detail.description')}
              </Typography>
              <Typography variant="body2" sx={{ mt: 0.5 }}>
                {complaint.description}
              </Typography>
            </Box>
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ boxShadow: 1 }}>
        <Tabs
          value={activeTab}
          onChange={(_, value: 'activity' | 'attachments') => setActiveTab(value)}
          sx={{ px: 2 }}
        >
          <Tab value="activity" label={t('complaints.activity.title')} />
          <Tab
            value="attachments"
            label={
              attachmentCount > 0
                ? t('complaints.attachments.tabLabelWithCount', { count: attachmentCount })
                : t('complaints.attachments.tabLabel')
            }
          />
        </Tabs>
        <Divider />
        <CardContent>
          {activeTab === 'activity' ? (
            <Stack spacing={3}>
              {complaint.status === 'RESOLVED' ? (
                <Alert severity="warning">{t('complaints.management.case.resolvedLocked')}</Alert>
              ) : (
                <ComplaintReplyComposer
                  canPostInternalNote
                  statusOptions={allowedNextStatuses}
                  getStatusLabel={(status) =>
                    t(`complaints.status.${getComplaintStatusLabelKey(status)}`)
                  }
                  isSending={sendMessageMutation.isPending}
                  onSend={(message, files, visibility, nextStatus, onSent) => {
                    const isPublic = visibility !== 'internal'

                    if (nextStatus === 'RESOLVED') {
                      setPendingResolve({ message, files, isPublic, onSent })
                      return
                    }

                    sendMessageMutation.mutate(
                      {
                        complaintId: complaint.id,
                        message,
                        isPublic,
                        files,
                        toStatus: nextStatus,
                      },
                      { onSuccess: () => onSent() },
                    )
                  }}
                />
              )}
              <ComplaintActivityFeed
                complaintId={complaint.id}
                entries={complaint.timeline}
                viewerRole="ComplaintOfficer"
              />
            </Stack>
          ) : (
            <ComplaintAttachmentsPanel complaint={complaint} viewerRole="ComplaintOfficer" />
          )}
        </CardContent>
      </Card>

      <ComplaintResolveConfirmDialog
        open={pendingResolve !== null}
        loading={sendMessageMutation.isPending}
        error={resolveErrorMessage(sendMessageMutation.error, t)}
        onClose={() => {
          setPendingResolve(null)
          sendMessageMutation.reset()
        }}
        onConfirm={() => {
          if (!pendingResolve) {
            return
          }

          const { onSent } = pendingResolve

          sendMessageMutation.mutate(
            {
              complaintId: complaint.id,
              message: pendingResolve.message,
              isPublic: pendingResolve.isPublic,
              files: pendingResolve.files,
              toStatus: 'RESOLVED',
            },
            {
              onSuccess: () => {
                setPendingResolve(null)
                onSent()
              },
            },
          )
        }}
      />
    </Box>
  )
}

export default ComplaintCaseDetailPage
