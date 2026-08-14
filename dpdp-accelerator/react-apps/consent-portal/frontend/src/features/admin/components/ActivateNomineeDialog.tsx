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
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from '@wso2/oxygen-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dialogContentPadding } from '../../../components/common/dialogContentPadding'

interface ActivateNomineeDialogProps {
  open: boolean
  /** The owner's resolved display name, already looked up by the page. */
  ownerName: string
  nomineeEmail: string
  loading: boolean
  errorMessage: string
  onClose: () => void
  onConfirm: (ticket: string) => void
}

function ActivateNomineeDialog({
  open,
  ownerName,
  nomineeEmail,
  loading,
  errorMessage,
  onClose,
  onConfirm,
}: ActivateNomineeDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [ticket, setTicket] = useState<string>('')

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="xs"
      fullWidth
      PaperProps={{
        sx: (theme) => ({
          borderRadius: 1,
          ...theme.applyStyles('light', { bgcolor: theme.palette.grey[50] }),
          ...theme.applyStyles('dark', { bgcolor: 'rgba(255, 255, 255, 0.06)' }),
        }),
      }}
    >
      <DialogTitle sx={{ p: 3, borderBottom: 1, borderColor: 'divider' }}>
        <Stack spacing={0.75}>
          <Typography variant="h6" fontWeight={700}>
            {t('admin.activateDialog.title', 'Activate nominee access')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {/*
              The interpolation names have to match the ones in the resource
              strings exactly. i18next runs with skipOnVariables, so a name that
              is not passed is left in the output as literal "{{ownerName}}".
            */}
            {t('admin.activateDialog.subtitle', {
              ownerName,
              nomineeName: nomineeEmail,
              defaultValue:
                'This grants {{nomineeName}} access to manage consents for {{ownerName}}.',
            })}
          </Typography>
        </Stack>
      </DialogTitle>

      <DialogContent sx={dialogContentPadding(3.5)}>
        <Stack spacing={2}>
          <TextField
            label={t('admin.activateDialog.ticketLabel', 'Legal/support ticket reference')}
            fullWidth
            value={ticket}
            onChange={(event) => {
              setTicket(event.target.value)
            }}
            disabled={loading}
          />
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
          {t('admin.activateDialog.cancel', 'Cancel')}
        </Button>
        <Button
          variant="contained"
          disabled={loading || !ticket.trim()}
          onClick={() => {
            onConfirm(ticket.trim())
          }}
        >
          {loading
            ? t('admin.activateDialog.processing', 'Activating…')
            : t('admin.activateDialog.confirm', 'Activate access')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default ActivateNomineeDialog
