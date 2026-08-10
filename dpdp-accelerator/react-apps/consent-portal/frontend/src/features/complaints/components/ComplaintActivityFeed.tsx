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

import { Box, Chip, Divider, Stack, Typography } from '@wso2/oxygen-ui'
import { Clock, FileText, Image, Lock, Paperclip } from '@wso2/oxygen-ui-icons-react'
import type { TFunction } from 'i18next'
import { Fragment } from 'react'
import { useTranslation } from 'react-i18next'
import type {
  ComplaintActorRole,
  ComplaintAttachment,
  ComplaintTimelineEntry,
} from '../../../types/complaint'
import { formatIsoDateTime } from '../../../utils/dateTime'
import { useAttachmentImagePreview } from '../hooks/useAttachmentImagePreview'
import { downloadComplaintAttachment } from '../utils/downloadAttachment'
import { getAttachmentFileKind } from '../utils/complaintAttachments'
import { getComplaintStatusLabelKey } from '../utils/complaintDisplay'

type ViewerRole = Extract<ComplaintActorRole, 'DataPrincipal' | 'ComplaintOfficer'>

interface ComplaintActivityFeedProps {
  complaintId: string
  entries: ComplaintTimelineEntry[]
  viewerRole: ViewerRole
}

interface AttachmentTileProps {
  complaintId: string
  attachment: ComplaintAttachment
  viewerRole: ViewerRole
}

const MAX_VISIBLE_COMMENT_ATTACHMENTS = 6
const ATTACHMENT_TILE_WIDTH = 132
const ATTACHMENT_TILE_PREVIEW_HEIGHT = 92

const FILE_KIND_VISUALS = {
  image: { Icon: Image, color: 'success.main' },
  pdf: { Icon: FileText, color: 'error.main' },
  doc: { Icon: FileText, color: 'info.main' },
  other: { Icon: Paperclip, color: 'text.secondary' },
} as const

const ATTACHMENT_PREVIEW_TINTS: Record<'image' | 'pdf' | 'doc', { light: string; dark: string }> = {
  image: { light: 'rgba(46, 125, 50, 0.08)', dark: 'rgba(102, 187, 106, 0.12)' },
  pdf: { light: 'rgba(211, 47, 47, 0.08)', dark: 'rgba(239, 83, 80, 0.12)' },
  doc: { light: 'rgba(2, 136, 209, 0.08)', dark: 'rgba(41, 182, 246, 0.12)' },
}

function AttachmentTile({
  complaintId,
  attachment,
  viewerRole,
}: AttachmentTileProps): React.JSX.Element {
  const imageUrl = useAttachmentImagePreview(
    complaintId,
    attachment.id,
    attachment.fileName,
    viewerRole,
  )
  const kind = getAttachmentFileKind(attachment.fileName)
  const { Icon, color } = FILE_KIND_VISUALS[kind]
  const tint = kind === 'other' ? null : ATTACHMENT_PREVIEW_TINTS[kind]

  const handleDownload = (): void => {
    downloadComplaintAttachment(complaintId, attachment.id, attachment.fileName, viewerRole).catch(
      () => undefined,
    )
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
      sx={{
        width: ATTACHMENT_TILE_WIDTH,
        borderRadius: 1,
        overflow: 'hidden',
        cursor: 'pointer',
        bgcolor: 'background.paper',
        boxShadow: 1,
        transition: 'box-shadow 0.15s ease',
        '&:hover': { boxShadow: 4 },
      }}
    >
      <Box
        sx={(theme) => ({
          position: 'relative',
          height: ATTACHMENT_TILE_PREVIEW_HEIGHT,
          bgcolor: 'action.hover',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          overflow: 'hidden',
          ...(tint
            ? {
                ...theme.applyStyles('light', { bgcolor: tint.light }),
                ...theme.applyStyles('dark', { bgcolor: tint.dark }),
              }
            : {}),
        })}
      >
        {imageUrl ? (
          <Box
            component="img"
            src={imageUrl}
            alt={attachment.fileName}
            sx={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
        ) : (
          <Box sx={{ display: 'flex', color }}>
            <Icon size={32} />
          </Box>
        )}
        <Box
          sx={{
            position: 'absolute',
            bottom: -7,
            right: -7,
            width: 20,
            height: 20,
            bgcolor: color,
            transform: 'rotate(45deg)',
            boxShadow: '-1px -1px 3px rgba(0, 0, 0, 0.25)',
          }}
        />
      </Box>
      <Stack
        direction="row"
        spacing={0.5}
        alignItems="center"
        sx={{ px: 1, py: 0.75, borderTop: 1, borderColor: 'divider' }}
      >
        <Box sx={{ display: 'flex', color, flexShrink: 0 }}>
          <Icon size={13} />
        </Box>
        <Typography variant="caption" noWrap sx={{ fontSize: 11 }}>
          {attachment.fileName}
        </Typography>
      </Stack>
    </Box>
  )
}

const ACTIVITY_DATE_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
}

