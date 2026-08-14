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

import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'
import { dialogContentPadding } from '../../../../components/common/dialogContentPadding'

interface ConsentResourcesModalProps {
  open: boolean
  resourcesJson: string
  onClose: () => void
}

function ConsentResourcesModal({
  open,
  resourcesJson,
  onClose,
}: ConsentResourcesModalProps): React.JSX.Element {
  const { t } = useTranslation('common')

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ borderBottom: 1, borderColor: 'divider' }}>
        {t('consentRegistry.details.resourcesModal.title', 'Authorization Resources')}
      </DialogTitle>
      <DialogContent sx={dialogContentPadding(2.5, 3, 2)}>
        <Box
          component="pre"
          sx={{
            mb: 0,
            p: 2,
            border: 1,
            borderColor: 'divider',
            borderRadius: 1,
            bgcolor: 'background.default',
            color: 'text.primary',
            fontSize: '0.8125rem',
            fontFamily: 'monospace',
            whiteSpace: 'pre',
            overflow: 'auto',
            maxHeight: '60vh',
          }}
        >
          {resourcesJson}
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button variant="outlined" onClick={onClose}>
          {t('consentRegistry.details.resourcesModal.close', 'Close')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default ConsentResourcesModal
