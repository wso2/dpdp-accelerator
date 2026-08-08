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

import { useQuery } from '@tanstack/react-query'
import type { ComplaintActorRole } from '../../../types/complaint'
import {
  downloadManagedComplaintAttachment,
  downloadMyComplaintAttachment,
} from '../api/complaintsApi'
import { getAttachmentFileKind } from '../utils/complaintAttachments'

/**
 * Fetches an already-uploaded attachment's real bytes and returns a data URL, but only for
 * image attachments — PDFs/DOCs keep the synthetic preview since there's nothing to render.
 */
export function useAttachmentImagePreview(
  complaintId: string,
  attachmentId: string,
  fileName: string,
  viewerRole: Extract<ComplaintActorRole, 'DataPrincipal' | 'ComplaintOfficer'>,
): string | undefined {
  const isImage = getAttachmentFileKind(fileName) === 'image'

  const query = useQuery({
    queryKey: ['attachment-image', viewerRole, complaintId, attachmentId],
    queryFn: async (): Promise<string> => {
      const attachment =
        viewerRole === 'DataPrincipal'
          ? await downloadMyComplaintAttachment(complaintId, attachmentId)
          : await downloadManagedComplaintAttachment(complaintId, attachmentId)

      return `data:${attachment.contentType};base64,${attachment.content}`
    },
    enabled: isImage,
    staleTime: Infinity,
  })

  return query.data
}