const TIMELINE_MARKER_COLUMN_WIDTH = 24
const TIMELINE_LOG_DOT_SIZE = 8
const TIMELINE_COMMENT_DOT_SIZE = 10

function isStatusChangeEntry(entry: ComplaintTimelineEntry): boolean {
  return entry.type === 'statusChange' || entry.type === 'resolution'
}

function isPlainLogEntry(entry: ComplaintTimelineEntry): boolean {
  if (entry.type === 'systemAcknowledgement') {
    return true
  }

  return isStatusChangeEntry(entry) && entry.message.trim().length === 0
}

function renderLogHeadline(entry: ComplaintTimelineEntry, t: TFunction<'common'>): React.ReactNode {
  if (entry.type === 'systemAcknowledgement') {
    return t('complaints.timeline.complaintReceived')
  }

  if (entry.fromStatus && entry.toStatus) {
    return (
      <>
        {t('complaints.timeline.statusChangePrefix')}{' '}
        <Box component="span" sx={{ color: 'text.primary', fontWeight: 700 }}>
          {t(`complaints.status.${getComplaintStatusLabelKey(entry.fromStatus)}`)}
        </Box>{' '}
        {t('complaints.timeline.statusChangeJoiner')}{' '}
        <Box component="span" sx={{ color: 'text.primary', fontWeight: 700 }}>
          {t(`complaints.status.${getComplaintStatusLabelKey(entry.toStatus)}`)}
        </Box>
      </>
    )
  }

  return entry.message
}

