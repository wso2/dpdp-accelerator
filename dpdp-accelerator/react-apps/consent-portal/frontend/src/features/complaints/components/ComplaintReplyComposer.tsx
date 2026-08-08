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
  ButtonGroup,
  Divider,
  ListItemIcon,
  ListItemText,
  ListSubheader,
  Menu,
  MenuItem,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@wso2/oxygen-ui'
import { Check, ChevronDown, Flag, Info, Lock, Paperclip, Send } from '@wso2/oxygen-ui-icons-react'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ComplaintStatus, ComplaintTimelineVisibility } from '../../../types/complaint'
import { MAX_ATTACHMENT_SIZE_BYTES, MAX_ATTACHMENT_SIZE_LABEL } from '../constants'
import { getComplaintStatusAccentColor } from '../utils/complaintDisplay'
import DraftAttachmentCard from './DraftAttachmentCard'

interface ComplaintReplyComposerProps {
  canPostInternalNote: boolean
  statusOptions: ComplaintStatus[]
  getStatusLabel: (status: ComplaintStatus) => string
  onSend: (
    message: string,
    files: File[],
    visibility: ComplaintTimelineVisibility,
    nextStatus?: ComplaintStatus,
  ) => void
}

function ComplaintReplyComposer({
  canPostInternalNote,
  statusOptions,
  getStatusLabel,
  onSend,
}: ComplaintReplyComposerProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [draft, setDraft] = useState<string>('')
  const [draftFiles, setDraftFiles] = useState<File[]>([])
  const [attachmentSizeError, setAttachmentSizeError] = useState<string | null>(null)
  const [composerVisibility, setComposerVisibility] =
    useState<ComplaintTimelineVisibility>('shared')
  const [statusMenuAnchor, setStatusMenuAnchor] = useState<HTMLElement | null>(null)
  const [pendingStatus, setPendingStatus] = useState<ComplaintStatus | ''>('')

  const isInternalDraft = canPostInternalNote && composerVisibility === 'internal'
  const isStatusMenuOpen = Boolean(statusMenuAnchor)

  let sendIcon = <Send size={16} />
  let sendLabel = t('complaints.activity.send')
  let sendColor: 'primary' | 'warning' = 'primary'
  if (pendingStatus) {
    sendIcon = <Flag size={16} />
    sendLabel = t('complaints.activity.sendAndUpdateStatus', {
      status: getStatusLabel(pendingStatus),
    })
    sendColor = 'warning'
  } else if (isInternalDraft) {
    sendIcon = <Lock size={16} />
    sendColor = 'warning'
  }

  return (
    <Stack spacing={1}>
      {canPostInternalNote ? (
        <ToggleButtonGroup
          size="small"
          exclusive
          value={composerVisibility}
          onChange={(_, nextValue: ComplaintTimelineVisibility | null) => {
            if (nextValue) {
              setComposerVisibility(nextValue)
            }
          }}
        >
          <ToggleButton value="shared">{t('complaints.activity.publicReply')}</ToggleButton>
          <ToggleButton value="internal">{t('complaints.activity.internalNote')}</ToggleButton>
        </ToggleButtonGroup>
      ) : null}

      <TextField
        fullWidth
        multiline
        minRows={4}
        maxRows={12}
        placeholder={
          isInternalDraft
            ? t('complaints.activity.composerPlaceholderInternal')
            : t('complaints.activity.composerPlaceholderPublic')
        }
        value={draft}
        onChange={(event) => {
          setDraft(event.target.value)
        }}
      />

      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept=".pdf,.docx,.png,.jpg,.jpeg"
        hidden
        onChange={(event) => {
          const input = event.target
          const files = Array.from(input.files ?? [])
          const acceptedFiles = files.filter((file) => file.size <= MAX_ATTACHMENT_SIZE_BYTES)
          const oversizedFiles = files.filter((file) => file.size > MAX_ATTACHMENT_SIZE_BYTES)

          setAttachmentSizeError(
            oversizedFiles.length > 0
              ? t('complaints.activity.attachmentTooLarge', {
                  fileNames: oversizedFiles.map((file) => file.name).join(', '),
                  maxSize: MAX_ATTACHMENT_SIZE_LABEL,
                })
              : null,
          )
          setDraftFiles((previousFiles) => [...previousFiles, ...acceptedFiles])
          input.value = ''
        }}
      />

      {attachmentSizeError ? (
        <Typography variant="caption" color="error.main">
          {attachmentSizeError}
        </Typography>
      ) : null}

      {draftFiles.length > 0 ? (
        <Stack spacing={0.75} alignItems="flex-start">
          {draftFiles.map((file, index) => (
            <DraftAttachmentCard
              key={`${file.name}-${String(index)}`}
              file={file}
              removeLabel={t('complaints.activity.removeAttachment')}
              onRemove={() => {
                setDraftFiles((previousFiles) =>
                  previousFiles.filter((_, fileIndex) => fileIndex !== index),
                )
              }}
            />
          ))}
        </Stack>
      ) : null}

      <Stack
        direction="row"
        spacing={1.5}
        justifyContent="space-between"
        alignItems="center"
        flexWrap="wrap"
        useFlexGap
      >
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
          <Button
            variant="outlined"
            size="small"
            startIcon={<Paperclip size={16} />}
            onClick={() => fileInputRef.current?.click()}
          >
            {t('complaints.activity.attach')}
          </Button>

          <Stack direction="row" spacing={0.5} alignItems="center" sx={{ color: 'text.secondary' }}>
            <Info size={13} style={{ flexShrink: 0 }} />
            <Typography variant="caption" color="inherit">
              {t('complaints.activity.attachmentsHelp', { maxSize: MAX_ATTACHMENT_SIZE_LABEL })}
            </Typography>
          </Stack>
        </Stack>

        <Stack direction="row" spacing={1.5} alignItems="center">
          <ButtonGroup variant="contained" color={sendColor} size="small">
            <Button
              startIcon={sendIcon}
              disabled={!draft.trim()}
              onClick={() => {
                onSend(
                  draft.trim(),
                  draftFiles,
                  isInternalDraft ? 'internal' : 'shared',
                  pendingStatus || undefined,
                )
                setDraft('')
                setDraftFiles([])
                setAttachmentSizeError(null)
                setPendingStatus('')
              }}
            >
              {sendLabel}
            </Button>
            {statusOptions.length > 0 ? (
              <Button
                size="small"
                sx={{ px: 0.5 }}
                aria-label={t('complaints.activity.sendOptions')}
                onClick={(event) => {
                  setStatusMenuAnchor(event.currentTarget)
                }}
              >
                <ChevronDown size={16} />
              </Button>
            ) : null}
          </ButtonGroup>
        </Stack>
      </Stack>

      <Menu
        open={isStatusMenuOpen}
        anchorEl={statusMenuAnchor}
        onClose={() => setStatusMenuAnchor(null)}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
        transformOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        slotProps={{ paper: { sx: { minWidth: 240 } } }}
      >
        <MenuItem
          selected={!pendingStatus}
          onClick={() => {
            setPendingStatus('')
            setStatusMenuAnchor(null)
          }}
        >
          <ListItemIcon>
            <Send size={15} />
          </ListItemIcon>
          <ListItemText>{t('complaints.activity.sendOnly')}</ListItemText>
          {!pendingStatus ? <Check size={16} style={{ marginLeft: 12, flexShrink: 0 }} /> : null}
        </MenuItem>
        <Divider />
        <ListSubheader sx={{ lineHeight: 2.5 }}>
          {t('complaints.activity.sendAndChangeStatusTo')}
        </ListSubheader>
        {statusOptions.map((status) => {
          const dotColor = getComplaintStatusAccentColor(status, 'ComplaintOfficer')

          return (
            <MenuItem
              key={status}
              selected={pendingStatus === status}
              onClick={() => {
                setPendingStatus(status)
                setStatusMenuAnchor(null)
              }}
            >
              <ListItemIcon>
                <Box
                  sx={{ width: 9, height: 9, borderRadius: '50%', bgcolor: dotColor, mx: '3px' }}
                />
              </ListItemIcon>
              <ListItemText>{getStatusLabel(status)}</ListItemText>
              {pendingStatus === status ? (
                <Check size={16} style={{ marginLeft: 12, flexShrink: 0 }} />
              ) : null}
            </MenuItem>
          )
        })}
      </Menu>
    </Stack>
  )
}

export default ComplaintReplyComposer
