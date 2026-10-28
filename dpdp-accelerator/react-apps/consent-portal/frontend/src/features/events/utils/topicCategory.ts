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

import type { TopicRecord } from '../../../types/topic'

export const TOPIC_CATEGORIES = ['consent', 'user', 'custom'] as const

export type TopicCategory = (typeof TOPIC_CATEGORIES)[number]

const DEFAULT_SYSTEM_CONSENT_TOPICS = new Set([
  'consent.create',
  'consent.update',
  'consent.revoke',
  'consent.expire',
])
const DEFAULT_SYSTEM_USER_TOPICS = new Set(['user.data.change', 'user.account.delete'])

/**
 * Categorize a topic into 'consent', 'user', or 'custom'.
 *
 * Topics are checked as SYSTEM topics first:
 * - If initiatedBy is 'SYSTEM':
 *     - starts with 'consent.' -> 'consent' (Consent Topics)
 *     - starts with 'user.'    -> 'user' (User Topics)
 *     - otherwise              -> 'custom' (Custom Topics)
 * - If not a SYSTEM topic (e.g. created by a user / initiatedBy is 'USER'):
 *     - always 'custom' (Custom Topics)
 */
export function getTopicCategory(
  topic: TopicRecord | { initiatedBy?: string; name: string } | string,
  allTopics?: TopicRecord[],
): TopicCategory {
  if (typeof topic === 'string') {
    const record = allTopics?.find((r) => r.name.toLowerCase() === topic.toLowerCase())
    if (record) {
      return getTopicCategory(record)
    }
    const normalized = topic.trim().toLowerCase()
    if (DEFAULT_SYSTEM_CONSENT_TOPICS.has(normalized)) {
      return 'consent'
    }
    if (DEFAULT_SYSTEM_USER_TOPICS.has(normalized)) {
      return 'user'
    }
    return 'custom'
  }

  const isSystem = topic.initiatedBy?.toLowerCase() === 'system'
  if (isSystem) {
    const normalized = topic.name.trim().toLowerCase()
    if (normalized.startsWith('consent.')) {
      return 'consent'
    }
    if (normalized.startsWith('user.')) {
      return 'user'
    }
  }

  return 'custom'
}
