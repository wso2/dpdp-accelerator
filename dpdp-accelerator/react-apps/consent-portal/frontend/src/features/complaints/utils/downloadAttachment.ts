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

import type { ComplaintActorRole } from '../../../types/complaint'
import {
  downloadManagedComplaintAttachment,
  downloadMyComplaintAttachment,
} from '../api/complaintsApi'

export async function downloadComplaintAttachment(
  complaintId: string,
  attachmentId: string,
  fileName: string,
  viewerRole: Extract<ComplaintActorRole, 'DataPrincipal' | 'ComplaintOfficer'>,
): Promise<void> {
  const attachment =
    viewerRole === 'DataPrincipal'
      ? await downloadMyComplaintAttachment(complaintId, attachmentId)
      : await downloadManagedComplaintAttachment(complaintId, attachmentId)
  const byteCharacters = atob(attachment.content)
  const byteNumbers = new Array<number>(byteCharacters.length)

  for (let index = 0; index < byteCharacters.length; index += 1) {
    byteNumbers[index] = byteCharacters.charCodeAt(index)
  }

  const blob = new Blob([new Uint8Array(byteNumbers)], { type: attachment.contentType })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
