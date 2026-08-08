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
  ComplaintActorRoleAPI,
  ComplaintAttachmentAPI,
  ComplaintRecordAPI,
  ComplaintTimelineEntryAPI,
  ComplaintTimelineEntryTypeAPI,
  ComplaintActorRole,
  ComplaintAttachment,
  ComplaintDetail,
  ComplaintRecord,
  ComplaintStatus,
  ComplaintTimelineEntry,
} from '../../../types/complaint'

const ACTOR_ROLE_FROM_API: Record<ComplaintActorRoleAPI, ComplaintActorRole> = {
  USER: 'DataPrincipal',
  COMPLAINT_OFFICER: 'ComplaintOfficer',
  SYSTEM: 'System',
}

export function mapActorRoleFromApi(role: ComplaintActorRoleAPI): ComplaintActorRole {
  return ACTOR_ROLE_FROM_API[role]
}

const FILE_SIZE_UNITS = ['B', 'KB', 'MB', 'GB'] as const

export function formatFileSize(sizeBytes: number): string {
  if (sizeBytes <= 0) {
    return '0 B'
  }

  const exponent = Math.min(
    Math.floor(Math.log(sizeBytes) / Math.log(1024)),
    FILE_SIZE_UNITS.length - 1,
  )
  const value = sizeBytes / 1024 ** exponent

  return `${exponent === 0 ? value : value.toFixed(1)} ${FILE_SIZE_UNITS[exponent]}`
}

function mapAttachmentFromApi(attachment: ComplaintAttachmentAPI): ComplaintAttachment {
  return {
    id: attachment.attachmentId,
    fileName: attachment.fileName,
    fileSizeLabel: formatFileSize(attachment.sizeBytes),
  }
}

function mapTimelineEntryType(
  entryType: ComplaintTimelineEntryTypeAPI,
  toStatus: ComplaintStatus | undefined,
): ComplaintTimelineEntry['type'] {
  if (entryType === 'STATUS_CHANGE') {
    return toStatus === 'RESOLVED' ? 'resolution' : 'statusChange'
  }

  return entryType === 'INTERNAL_NOTE' ? 'note' : 'communication'
}

function mapTimelineEntryFromApi(entry: ComplaintTimelineEntryAPI): ComplaintTimelineEntry {
  return {
    id: entry.id,
    type: mapTimelineEntryType(entry.type, entry.toStatus),
    actorName: entry.actorUserId ?? '',
    actorRole: mapActorRoleFromApi(entry.actorRole),
    message: entry.message,
    timestamp: entry.createdTime,
    visibility: entry.isPublic ? 'shared' : 'internal',
    fromStatus: entry.fromStatus,
    toStatus: entry.toStatus,
    attachments: entry.attachments.map(mapAttachmentFromApi),
  }
}

function buildAcknowledgementEntry(record: ComplaintRecordAPI): ComplaintTimelineEntry {
  return {
    id: `${record.id}-acknowledgement`,
    type: 'systemAcknowledgement',
    actorName: 'System',
    actorRole: 'System',
    message: '',
    timestamp: record.submittedAt,
    visibility: 'shared',
  }
}

export function buildComplaintDetail(
  record: ComplaintRecordAPI,
  timelineEntries: ComplaintTimelineEntryAPI[],
): ComplaintDetail {
  return {
    id: record.id,
    referenceId: record.referenceId,
    category: record.subjectCategory,
    priority: record.priority,
    status: record.status,
    dataPrincipalName: record.userId,
    submittedAt: record.submittedAt,
    updatedAt: record.updatedAt,
    statutoryDueDate: record.statutoryDueDate,
    description: record.description,
    attachments: record.attachments.map(mapAttachmentFromApi),
    timeline: [buildAcknowledgementEntry(record), ...timelineEntries.map(mapTimelineEntryFromApi)],
  }
}

export function buildComplaintRecord(record: ComplaintRecordAPI): ComplaintRecord {
  return {
    id: record.id,
    referenceId: record.referenceId,
    category: record.subjectCategory,
    priority: record.priority,
    status: record.status,
    dataPrincipalName: record.userId,
    submittedAt: record.submittedAt,
    updatedAt: record.updatedAt,
    statutoryDueDate: record.statutoryDueDate,
  }
}
