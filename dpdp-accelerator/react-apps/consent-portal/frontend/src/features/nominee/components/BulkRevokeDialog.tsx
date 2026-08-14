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
  LinearProgress,
  Stack,
  Typography,
} from '@wso2/oxygen-ui'
import { AlertTriangle } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import { dialogContentPadding } from '../../../components/common/dialogContentPadding'

interface BulkRevokeDialogProps {
  open: boolean
  count: number
  processed: number
  loading: boolean
  onClose: () => void
  onConfirm: () => void
}

function BulkRevokeDialog({
  open,
  count,
  processed,
  loading,
  onClose,
  onConfirm,
}: BulkRevokeDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const progress = count > 0 ? Math.round((processed / count) * 100) : 0

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
      <DialogTitle sx={{ p: 3, borderBottom: 1, borderColor: 'divider', textAlign: 'center' }}>
        <Stack spacing={0.75}>
          <Typography variant="h6" fontWeight={700}>
            {t('nominee.manage.bulkRevoke.title', 'Confirm Bulk Revocation')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('nominee.manage.bulkRevoke.message', {
              count,
              defaultValue: 'This will revoke {{count}} consent(s). This cannot be undone.',
            })}
          </Typography>
        </Stack>
      </DialogTitle>

      <DialogContent sx={dialogContentPadding(3.5)}>
        <Stack spacing={2}>
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
            <AlertTriangle size={20} />
            <Typography variant="body2" color="text.secondary">
              {t(
                'nominee.manage.bulkRevoke.note',
                'Each revocation is recorded separately as an action you performed on behalf of the owner.',
              )}
            </Typography>
          </Box>

          {loading ? (
            <Stack spacing={1}>
              <LinearProgress variant="determinate" value={progress} />
              <Typography variant="caption" color="text.secondary">
                {t('nominee.manage.bulkRevoke.progress', {
                  processed,
                  count,
                  defaultValue: 'Revoking {{processed}} of {{count}}...',
                })}
              </Typography>
            </Stack>
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
          flexDirection: 'column',
          gap: 1.25,
        }}
      >
        <Button fullWidth color="error" variant="contained" disabled={loading} onClick={onConfirm}>
          {loading
            ? t('nominee.manage.bulkRevoke.processing', 'Processing...')
            : t('nominee.manage.bulkRevoke.confirm', 'Revoke Selected')}
        </Button>
        <Button fullWidth variant="outlined" disabled={loading} onClick={onClose}>
          {t('nominee.manage.bulkRevoke.cancel', 'Cancel')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default BulkRevokeDialog
