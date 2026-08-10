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

import { Box } from '@wso2/oxygen-ui'
import { FileText, Image, Paperclip } from '@wso2/oxygen-ui-icons-react'
import { getAttachmentFileKind } from '../utils/complaintAttachments'
import AttachmentThumbnail from './AttachmentThumbnail'

interface AttachmentTypeIconProps {
  fileName: string
  size: number
  variant: 'tile' | 'inline'
  imageUrl: string | undefined
}

const FILE_KIND_VISUALS = {
  image: { Icon: Image, color: 'success.main' },
  pdf: { Icon: FileText, color: 'error.main' },
  doc: { Icon: FileText, color: 'info.main' },
  other: { Icon: Paperclip, color: 'text.secondary' },
} as const

function AttachmentTypeIcon({
  fileName,
  size,
  variant,
  imageUrl,
}: AttachmentTypeIconProps): React.JSX.Element {
  const kind = getAttachmentFileKind(fileName)
  const { Icon, color } = FILE_KIND_VISUALS[kind]

  if (variant === 'inline') {
    return (
      <Box sx={{ display: 'flex', color, flexShrink: 0 }}>
        <Icon size={size} />
      </Box>
    )
  }

  if (kind === 'image' && imageUrl) {
    return (
      <Box
        component="img"
        src={imageUrl}
        alt={fileName}
        sx={{
          width: size,
          height: size,
          flexShrink: 0,
          borderRadius: 0,
          objectFit: 'cover',
        }}
      />
    )
  }

  if (kind === 'image' || kind === 'pdf' || kind === 'doc') {
    return (
      <Box sx={{ display: 'flex', flexShrink: 0, borderRadius: 0, overflow: 'hidden' }}>
        <AttachmentThumbnail fileName={fileName} kind={kind} size={size} />
      </Box>
    )
  }

  return (
    <Box
      sx={(theme) => ({
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: size,
        height: size,
        borderRadius: 0,
        flexShrink: 0,
        color,
        ...theme.applyStyles('light', { bgcolor: 'rgba(0, 0, 0, 0.05)' }),
        ...theme.applyStyles('dark', { bgcolor: 'rgba(255, 255, 255, 0.08)' }),
      })}
    >
      <Icon size={Math.round(size * 0.55)} />
    </Box>
  )
}

export default AttachmentTypeIcon
