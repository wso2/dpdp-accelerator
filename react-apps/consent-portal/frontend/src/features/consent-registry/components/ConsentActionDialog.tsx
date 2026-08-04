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
  Stack,
  Typography,
} from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'

export interface ConsentActionDialogProps {
  open: boolean
  consentId: string
  title: string
  message: string
  note: string
  confirmLabel: string
  color: 'primary' | 'error'
  icon: React.ReactNode
  loading: boolean
  error?: string
  onClose: () => void
  onConfirm: () => void
}

/**
 * Whole-consent confirmation dialog shared by approve, reject and revoke.
 *
 * WSO2 Identity Server 7.3 has no per element selection, so every lifecycle
 * action is a single confirmation.
 */
function ConsentActionDialog({
  open,
  consentId,
  title,
  message,
  note,
  confirmLabel,
  color,
  icon,
  loading,
  error,
  onClose,
  onConfirm,
}: ConsentActionDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')

  return (
    <Dialog
      open={open}
      onClose={loading ? undefined : onClose}
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
            {title}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {message}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 300 }}>
            {t('consentRegistry.modals.consentId')}: {consentId}
          </Typography>
        </Stack>
      </DialogTitle>

      <DialogContent sx={{ px: 3, pt: 3.5, pb: 3 }}>
        <Stack spacing={2} sx={{ mt: 3 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Box
            sx={{
              width: '100%',
              p: 2,
              border: 1,
              borderColor: color === 'error' ? 'error.light' : 'divider',
              borderRadius: 1,
              bgcolor: color === 'error' ? 'error.lighter' : 'action.hover',
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
                color: color === 'error' ? 'error.main' : 'primary.main',
                bgcolor: 'background.paper',
                flexShrink: 0,
              }}
            >
              {icon}
            </Box>
            <Typography variant="body2" color="text.secondary">
              {note}
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
          color={color}
          variant="contained"
          startIcon={icon}
          disabled={loading}
          onClick={() => onConfirm()}
        >
          {loading ? t('consentRegistry.modals.actions.processing') : confirmLabel}
        </Button>
        <Button fullWidth variant="outlined" disabled={loading} onClick={onClose}>
          {t('consentRegistry.modals.actions.cancel')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

ConsentActionDialog.defaultProps = {
  error: undefined,
}

export default ConsentActionDialog
