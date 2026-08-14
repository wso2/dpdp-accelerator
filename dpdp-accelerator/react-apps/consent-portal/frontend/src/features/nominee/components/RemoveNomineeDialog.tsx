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
  Typography,
} from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'
import { dialogContentPadding } from '../../../components/common/dialogContentPadding'

interface RemoveNomineeDialogProps {
  open: boolean
  nomineeEmail: string
  loading: boolean
  onClose: () => void
  onConfirm: () => void
}

function RemoveNomineeDialog({
  open,
  nomineeEmail,
  loading,
  onClose,
  onConfirm,
}: RemoveNomineeDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')

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
            {t('nominee.setup.removeDialog.title', 'Remove Nominee')}
          </Typography>
        </Stack>
      </DialogTitle>

      <DialogContent sx={dialogContentPadding(3.5)}>
        <Typography variant="body2" color="text.secondary">
          {t('nominee.setup.removeDialog.message', {
            nomineeEmail,
            defaultValue: '{{nomineeEmail}} will no longer be able to manage your consents.',
          })}
        </Typography>
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
          {t('nominee.setup.removeDialog.cancel', 'Cancel')}
        </Button>
        <Button color="error" variant="contained" disabled={loading} onClick={onConfirm}>
          {loading
            ? t('nominee.setup.removeDialog.processing', 'Removing…')
            : t('nominee.setup.removeDialog.confirm', 'Remove nominee')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default RemoveNomineeDialog
