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

import { Box, Stack, Typography } from '@wso2/oxygen-ui'
import AttachmentTypeIcon from './AttachmentTypeIcon'

interface AttachmentPreviewCardProps {
  fileName: string
  fileSizeLabel: string | undefined
  fullWidth: boolean
  imageUrl: string | undefined
  thumbnailSize: number | undefined
  children: React.ReactNode
}

function AttachmentPreviewCard({
  fileName,
  fileSizeLabel,
  fullWidth,
  imageUrl,
  thumbnailSize = 36,
  children,
}: AttachmentPreviewCardProps): React.JSX.Element {
  return (
    <Stack
      direction="row"
      spacing={1.5}
      alignItems="center"
      sx={{
        p: 1.25,
        borderRadius: 1.5,
        border: 1,
        borderColor: 'divider',
        ...(fullWidth ? {} : { maxWidth: 320 }),
      }}
    >
      <AttachmentTypeIcon
        fileName={fileName}
        variant="tile"
        size={thumbnailSize}
        imageUrl={imageUrl}
      />

      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Typography variant="body2" fontWeight={600} noWrap>
          {fileName}
        </Typography>
        {fileSizeLabel ? (
          <Typography variant="caption" color="text.secondary">
            {fileSizeLabel}
          </Typography>
        ) : null}
      </Box>

      {children}
    </Stack>
  )
}

export default AttachmentPreviewCard
