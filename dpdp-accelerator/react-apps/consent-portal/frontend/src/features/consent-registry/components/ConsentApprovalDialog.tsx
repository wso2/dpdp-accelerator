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

import { CircleCheckBig } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import ConsentActionDialog from './ConsentActionDialog'

interface ConsentApprovalDialogProps {
  open: boolean
  consentId: string
  loading: boolean
  error?: string
  onClose: () => void
  onConfirm: () => void
}

function ConsentApprovalDialog({
  open,
  consentId,
  loading,
  error,
  onClose,
  onConfirm,
}: ConsentApprovalDialogProps): React.JSX.Element {
  const { t } = useTranslation('common')

  return (
    <ConsentActionDialog
      open={open}
      consentId={consentId}
      title={t('consentRegistry.modals.approval.title')}
      message={t('consentRegistry.modals.approval.message')}
      note={t('consentRegistry.modals.approval.note')}
      confirmLabel={t('consentRegistry.modals.approval.confirm')}
      color="primary"
      icon={<CircleCheckBig size={16} />}
      loading={loading}
      error={error}
      onClose={onClose}
      onConfirm={onConfirm}
    />
  )
}

ConsentApprovalDialog.defaultProps = {
  error: undefined,
}

export default ConsentApprovalDialog
