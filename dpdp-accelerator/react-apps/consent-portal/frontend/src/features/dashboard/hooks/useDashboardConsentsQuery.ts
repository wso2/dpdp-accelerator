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

import { type UseQueryResult, useQuery } from '@tanstack/react-query'
import type { ConsentDetail } from '../../../types/consent'
import { fetchMyConsents } from '../../consent-registry/api/consentsApi'

const DASHBOARD_PAGE_SIZE = 100
const DASHBOARD_MAX_PAGES = 20

/**
 * Walks the self-service pages until a short page arrives.
 *
 * `metadata.total` only counts the records seen so far, so a full page is the
 * only reliable signal that more consents may exist.
 */
async function fetchConsentPage(
  offset: number,
  collected: ConsentDetail[],
  remainingPages: number,
): Promise<ConsentDetail[]> {
  const response = await fetchMyConsents({ limit: DASHBOARD_PAGE_SIZE, offset })
  const consents = [...collected, ...response.data]

  if (response.data.length < DASHBOARD_PAGE_SIZE || remainingPages <= 1) {
    return consents
  }

  return fetchConsentPage(offset + response.data.length, consents, remainingPages - 1)
}

async function fetchAllMyConsents(): Promise<ConsentDetail[]> {
  return fetchConsentPage(0, [], DASHBOARD_MAX_PAGES)
}

export default function useDashboardConsentsQuery(): UseQueryResult<ConsentDetail[]> {
  return useQuery({
    queryKey: ['consents', 'dashboard'],
    queryFn: fetchAllMyConsents,
  })
}
