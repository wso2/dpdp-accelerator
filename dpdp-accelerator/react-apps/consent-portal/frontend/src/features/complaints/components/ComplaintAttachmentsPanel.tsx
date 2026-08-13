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

import { Box, Chip, Stack, Typography } from '@wso2/oxygen-ui'
import { Lock, Shield, User } from '@wso2/oxygen-ui-icons-react'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import type { ComplaintActorRole, ComplaintDetail } from '../../../types/complaint'
import { formatIsoDateTime } from '../../../utils/dateTime'
import { useAttachmentImagePreview } from '../hooks/useAttachmentImagePreview'
import type { AggregatedComplaintAttachment } from '../utils/complaintAttachments'
import { collectComplaintAttachments } from '../utils/complaintAttachments'
import { downloadComplaintAttachment } from '../utils/downloadAttachment'
import AttachmentPreviewCard from './AttachmentPreviewCard'

interface ComplaintAttachmentsPanelProps {
  complaint: ComplaintDetail
  viewerRole: Extract<ComplaintActorRole, 'DataPrincipal' | 'ComplaintOfficer'>
}

type ViewerRole = Extract<ComplaintActorRole, 'DataPrincipal' | 'ComplaintOfficer'>

const ATTACHMENT_DATE_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  month: 'short',
  day: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
}

interface AttachmentRowProps {
  complaintId: string
  item: AggregatedComplaintAttachment
  viewerRole: ViewerRole
  t: TFunction<'common'>
}

function AttachmentRow({
  complaintId,
  item,
  viewerRole,
  t,
}: AttachmentRowProps): React.JSX.Element {
  const imageUrl = useAttachmentImagePreview(
    complaintId,
    item.attachment.id,
    item.attachment.fileName,
    viewerRole,
  )
  const accentColor = item.actorRole === 'DataPrincipal' ? 'primary.main' : 'info.main'

  const handleDownload = (): void => {
    downloadComplaintAttachment(
      complaintId,
      item.attachment.id,
      item.attachment.fileName,
      viewerRole,
    ).catch(() => undefined)
  }

  return (
    <Box
      role="button"
      tabIndex={0}
      onClick={handleDownload}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          handleDownload()
        }
      }}
      sx={{ cursor: 'pointer' }}
    >
      <AttachmentPreviewCard
        fileName={item.attachment.fileName}
        fileSizeLabel={item.attachment.fileSizeLabel}
        fullWidth
        imageUrl={imageUrl}
        thumbnailSize={56}
      >
        <Stack direction="row" spacing={0.75} alignItems="center" sx={{ flexShrink: 0 }}>
          {item.isInternal ? (
            <Chip
              size="small"
              variant="outlined"
              icon={<Lock size={11} />}
              label={t('complaints.attachments.internalNote')}
              sx={{
                height: 18,
                borderColor: accentColor,
                color: accentColor,
                '& .MuiChip-label': { px: 0.75, fontSize: '0.6875rem' },
              }}
            />
          ) : (
            <Box sx={{ display: 'flex', color: accentColor }}>
              {item.actorRole === 'ComplaintOfficer' ? <Shield size={14} /> : <User size={14} />}
            </Box>
          )}
          <Stack alignItems="flex-end">
            <Typography variant="caption" fontWeight={600}>
              {item.isInitialSubmission
                ? t('complaints.attachments.initialSubmission')
                : item.actorName}
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
              {formatIsoDateTime(item.timestamp, ATTACHMENT_DATE_FORMAT_OPTIONS)}
            </Typography>
          </Stack>
        </Stack>
      </AttachmentPreviewCard>
    </Box>
  )
}

function ComplaintAttachmentsPanel({
  complaint,
  viewerRole,
}: ComplaintAttachmentsPanelProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const attachments = collectComplaintAttachments(complaint, viewerRole)

  if (attachments.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t('complaints.attachments.empty')}
      </Typography>
    )
  }

  return (
    <Stack spacing={1}>
      {attachments.map((item) => (
        <AttachmentRow
          key={item.key}
          complaintId={complaint.id}
          item={item}
          viewerRole={viewerRole}
          t={t}
        />
      ))}
    </Stack>
  )
}

export default ComplaintAttachmentsPanel
