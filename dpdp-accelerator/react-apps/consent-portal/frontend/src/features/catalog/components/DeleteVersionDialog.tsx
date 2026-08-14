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

interface DeleteVersionDialogProps {
  open: boolean
  version: string
  loading: boolean
  error: string | undefined
  onClose: () => void
  onConfirm: () => void
}

function DeleteVersionDialog({
  open,
  version,
  loading,
  error,
  onClose,
  onConfirm,
}: DeleteVersionDialogProps): React.JSX.Element {
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
          bgcolor: 'background.default',
        }}
      >
        <Typography variant="h4" fontWeight={700}>
          {t('catalog.delete.title')}
        </Typography>
      </DialogTitle>
      <DialogContent sx={{ px: 3, mt: 3, pb: 3 }}>
        <Stack spacing={2}>
          <Typography>{t('catalog.delete.message', { version })}</Typography>
          <Typography variant="body2" color="text.secondary">
            {t('catalog.delete.warning')}
          </Typography>
          {error ? (
            <Typography variant="body2" color="error.main">
              {error}
            </Typography>
          ) : null}
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
        <Button fullWidth variant="outlined" onClick={onClose} disabled={loading}>
          {t('catalog.actions.cancel')}
        </Button>
        <Button fullWidth color="error" variant="contained" onClick={onConfirm} disabled={loading}>
          {loading ? t('catalog.actions.deleting') : t('catalog.actions.delete')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default DeleteVersionDialog
