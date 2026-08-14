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
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { GRANTABLE_NOMINEE_PERMISSIONS, type NomineePermission } from '../../../types/nominee'
import { dialogContentPadding } from '../../../components/common/dialogContentPadding'

interface AddNomineeSubmission {
  nomineeEmail: string
  permissions: NomineePermission[]
}

interface AddNomineeDialogProps {
  open: boolean
  loading: boolean
  errorMessage: string
  mode: 'add' | 'edit'
  initialEmail: string
  initialPermissions: NomineePermission[]
  onClose: () => void
  onConfirm: (submission: AddNomineeSubmission) => void
}

function AddNomineeDialog({
  open,
  loading,
  errorMessage,
  mode,
  initialEmail,
  initialPermissions,
  onClose,
  onConfirm,
}: AddNomineeDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const isEdit = mode === 'edit'
  const [nomineeEmail, setNomineeEmail] = useState<string>(initialEmail)
  const [permissions, setPermissions] = useState<NomineePermission[]>(initialPermissions)

  const togglePermission = (value: NomineePermission): void => {
    setPermissions((current) =>
      current.includes(value) ? current.filter((item) => item !== value) : [...current, value],
    )
  }

  const canSubmit = Boolean(nomineeEmail.trim()) && permissions.length > 0

  // "Send nomination" rather than "Save": the click creates a pending record
  // that the nominee still has to accept and an administrator has to approve.
  let submitLabel = t('nominee.setup.dialog.confirm', 'Send nomination')
  if (loading) {
    submitLabel = isEdit
      ? t('nominee.setup.dialog.processingEdit', 'Saving…')
      : t('nominee.setup.dialog.processing', 'Sending…')
  } else if (isEdit) {
    submitLabel = t('nominee.setup.dialog.saveChanges', 'Save changes')
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
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
        }}
      >
        <Typography variant="h6" fontWeight={700}>
          {isEdit
            ? t('nominee.setup.dialog.editTitle', 'Edit permissions')
            : t('nominee.setup.dialog.title', 'Add nominee')}
        </Typography>
      </DialogTitle>

      <DialogContent sx={dialogContentPadding(3.5)}>
        <Stack spacing={2.5}>
          <TextField
            label={t('nominee.setup.dialog.emailLabel', "Nominee's registered email")}
            type="email"
            fullWidth
            value={nomineeEmail}
            onChange={(event) => {
              setNomineeEmail(event.target.value)
            }}
            disabled={loading}
          />

          <Box>
            <Typography variant="subtitle2" fontWeight={700}>
              {t('nominee.setup.dialog.permissionsTitle', 'What can this nominee do?')}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              {t(
                'nominee.setup.dialog.permissionsSubtitle',
                'Tick only what you want to grant. These become the access this nominee is allowed to use.',
              )}
            </Typography>

            <Stack spacing={0.25}>
              {GRANTABLE_NOMINEE_PERMISSIONS.map((option) => {
                const checked = permissions.includes(option.value)
                return (
                  <FormControlLabel
                    key={option.value}
                    disabled={loading}
                    control={
                      <Checkbox
                        checked={checked}
                        color={option.risky ? 'error' : 'primary'}
                        onChange={() => {
                          togglePermission(option.value)
                        }}
                      />
                    }
                    label={
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Typography variant="body2">
                          {t(option.labelKey, option.defaultLabel)}
                        </Typography>
                        {option.risky ? (
                          <Chip
                            size="small"
                            color="error"
                            variant="outlined"
                            label={t('nominee.setup.dialog.sensitive', 'Sensitive')}
                          />
                        ) : null}
                      </Stack>
                    }
                  />
                )
              })}
            </Stack>
          </Box>

          {errorMessage ? (
            <Typography variant="body2" color="error.main">
              {errorMessage}
            </Typography>
          ) : null}
        </Stack>
      </DialogContent>

      <DialogActions
        sx={{
          p: 3,
          pt: 2,
          borderTop: 1,
          borderColor: 'divider',
          bgcolor: 'background.default',
          gap: 1,
        }}
      >
        <Button variant="text" disabled={loading} onClick={onClose}>
          {t('nominee.setup.dialog.cancel', 'Cancel')}
        </Button>
        <Button
          variant="contained"
          disabled={loading || !canSubmit}
          onClick={() => {
            onConfirm({
              nomineeEmail: nomineeEmail.trim(),
              permissions,
            })
          }}
        >
          {submitLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default AddNomineeDialog
