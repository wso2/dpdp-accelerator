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
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormHelperText,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { UploadCloud } from '@wso2/oxygen-ui-icons-react'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ComplaintCategoryAPI } from '../../../types/complaint'
import { MAX_ATTACHMENT_SIZE_BYTES, MAX_ATTACHMENT_SIZE_LABEL } from '../constants'
import {
  useComplaintCategoriesQuery,
  useCreateComplaintMutation,
} from '../hooks/useComplaintQueries'
import DraftAttachmentCard from './DraftAttachmentCard'

interface ComplaintSubmitDialogProps {
  open: boolean
  onClose: () => void
  onSubmitted: (referenceId: string, attachmentUploadFailed: boolean) => void
}

function ComplaintSubmitDialog({
  open,
  onClose,
  onSubmitted,
}: ComplaintSubmitDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [category, setCategory] = useState<ComplaintCategoryAPI | ''>('')
  const [description, setDescription] = useState<string>('')
  const [attachmentFiles, setAttachmentFiles] = useState<File[]>([])
  const [attachmentSizeError, setAttachmentSizeError] = useState<string | null>(null)
  const [showValidation, setShowValidation] = useState<boolean>(false)
  const categoriesQuery = useComplaintCategoriesQuery()
  const categories = categoriesQuery.data ?? []
  const createComplaintMutation = useCreateComplaintMutation()

  const resetForm = (): void => {
    setCategory('')
    setDescription('')
    setAttachmentFiles([])
    setAttachmentSizeError(null)
    setShowValidation(false)
  }

  const handleClose = (): void => {
    resetForm()
    onClose()
  }

  const categoryError = showValidation && !category
  const descriptionError = showValidation && !description.trim()

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="sm"
      fullWidth
      scroll="paper"
      PaperProps={{
        sx: (theme) => ({
          borderRadius: 1,
          ...theme.applyStyles('light', { bgcolor: theme.palette.grey[50] }),
          ...theme.applyStyles('dark', { bgcolor: 'rgba(255, 255, 255, 0.06)' }),
        }),
      }}
    >
      <DialogTitle
        sx={{
          p: 3,
          borderBottom: 1,
          borderColor: 'divider',
          bgcolor: 'background.default',
        }}
      >
        <Stack spacing={1}>
          <Typography variant="h4" fontWeight={700}>
            {t('complaints.submit.title')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('complaints.submit.subtitle')}
          </Typography>
        </Stack>
      </DialogTitle>

      <DialogContent sx={{ px: 3, mt: 3, pb: 3 }}>
        <Stack spacing={3}>
          <Box>
            <Typography variant="body2" sx={{ mb: 0.5 }}>
              {t('complaints.submit.fields.category')}
              <Box component="span" sx={{ color: 'error.main' }}>
                {' '}
                *
              </Box>
            </Typography>
            <FormControl fullWidth error={categoryError} disabled={categoriesQuery.isPending}>
              <Select
                id="complaint-category"
                value={category}
                displayEmpty
                onChange={(event) => {
                  setCategory(event.target.value as ComplaintCategoryAPI)
                }}
                renderValue={(selected) =>
                  selected ? (
                    t(`complaints.categories.${selected}`)
                  ) : (
                    <Typography component="span" color="text.secondary">
                      {t('complaints.submit.fields.categoryPlaceholder')}
                    </Typography>
                  )
                }
              >
                {categories.map((categoryOption) => (
                  <MenuItem key={categoryOption} value={categoryOption}>
                    {t(`complaints.categories.${categoryOption}`)}
                  </MenuItem>
                ))}
              </Select>
              <FormHelperText>
                {categoryError
                  ? t('complaints.submit.validation.categoryRequired')
                  : t('complaints.submit.fields.categoryHelp')}
              </FormHelperText>
            </FormControl>
            {categoriesQuery.isError ? (
              <Typography variant="caption" color="error.main" sx={{ display: 'block', mt: 0.5 }}>
                {t('complaints.submit.fields.categoriesLoadFailed')}
              </Typography>
            ) : null}
          </Box>

          <Box>
            <Typography variant="body2" sx={{ mb: 0.5 }}>
              {t('complaints.submit.fields.description')}
              <Box component="span" sx={{ color: 'error.main' }}>
                {' '}
                *
              </Box>
            </Typography>
            <TextField
              placeholder={t('complaints.submit.fields.descriptionPlaceholder')}
              multiline
              minRows={4}
              fullWidth
              value={description}
              error={descriptionError}
              helperText={
                descriptionError
                  ? t('complaints.submit.validation.descriptionRequired')
                  : t('complaints.submit.fields.descriptionHelp')
              }
              onChange={(event) => {
                setDescription(event.target.value)
              }}
            />
          </Box>

          <Box>
            <Typography variant="body2" sx={{ mb: 0.5 }}>
              {t('complaints.submit.fields.attachments')}
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
              {t('complaints.submit.fields.attachmentsHelp', {
                maxSize: MAX_ATTACHMENT_SIZE_LABEL,
              })}
            </Typography>

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
                    ? t('complaints.submit.validation.attachmentTooLarge', {
                        fileNames: oversizedFiles.map((file) => file.name).join(', '),
                        maxSize: MAX_ATTACHMENT_SIZE_LABEL,
                      })
                    : null,
                )
                setAttachmentFiles((previousFiles) => [...previousFiles, ...acceptedFiles])
                input.value = ''
              }}
            />
            <Button
              variant="outlined"
              size="small"
              startIcon={<UploadCloud size={16} />}
              onClick={() => fileInputRef.current?.click()}
            >
              {t('complaints.submit.fields.uploadButton')}
            </Button>

            {attachmentSizeError ? (
              <Typography variant="caption" color="error.main" sx={{ display: 'block', mt: 1 }}>
                {attachmentSizeError}
              </Typography>
            ) : null}

            {attachmentFiles.length > 0 ? (
              <Stack spacing={0.75} alignItems="flex-start" sx={{ mt: 1.5 }}>
                {attachmentFiles.map((file, index) => (
                  <DraftAttachmentCard
                    key={`${file.name}-${String(index)}`}
                    file={file}
                    removeLabel={t('complaints.activity.removeAttachment')}
                    onRemove={() => {
                      setAttachmentFiles((previousFiles) =>
                        previousFiles.filter((_, fileIndex) => fileIndex !== index),
                      )
                    }}
                  />
                ))}
              </Stack>
            ) : null}

            {createComplaintMutation.isError ? (
              <Alert severity="error" sx={{ mt: 1.5 }}>
                {t('complaints.submit.submitFailed')}
              </Alert>
            ) : null}
          </Box>
        </Stack>
      </DialogContent>

      <DialogActions
        sx={{
          px: 3,
          py: 2.5,
          borderTop: 1,
          borderColor: 'divider',
          bgcolor: 'background.default',
          flexDirection: { xs: 'column-reverse', sm: 'row' },
          gap: 1.25,
        }}
      >
        <Button fullWidth variant="outlined" onClick={handleClose}>
          {t('complaints.submit.actions.cancel')}
        </Button>
        <Button
          autoFocus
          fullWidth
          variant="contained"
          disabled={createComplaintMutation.isPending}
          onClick={() => {
            if (!category || !description.trim()) {
              setShowValidation(true)
              return
            }

            createComplaintMutation.mutate(
              {
                category,
                description: description.trim(),
                files: attachmentFiles,
              },
              {
                onSuccess: (created) => {
                  resetForm()
                  onSubmitted(created.referenceId, created.attachmentUploadFailed)
                },
              },
            )
          }}
        >
          {t('complaints.submit.actions.submit')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default ComplaintSubmitDialog
