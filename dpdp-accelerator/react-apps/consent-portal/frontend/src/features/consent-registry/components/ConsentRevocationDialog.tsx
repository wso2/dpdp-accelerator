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

import { Ban } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import ConsentActionDialog from './ConsentActionDialog'

interface ConsentRevocationDialogProps {
  open: boolean
  consentId: string
  loading: boolean
  error?: string
  onClose: () => void
  onConfirm: () => void
}

function ConsentRevocationDialog({
  open,
  consentId,
  loading,
  error,
  onClose,
  onConfirm,
}: ConsentRevocationDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')

  return (
    <ConsentActionDialog
      open={open}
      consentId={consentId}
      title={t('consentRegistry.modals.revocation.title')}
      message={t('consentRegistry.modals.revocation.message')}
      note={t('consentRegistry.modals.revocation.note')}
      confirmLabel={t('consentRegistry.modals.revocation.confirm')}
      color="error"
      icon={<Ban size={16} />}
      loading={loading}
      error={error}
      onClose={onClose}
      onConfirm={onConfirm}
    />
  )
}

ConsentRevocationDialog.defaultProps = {
  error: undefined,
}

export default ConsentRevocationDialog
