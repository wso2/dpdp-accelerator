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

import type {
  ComplaintActorRole,
  ComplaintAttachment,
  ComplaintDetail,
} from '../../../types/complaint'

export type AttachmentFileKind = 'image' | 'pdf' | 'doc' | 'other'

const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg'])
const DOC_EXTENSIONS = new Set(['doc', 'docx'])

export function getAttachmentFileKind(fileName: string): AttachmentFileKind {
  const extension = fileName.split('.').pop()?.toLowerCase() ?? ''

  if (IMAGE_EXTENSIONS.has(extension)) {
    return 'image'
  }

  if (extension === 'pdf') {
    return 'pdf'
  }

  if (DOC_EXTENSIONS.has(extension)) {
    return 'doc'
  }

  return 'other'
}

export function hashFileName(fileName: string): number {
  let hash = 0

  for (let index = 0; index < fileName.length; index += 1) {
    hash = (hash * 31 + fileName.charCodeAt(index)) % 2147483647
  }

  return hash
}

export interface AggregatedComplaintAttachment {
  key: string
  attachment: ComplaintAttachment
  actorName: string
  actorRole: ComplaintActorRole
  isInternal: boolean
  isInitialSubmission: boolean
  timestamp: string
}

export function collectComplaintAttachments(
  complaint: ComplaintDetail,
  viewerRole: Extract<ComplaintActorRole, 'DataPrincipal' | 'ComplaintOfficer'>,
): AggregatedComplaintAttachment[] {
  const initialSubmission: AggregatedComplaintAttachment[] = complaint.attachments.map(
    (attachment) => ({
      key: `submission-${attachment.id}`,
      attachment,
      actorName: complaint.dataPrincipalName,
      actorRole: 'DataPrincipal',
      isInternal: false,
      isInitialSubmission: true,
      timestamp: complaint.submittedAt,
    }),
  )

  const visibleTimeline =
    viewerRole === 'DataPrincipal'
      ? complaint.timeline.filter((entry) => entry.visibility === 'shared')
      : complaint.timeline

  const fromTimeline: AggregatedComplaintAttachment[] = visibleTimeline.flatMap((entry) =>
    (entry.attachments ?? []).map((attachment) => ({
      key: `entry-${attachment.id}`,
      attachment,
      actorName: entry.actorName,
      actorRole: entry.actorRole,
      isInternal: entry.visibility === 'internal',
      isInitialSubmission: false,
      timestamp: entry.timestamp,
    })),
  )

  return [...initialSubmission, ...fromTimeline].sort(
    (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime(),
  )
}
