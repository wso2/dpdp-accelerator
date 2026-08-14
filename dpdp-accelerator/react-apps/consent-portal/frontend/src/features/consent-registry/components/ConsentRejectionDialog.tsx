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
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Typography,
} from '@wso2/oxygen-ui'
import { Ban } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import { dialogContentPadding } from '../../../components/common/dialogContentPadding'

interface ConsentRejectionDialogProps {
  open: boolean
  consentId: string
  loading: boolean
  onClose: () => void
  onConfirm: () => void
}

function ConsentRejectionDialog({
  open,
  consentId,
  loading,
  onClose,
  onConfirm,
}: ConsentRejectionDialogProps): React.JSX.Element {
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
      <DialogTitle
        sx={{
          p: 3,
          borderBottom: 1,
          borderColor: 'divider',
          textAlign: 'center',
        }}
      >
        <Stack spacing={0.75}>
          <Typography variant="h6" fontWeight={700}>
            {t('consentRegistry.modals.rejection.title', 'Confirm Rejection')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t(
              'consentRegistry.modals.rejection.message',
              'Are you sure you want to reject this consent?',
            )}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 300 }}>
            {t('consentRegistry.modals.consentId', 'Consent ID')}: {consentId}
          </Typography>
        </Stack>
      </DialogTitle>

      <DialogContent sx={dialogContentPadding(3.5)}>
        <Stack spacing={2} sx={{ mt: 3 }}>
          <Box
            sx={{
              width: '100%',
              p: 2,
              border: 1,
              borderColor: 'error.light',
              borderRadius: 1,
              bgcolor: 'error.lighter',
              display: 'flex',
              alignItems: 'center',
              gap: 1.5,
            }}
          >
            <Box
              sx={{
                width: 36,
                height: 36,
                borderRadius: '50%',
                display: 'grid',
                placeItems: 'center',
                color: 'error.main',
                bgcolor: 'background.paper',
                flexShrink: 0,
              }}
            >
              <Ban size={20} />
            </Box>
            <Typography variant="body2" color="text.secondary">
              {t(
                'consentRegistry.modals.rejection.note',
                'Rejecting this consent means the requested permissions will not be granted.',
              )}
            </Typography>
          </Box>
        </Stack>
      </DialogContent>

      <DialogActions
        sx={{
          p: 3,
          pt: 2,
          borderTop: 1,
          borderColor: 'divider',
          bgcolor: 'background.default',
          flexDirection: 'column',
          gap: 1.25,
        }}
      >
        <Button
          fullWidth
          color="error"
          variant="contained"
          startIcon={<Ban size={16} />}
          disabled={loading}
          onClick={onConfirm}
        >
          {loading
            ? t('consentRegistry.modals.actions.processing', 'Processing...')
            : t('consentRegistry.modals.rejection.confirm', 'Reject Consent')}
        </Button>
        <Button fullWidth variant="outlined" disabled={loading} onClick={onClose}>
          {t('consentRegistry.modals.rejection.cancel', 'Cancel')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default ConsentRejectionDialog
