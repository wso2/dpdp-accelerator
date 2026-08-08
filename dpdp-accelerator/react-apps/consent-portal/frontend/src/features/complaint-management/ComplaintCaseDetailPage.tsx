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
  CircularProgress,
  Divider,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@wso2/oxygen-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import HeaderBreadcrumbs from '../../components/layout/main-layout/HeaderBreadcrumbs'
import { formatIsoDateTime } from '../../utils/dateTime'
import ComplaintActivityFeed from '../complaints/components/ComplaintActivityFeed'
import ComplaintAttachmentsPanel from '../complaints/components/ComplaintAttachmentsPanel'
import ComplaintPriorityChip from '../complaints/components/ComplaintPriorityChip'
import ComplaintReplyComposer from '../complaints/components/ComplaintReplyComposer'
import ComplaintSlaIndicator from '../complaints/components/ComplaintSlaIndicator'
import ComplaintStatusChip from '../complaints/components/ComplaintStatusChip'
import { COMPLAINT_NEXT_STATUSES } from '../complaints/constants'
import {
  useManagedComplaintDetailQuery,
  useSendManagedComplaintMessageMutation,
} from '../complaints/hooks/useComplaintQueries'
import { collectComplaintAttachments } from '../complaints/utils/complaintAttachments'
import { getComplaintStatusLabelKey } from '../complaints/utils/complaintDisplay'

const DATE_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
}

function ComplaintCaseDetailPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const detailQuery = useManagedComplaintDetailQuery(id)
  const sendMessageMutation = useSendManagedComplaintMessageMutation()
  const [activeTab, setActiveTab] = useState<'activity' | 'attachments'>('activity')

  if (detailQuery.isPending) {
    return (
      <Box component="main" sx={{ p: { xs: 2, md: 4 }, display: 'flex', justifyContent: 'center' }}>
        <CircularProgress size={28} />
      </Box>
    )
  }

  if (detailQuery.isError) {
    return (
      <Box
        component="main"
        sx={{ p: { xs: 2, md: 4 }, display: 'flex', flexDirection: 'column', gap: 2 }}
      >
        <Typography variant="h5">{t('complaints.management.case.notFound')}</Typography>
        <Box>
          <Button variant="outlined" onClick={() => navigate('/complaint-management')}>
            {t('complaints.management.case.back')}
          </Button>
        </Box>
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
        <HeaderBreadcrumbs currentLabel={complaint.referenceId} />
        <Typography variant="h4" fontWeight={700}>
          {complaint.referenceId}
        </Typography>
        <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap>
          <ComplaintPriorityChip priority={complaint.priority} />
          <ComplaintStatusChip status={complaint.status} viewerRole="ComplaintOfficer" />
          <ComplaintSlaIndicator
            statutoryDueDate={complaint.statutoryDueDate}
            status={complaint.status}
          />
        </Stack>
      </Stack>

      <Card sx={{ boxShadow: 1 }}>
        <CardHeader title={t(`complaints.categories.${complaint.category}`)} sx={{ pb: 1 }} />
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
                  {t('complaints.detail.submittedOn', {
                    date: formatIsoDateTime(complaint.submittedAt, DATE_FORMAT_OPTIONS),
                  })}
                </Typography>
              </Box>
              <Box>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  fontWeight={600}
                  sx={{ display: 'block', textTransform: 'uppercase' }}
                >
                  {t('complaints.sla.dueDate', {
                    date: formatIsoDateTime(complaint.statutoryDueDate, DATE_FORMAT_OPTIONS),
                  })}
                </Typography>
              </Box>
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
              <ComplaintReplyComposer
                canPostInternalNote
                statusOptions={allowedNextStatuses}
                getStatusLabel={(status) =>
                  t(`complaints.status.${getComplaintStatusLabelKey(status)}`)
                }
                onSend={(message, files, visibility, nextStatus) => {
                  sendMessageMutation.mutate({
                    complaintId: complaint.id,
                    message,
                    isPublic: visibility !== 'internal',
                    files,
                    toStatus: nextStatus,
                  })
                }}
              />
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

      <Box>
        <Button variant="outlined" onClick={() => navigate('/complaint-management')}>
          {t('complaints.management.case.back')}
        </Button>
      </Box>
    </Box>
  )
}

export default ComplaintCaseDetailPage