function ComplaintActivityFeed({
  complaintId,
  entries,
  viewerRole,
}: ComplaintActivityFeedProps): React.JSX.Element {
  const { t } = useTranslation('common')

  const visibleEntries = (
    viewerRole === 'DataPrincipal'
      ? entries.filter((entry) => entry.visibility === 'shared')
      : entries
  )
    .slice()
    .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())

  function renderTimelineMarker(dotColor: string | null, dotSize: number, dotTop: number) {
    return (
      <Box sx={{ width: TIMELINE_MARKER_COLUMN_WIDTH, flexShrink: 0, position: 'relative' }}>
        <Box
          sx={{
            position: 'absolute',
            top: 0,
            bottom: 0,
            left: '50%',
            width: '2px',
            bgcolor: 'divider',
            transform: 'translateX(-50%)',
          }}
        />
        <Box
          sx={{
            position: 'absolute',
            top: dotTop,
            left: '50%',
            transform: 'translateX(-50%)',
            width: dotSize,
            height: dotSize,
            borderRadius: '50%',
            zIndex: 1,
            ...(dotColor
              ? { bgcolor: dotColor }
              : { bgcolor: 'background.paper', border: '1.5px solid', borderColor: 'divider' }),
          }}
        />
      </Box>
    )
  }

  function renderTimelineLogRow(entry: ComplaintTimelineEntry) {
    return (
      <Stack key={entry.id} direction="row" alignItems="stretch">
        {renderTimelineMarker(null, TIMELINE_LOG_DOT_SIZE, 11)}
        <Stack spacing={0.5} sx={{ py: 1, minWidth: 0 }}>
          <Stack direction="row" spacing={0.75} alignItems="center">
            <Box sx={{ display: 'flex', color: 'text.secondary', flexShrink: 0 }}>
              <Clock size={13} />
            </Box>
            <Typography variant="body2" color="text.secondary">
              {renderLogHeadline(entry, t)} &middot;{' '}
              {formatIsoDateTime(entry.timestamp, ACTIVITY_DATE_FORMAT_OPTIONS)}
            </Typography>
          </Stack>
        </Stack>
      </Stack>
    )
  }

  function renderTimelineCommentRow(entry: ComplaintTimelineEntry) {
    const isInternal = entry.visibility === 'internal'
    const isFromDataPrincipal = entry.actorRole === 'DataPrincipal'
    const isOfficerActor = entry.actorRole === 'ComplaintOfficer'
    const accentColor = isFromDataPrincipal ? 'primary.main' : 'info.main'

    return (
      <Stack key={entry.id} direction="row" alignItems="stretch">
        {renderTimelineMarker(accentColor, TIMELINE_COMMENT_DOT_SIZE, 13)}
        <Box sx={{ flex: 1, minWidth: 0, pt: 0.5, pb: 1.5 }}>
          <Box
            sx={(theme) => ({
              px: 2,
              py: 1.5,
              borderRadius: 1.5,
              borderLeft: 3,
              borderLeftStyle: isInternal ? 'dashed' : 'solid',
              borderLeftColor: accentColor,
              bgcolor: 'action.hover',
              ...(isFromDataPrincipal
                ? {
                    ...theme.applyStyles('light', { bgcolor: 'rgba(237, 108, 2, 0.08)' }),
                    ...theme.applyStyles('dark', { bgcolor: 'rgba(255, 167, 38, 0.12)' }),
                  }
                : {
                    ...theme.applyStyles('light', { bgcolor: 'rgba(2, 136, 209, 0.08)' }),
                    ...theme.applyStyles('dark', { bgcolor: 'rgba(41, 182, 246, 0.12)' }),
                  }),
            })}
          >
            <Stack
              direction="row"
              justifyContent="space-between"
              alignItems="flex-start"
              flexWrap="wrap"
              useFlexGap
              spacing={1}
            >
              <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap" useFlexGap>
                <Typography variant="body2" fontWeight={700}>
                  {entry.actorName}
                </Typography>
                <Chip
                  size="small"
                  variant="outlined"
                  label={
                    isOfficerActor
                      ? t('complaints.activity.officerRoleLabel')
                      : t('complaints.activity.dataPrincipalRoleLabel')
                  }
                  sx={{
                    height: 18,
                    borderColor: accentColor,
                    color: accentColor,
                    '& .MuiChip-label': { px: 0.75, fontSize: 11 },
                  }}
                />
                {isInternal ? (
                  <Chip
                    size="small"
                    variant="outlined"
                    icon={<Lock size={11} />}
                    label={t('complaints.activity.internalTag')}
                    sx={{
                      height: 18,
                      borderColor: 'info.main',
                      color: 'info.main',
                      '& .MuiChip-icon': { color: 'info.main' },
                      '& .MuiChip-label': { px: 0.75, fontSize: 11 },
                    }}
                  />
                ) : null}
              </Stack>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ whiteSpace: 'nowrap', flexShrink: 0 }}
              >
                {formatIsoDateTime(entry.timestamp, ACTIVITY_DATE_FORMAT_OPTIONS)}
              </Typography>
            </Stack>

            <Typography variant="body2" color="text.primary" sx={{ mt: 1 }}>
              {entry.message}
            </Typography>

            {entry.attachments && entry.attachments.length > 0 ? (
              <Stack spacing={1} sx={{ mt: 1 }}>
                <Divider />
                <Stack direction="row" spacing={1.25} flexWrap="wrap" useFlexGap>
                  {entry.attachments.slice(0, MAX_VISIBLE_COMMENT_ATTACHMENTS).map((attachment) => (
                    <AttachmentTile
                      key={attachment.id}
                      complaintId={complaintId}
                      attachment={attachment}
                      viewerRole={viewerRole}
                    />
                  ))}
                </Stack>
                {entry.attachments.length > MAX_VISIBLE_COMMENT_ATTACHMENTS ? (
                  <Typography variant="caption" color="text.secondary" sx={{ pl: 0.5 }}>
                    {t('complaints.attachments.overflowMore', {
                      count: entry.attachments.length - MAX_VISIBLE_COMMENT_ATTACHMENTS,
                    })}
                  </Typography>
                ) : null}
              </Stack>
            ) : null}
          </Box>
        </Box>
      </Stack>
    )
  }

  function renderTimelineRow(entry: ComplaintTimelineEntry) {
    if (isPlainLogEntry(entry)) {
      return renderTimelineLogRow(entry)
    }

    if (isStatusChangeEntry(entry)) {
      return (
        <Fragment key={entry.id}>
          {renderTimelineLogRow(entry)}
          {renderTimelineCommentRow(entry)}
        </Fragment>
      )
    }

    return renderTimelineCommentRow(entry)
  }

  return <Stack spacing={0}>{visibleEntries.map((entry) => renderTimelineRow(entry))}</Stack>
}

export default ComplaintActivityFeed
