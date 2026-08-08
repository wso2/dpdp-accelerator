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

import { IconButton } from '@wso2/oxygen-ui'
import { X } from '@wso2/oxygen-ui-icons-react'
import { useEffect, useMemo } from 'react'
import { getAttachmentFileKind } from '../utils/complaintAttachments'
import AttachmentPreviewCard from './AttachmentPreviewCard'

interface DraftAttachmentCardProps {
  file: File
  onRemove: () => void
  removeLabel: string
}

/**
 * Preview for a file staged for upload but not yet sent - the same
 * AttachmentPreviewCard used to render an already-sent attachment in the
 * activity feed, with a remove control in place of a download one.
 */
function DraftAttachmentCard({
  file,
  onRemove,
  removeLabel,
}: DraftAttachmentCardProps): React.JSX.Element {
  const isImage = getAttachmentFileKind(file.name) === 'image'
  const imageUrl = useMemo(() => (isImage ? URL.createObjectURL(file) : undefined), [file, isImage])

  useEffect(() => {
    return () => {
      if (imageUrl) {
        URL.revokeObjectURL(imageUrl)
      }
    }
  }, [imageUrl])

  return (
    <AttachmentPreviewCard
      fileName={file.name}
      fileSizeLabel={undefined}
      fullWidth={false}
      imageUrl={imageUrl}
      thumbnailSize={undefined}
    >
      <IconButton size="small" aria-label={removeLabel} onClick={onRemove}>
        <X size={14} />
      </IconButton>
    </AttachmentPreviewCard>
  )
}

export default DraftAttachmentCard
