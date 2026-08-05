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

import { Box, IconButton, Tooltip, Typography } from '@wso2/oxygen-ui'
import { Check, Copy } from '@wso2/oxygen-ui-icons-react'
import { type MouseEvent, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

interface CopyableTextProps {
  value: string
  displayValue?: string
  truncateAt?: number
  textAriaLabel?: string
  copyAriaLabel?: string
  copyTooltip?: string
  monospace?: boolean
}

function CopyableText({
  value,
  displayValue,
  truncateAt,
  textAriaLabel,
  copyAriaLabel,
  copyTooltip,
  monospace = false,
}: CopyableTextProps): React.JSX.Element {
  const { t } = useTranslation('common')
  const [copied, setCopied] = useState(false)
  const resetTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const visibleValue =
    displayValue ??
    (truncateAt && value.length > truncateAt ? `${value.slice(0, truncateAt)}…` : value)

  useEffect(
    () => () => {
      if (resetTimer.current) clearTimeout(resetTimer.current)
    },
    [],
  )

  const handleCopy = (event: MouseEvent<HTMLButtonElement>): void => {
    event.stopPropagation()
    const writeRequest = navigator.clipboard?.writeText(value)

    if (!writeRequest) return

    writeRequest
      .then(() => {
        setCopied(true)
        if (resetTimer.current) clearTimeout(resetTimer.current)
        resetTimer.current = setTimeout(() => setCopied(false), 2000)
      })
      .catch(() => undefined)
  }

  return (
    <Box
      component="span"
      sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.25, minWidth: 0 }}
    >
      <Tooltip title={value}>
        <Typography
          component="span"
          variant="body2"
          aria-label={textAriaLabel ?? value}
          sx={{
            fontFamily: monospace ? 'monospace' : undefined,
            overflowWrap: 'anywhere',
            whiteSpace: truncateAt ? 'nowrap' : undefined,
          }}
        >
          {visibleValue}
        </Typography>
      </Tooltip>
      <Tooltip title={copied ? t('copyableText.copied') : (copyTooltip ?? t('copyableText.copy'))}>
        <IconButton
          size="small"
          aria-label={copyAriaLabel ?? t('copyableText.copy')}
          onClick={handleCopy}
        >
          {copied ? <Check size={14} /> : <Copy size={14} />}
        </IconButton>
      </Tooltip>
    </Box>
  )
}

CopyableText.defaultProps = {
  displayValue: undefined,
  truncateAt: undefined,
  textAriaLabel: undefined,
  copyAriaLabel: undefined,
  copyTooltip: undefined,
  monospace: false,
}

export default CopyableText
