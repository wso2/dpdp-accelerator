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
import ComplaintActivityFeed from './components/ComplaintActivityFeed'
import ComplaintAttachmentsPanel from './components/ComplaintAttachmentsPanel'
import ComplaintDeadline from './components/ComplaintDeadline'
import ComplaintReplyComposer from './components/ComplaintReplyComposer'
import ComplaintStatusChip from './components/ComplaintStatusChip'
import {
  useMyComplaintDetailQuery,
  useSendMyComplaintMessageMutation,
} from './hooks/useComplaintQueries'
import { collectComplaintAttachments } from './utils/complaintAttachments'

const DATE_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
}

function ComplaintDetailLoading(): React.JSX.Element {
  return (
    <Box
      component="main"
      sx={{ p: { xs: 2, md: 4 }, display: 'flex', flexDirection: 'column', gap: 3 }}
    >
      <Stack spacing={1}>
        <DetailBackButton to="/complaints" />
        <HeaderBreadcrumbs />
        <Skeleton variant="text" width={220} height={48} />
      </Stack>

      {['summary', 'activity'].map((section) => (
        <Card key={`complaint-detail-${section}-skeleton`} sx={{ boxShadow: 1 }}>
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

function ComplaintDetailPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { id } = useParams<{ id: string }>()
  const detailQuery = useMyComplaintDetailQuery(id)
  const sendMessageMutation = useSendMyComplaintMessageMutation()
  const [activeTab, setActiveTab] = useState<'activity' | 'attachments'>('activity')

  if (detailQuery.isPending) {
    return <ComplaintDetailLoading />
  }

  if (detailQuery.isError) {
    return (
      <Box
        component="main"
        sx={{ p: { xs: 2, md: 4 }, display: 'flex', flexDirection: 'column', gap: 2 }}
      >
        <Stack spacing={1}>
          <DetailBackButton to="/complaints" />
          <HeaderBreadcrumbs />
        </Stack>
        <Typography variant="h5">{t('complaints.detail.notFound')}</Typography>
      </Box>
    )
  }

  const complaint = detailQuery.data

  const attachmentCount = collectComplaintAttachments(complaint, 'DataPrincipal').length

  return (
    <Box
      component="main"
      sx={{ p: { xs: 2, md: 4 }, display: 'flex', flexDirection: 'column', gap: 3 }}
    >
      <Stack spacing={1}>
        <DetailBackButton to="/complaints" />
        <HeaderBreadcrumbs currentLabel={complaint.referenceId} />
        <Typography variant="h4" fontWeight={700}>
          {complaint.referenceId}
        </Typography>
        <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap>
          <ComplaintStatusChip status={complaint.status} viewerRole="DataPrincipal" />
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

            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
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
                audience="dataPrincipal"
              />
            </Box>
          </Stack>
        </CardContent>
      </Card>

      {complaint.status === 'WAITING_ON_CLIENT' ? (
        <Alert severity="warning">{t('complaints.detail.awaitingInfo.note')}</Alert>
      ) : null}

      {complaint.status === 'RESOLVED' ? (
        <Alert severity="success">{t('complaints.detail.resolved.note')}</Alert>
      ) : null}

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
              <ComplaintReplyComposer
                canPostInternalNote={false}
                statusOptions={[]}
                getStatusLabel={() => ''}
                isSending={sendMessageMutation.isPending}
                onSend={(message, files, _visibility, _nextStatus, onSent) => {
                  // A reply advances the workflow in the two cases the ball is in the client's
                  // court: the officer asked for more information (WAITING_ON_CLIENT), or the
                  // complaint was closed and the client is disputing it - a reply is the only
                  // way a RESOLVED complaint reopens, since the officer has no manual
                  // transition out of it. Any other status (OPEN, IN_PROGRESS) is left
                  // unchanged, since the officer hasn't asked for anything there.
                  const toStatus =
                    complaint.status === 'WAITING_ON_CLIENT' || complaint.status === 'RESOLVED'
                      ? 'AWAITING_INTERNAL_REVIEW'
                      : undefined

                  sendMessageMutation.mutate(
                    {
                      complaintId: complaint.id,
                      message,
                      files,
                      toStatus,
                    },
                    { onSuccess: () => onSent() },
                  )
                }}
              />
              <ComplaintActivityFeed
                complaintId={complaint.id}
                entries={complaint.timeline}
                viewerRole="DataPrincipal"
              />
            </Stack>
          ) : (
            <ComplaintAttachmentsPanel complaint={complaint} viewerRole="DataPrincipal" />
          )}
        </CardContent>
      </Card>
    </Box>
  )
}

export default ComplaintDetailPage
