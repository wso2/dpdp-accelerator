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
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Typography,
} from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'

interface ElementDeleteDialogProps {
  open: boolean
  elementName: string
  loading: boolean
  error?: string
  onClose: () => void
  onConfirm: () => void
}

function ElementDeleteDialog({
  open,
  elementName,
  loading,
  error,
  onClose,
  onConfirm,
}: ElementDeleteDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ p: 3, borderBottom: 1, borderColor: 'divider' }}>
        {t('catalog.elementDelete.title')}
      </DialogTitle>

      <DialogContent sx={{ p: 3 }}>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Typography variant="body2">
            {t('catalog.elementDelete.message', { name: elementName })}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('catalog.elementDelete.note')}
          </Typography>
        </Stack>
      </DialogContent>

      <DialogActions sx={{ p: 3, pt: 2, borderTop: 1, borderColor: 'divider' }}>
        <Button disabled={loading} onClick={onClose}>
          {t('catalog.actions.cancel')}
        </Button>
        <Button variant="contained" color="error" disabled={loading} onClick={onConfirm}>
          {loading ? t('catalog.elementDelete.deleting') : t('catalog.elementDelete.confirm')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

// React 19 no longer applies defaultProps on function components; this exists
// only to satisfy the react/require-default-props lint rule.
ElementDeleteDialog.defaultProps = {
  error: undefined,
}

export default ElementDeleteDialog
