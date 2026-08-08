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

import { hashFileName } from '../utils/complaintAttachments'

interface ImageThumbnailProps {
  fileName: string
  size: number
}

const IMAGE_PALETTES = [
  { bg: '#FFF3E0', bar: '#37474F', accent: '#FB8C00' },
  { bg: '#E3F2FD', bar: '#37474F', accent: '#1E88E5' },
  { bg: '#E8F5E9', bar: '#37474F', accent: '#43A047' },
  { bg: '#F3E5F5', bar: '#37474F', accent: '#8E24AA' },
  { bg: '#FCE4EC', bar: '#37474F', accent: '#D81B60' },
] as const

function ImageThumbnail({ fileName, size }: ImageThumbnailProps): React.JSX.Element {
  const palette = IMAGE_PALETTES[hashFileName(fileName) % IMAGE_PALETTES.length]

  return (
    <svg width={size} height={size} viewBox="0 0 36 36" aria-hidden="true">
      <rect width="36" height="36" rx="6" fill={palette.bg} />
      <rect x="3" y="3" width="30" height="4" rx="2" fill={palette.bar} />
      <circle cx="29" cy="5" r="1" fill="white" opacity="0.7" />
      <rect x="3" y="10" width="19" height="3" rx="1.5" fill="white" />
      <rect x="8" y="15" width="22" height="3" rx="1.5" fill={palette.accent} opacity="0.9" />
      <rect x="3" y="20" width="13" height="3" rx="1.5" fill="white" opacity="0.85" />
      <rect x="3" y="26" width="30" height="7" rx="1.5" fill={palette.accent} opacity="0.22" />
      <circle cx="9.5" cy="30" r="2" fill={palette.accent} />
      <path d="M4 33 L13 26.5 L19 31 L25 25.5 L32 33 Z" fill={palette.accent} opacity="0.55" />
    </svg>
  )
}

interface DocumentThumbnailProps {
  kind: 'pdf' | 'doc'
  size: number
}

const DOCUMENT_HEADER_COLORS: Record<'pdf' | 'doc', string> = {
  pdf: '#E53935',
  doc: '#1E88E5',
}

function DocumentThumbnail({ kind, size }: DocumentThumbnailProps): React.JSX.Element {
  return (
    <svg width={size} height={size} viewBox="0 0 36 36" aria-hidden="true">
      <rect width="36" height="36" rx="6" fill="#EEEEEE" />
      <rect x="6" y="4" width="24" height="28" rx="1.5" fill="white" />
      <rect x="6" y="4" width="24" height="7" rx="1.5" fill={DOCUMENT_HEADER_COLORS[kind]} />
      <rect x="9" y="15" width="18" height="2" rx="1" fill="#BDBDBD" />
      <rect x="9" y="19" width="18" height="2" rx="1" fill="#BDBDBD" />
      <rect x="9" y="23" width="12" height="2" rx="1" fill="#BDBDBD" />
      <rect x="9" y="27" width="15" height="2" rx="1" fill="#BDBDBD" />
    </svg>
  )
}

interface AttachmentThumbnailProps {
  fileName: string
  kind: 'image' | 'pdf' | 'doc'
  size: number
}

function AttachmentThumbnail({
  fileName,
  kind,
  size,
}: AttachmentThumbnailProps): React.JSX.Element {
  if (kind === 'image') {
    return <ImageThumbnail fileName={fileName} size={size} />
  }

  return <DocumentThumbnail kind={kind} size={size} />
}

export default AttachmentThumbnail
