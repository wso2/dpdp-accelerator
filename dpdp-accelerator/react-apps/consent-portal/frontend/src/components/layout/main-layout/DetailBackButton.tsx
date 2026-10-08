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

import { Button, type SxProps, type Theme } from '@wso2/oxygen-ui'
import { ArrowLeft } from '@wso2/oxygen-ui-icons-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

export interface DetailBackButtonProps {
  /** Target route to navigate to (e.g. '/events', '/events/subscriptions'). If omitted, navigates back (-1). */
  to?: string
  /** Custom label, defaults to "Back" */
  label?: string
  /** Optional custom click handler */
  onClick?: () => void
  /** Optional sx styling overrides */
  sx?: SxProps<Theme>
}

export default function DetailBackButton({
  to,
  label,
  onClick,
  sx,
}: DetailBackButtonProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()

  const handleClick = (): void => {
    if (onClick) {
      onClick()
    } else if (to) {
      navigate(to)
    } else {
      navigate(-1)
    }
  }

  return (
    <Button
      variant="outlined"
      color="primary"
      size="small"
      startIcon={<ArrowLeft size={16} />}
      onClick={handleClick}
      sx={{
        alignSelf: 'flex-start',
        borderColor: 'primary.main',
        color: 'primary.main',
        minWidth: 'auto',
        textTransform: 'none',
        fontWeight: 500,
        '&:hover': {
          borderColor: 'primary.dark',
          bgcolor: 'action.hover',
        },
        ...sx,
      }}
    >
      {label ?? t('common.back', 'Back')}
    </Button>
  )
}
