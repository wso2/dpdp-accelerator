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

import type { APIRequestContext, APIResponse } from '@playwright/test'
import {
  adminConsentsApiUrl,
  consentElementsApiUrl,
  consentHistoryApiUrl,
  consentPurposesApiUrl,
  myConsentsApiUrl,
} from '../utils/env'
import type { AuthHeaders } from '../utils/authStorage'

export interface PurposeElementBinding {
  id: string
  mandatory?: boolean
}

export interface CreatePurposeBody {
  name: string
  type: string
  version: string
  description?: string
  elements?: PurposeElementBinding[]
  properties?: Record<string, string>
}

export interface CreateElementBody {
  name: string
  displayName?: string
  description?: string
  properties?: Record<string, string>
}

export interface ConsentPurposeBinding {
  id: string
  elements: Array<{ id: string }>
}

export interface AuthorizationEntry {
  userId: string
  type?: string
}

export interface CreateConsentBody {
  subjectId: string
  serviceId: string
  purposes: ConsentPurposeBinding[]
  language?: string
  /** Omit when `authorizations` is set - the server forces PENDING and rejects an explicit state. */
  state?: 'ACTIVE' | 'REJECTED'
  expiryTime?: number
  authorizations?: AuthorizationEntry[]
  properties?: Record<string, string>
}

export interface ConsentHistoryQueryParams {
  limit?: number
  offset?: number
}

export interface AdminConsentListParams {
  limit?: number
  after?: string
  before?: string
  subjectId?: string
  serviceId?: string
  state?: string
}

export interface MyConsentListParams {
  serviceId?: string
  state?: 'PENDING' | 'ACTIVE' | 'REJECTED' | 'REVOKED' | 'EXPIRED'
  purposeId?: string
  purposeVersionId?: string
  limit?: number
  after?: string
  before?: string
}

/**
 * Wraps two of WSO2 IS's own REST APIs, called directly (no backend proxy any more - see
 * docs/content/configuration-guide.md): the self-service surface (`/api/users/v1/me/consents/*`, from
 * org.wso2.carbon.identity.rest.api.user.consent.v1, needing only the `internal_login` scope every
 * signed-in user already has - see consent.yaml bundled in that jar) and the administrative
 * surface (`/api/identity/consent-mgt/v2.0/{consents,purposes,elements}`, from
 * org.wso2.carbon.identity.api.server.consent.management.v2, needing the `internal_consent_mgt_*`
 * scopes only the `dpdp-consent-admin` role carries - see consent-management-v2.yaml bundled in
 * that jar).
 *
 * A single class serves both surfaces because the auth is just a header pair resolved from
 * whichever persona's captured auth state the caller passes in (see utils/authStorage.ts) - tests
 * that need to prove a user's token is rejected by the admin surface construct this client
 * with the user's headers and call an admin method directly.
 *
 * `tenantDomain`, when passed, prefixes every URL with `/t/<tenant>` - confirmed live that a real
 * bearer token reaches both surfaces fine tenant-qualified (see fixtures/tenant.fixtures.ts).
 * Omit it for the super-tenant every other caller of this class targets.
 */
export class ConsentApiClient {
  constructor(
    private readonly request: APIRequestContext,
    private readonly auth: AuthHeaders,
    private readonly tenantDomain?: string,
  ) {}

  private headers(extra?: Record<string, string>): Record<string, string> {
    return { ...this.auth, ...extra }
  }

  // --------------------------------------------------------------------- self-service surface

  async listMyConsents(params: MyConsentListParams = {}): Promise<APIResponse> {
    return this.request.get(myConsentsApiUrl('', this.tenantDomain), {
      headers: this.headers(),
      params: Object.fromEntries(
        Object.entries(params)
          .filter(([, value]) => value !== undefined)
          .map(([key, value]) => [key, String(value)]),
      ),
    })
  }

  async getMyConsent(consentId: string): Promise<APIResponse> {
    return this.request.get(myConsentsApiUrl(`/${consentId}`, this.tenantDomain), { headers: this.headers() })
  }

  /** A PENDING consent transitions to ACTIVE once every listed authorizer has APPROVED, or to
   * REJECTED as soon as any one of them REJECTs - see consent.yaml's authorize operation. */
  async authorizeMyConsent(consentId: string, state: 'APPROVED' | 'REJECTED'): Promise<APIResponse> {
    return this.request.post(myConsentsApiUrl(`/${consentId}/authorize`, this.tenantDomain), {
      headers: this.headers({ 'Content-Type': 'application/json' }),
      data: { state },
    })
  }

  async revokeMyConsent(consentId: string): Promise<APIResponse> {
    return this.request.post(myConsentsApiUrl(`/${consentId}/revoke`, this.tenantDomain), {
      headers: this.headers({ 'Content-Type': 'application/json' }),
      data: {},
    })
  }

  // ---------------------------------------------------------------------------- admin surface

  async createPurpose(body: CreatePurposeBody): Promise<APIResponse> {
    return this.request.post(consentPurposesApiUrl('', this.tenantDomain), {
      headers: this.headers({ 'Content-Type': 'application/json' }),
      data: body,
    })
  }

  async createElement(body: CreateElementBody): Promise<APIResponse> {
    return this.request.post(consentElementsApiUrl('', this.tenantDomain), {
      headers: this.headers({ 'Content-Type': 'application/json' }),
      data: body,
    })
  }

  /** Exact match on `name` - elements have no other unique-ish field to search by. */
  async findElementByName(name: string): Promise<APIResponse> {
    return this.request.get(consentElementsApiUrl('', this.tenantDomain), {
      headers: this.headers(),
      params: { filter: `name eq "${name}"`, limit: 1 },
    })
  }

  /** Exact match on `name` - a purpose can have several versions but the name alone identifies it here. */
  async findPurposeByName(name: string): Promise<APIResponse> {
    return this.request.get(consentPurposesApiUrl('', this.tenantDomain), {
      headers: this.headers(),
      params: { filter: `name eq "${name}"`, limit: 1 },
    })
  }

  /** WSO2 IS caps this at 100 regardless of the requested `limit` - page via `after` to see more. */
  async listElements(params: { limit?: number; after?: string } = {}): Promise<APIResponse> {
    return this.request.get(consentElementsApiUrl('', this.tenantDomain), {
      headers: this.headers(),
      params: Object.fromEntries(
        Object.entries(params)
          .filter(([, value]) => value !== undefined)
          .map(([key, value]) => [key, String(value)]),
      ),
    })
  }

  /** Same 100-record server cap as listElements. */
  async listPurposes(params: { limit?: number; after?: string } = {}): Promise<APIResponse> {
    return this.request.get(consentPurposesApiUrl('', this.tenantDomain), {
      headers: this.headers(),
      params: Object.fromEntries(
        Object.entries(params)
          .filter(([, value]) => value !== undefined)
          .map(([key, value]) => [key, String(value)]),
      ),
    })
  }

  /** 409 when still referenced by a Purpose - callers sweeping in bulk should tolerate that. */
  async deleteElement(elementId: string): Promise<APIResponse> {
    return this.request.delete(consentElementsApiUrl(`/${elementId}`, this.tenantDomain), { headers: this.headers() })
  }

  /** 409 when still referenced by a Consent - callers sweeping in bulk should tolerate that. */
  async deletePurpose(purposeId: string): Promise<APIResponse> {
    return this.request.delete(consentPurposesApiUrl(`/${purposeId}`, this.tenantDomain), { headers: this.headers() })
  }

  async createConsent(body: CreateConsentBody): Promise<APIResponse> {
    return this.request.post(adminConsentsApiUrl('', this.tenantDomain), {
      headers: this.headers({ 'Content-Type': 'application/json' }),
      data: body,
    })
  }

  async listAdminConsents(params: AdminConsentListParams = {}): Promise<APIResponse> {
    return this.request.get(adminConsentsApiUrl('', this.tenantDomain), {
      headers: this.headers(),
      params: Object.fromEntries(
        Object.entries(params)
          .filter(([, value]) => value !== undefined)
          .map(([key, value]) => [key, String(value)]),
      ),
    })
  }

  async revokeAdminConsent(consentId: string): Promise<APIResponse> {
    return this.request.post(adminConsentsApiUrl(`/${consentId}/revoke`, this.tenantDomain), {
      headers: this.headers({ 'Content-Type': 'application/json' }),
      data: {},
    })
  }

  // --------------------------------------------------------------- consent-history surface (admin)

  /** Status-audit trail only (previousStatus/currentStatus/actionType/actionBy/actionTime). */
  async getConsentStatusHistory(
    consentId: string,
    params: ConsentHistoryQueryParams = {},
  ): Promise<APIResponse> {
    return this.request.get(consentHistoryApiUrl(`/consents/${consentId}/status-history`, this.tenantDomain), {
      headers: this.headers(),
      params: Object.fromEntries(
        Object.entries(params)
          .filter(([, value]) => value !== undefined)
          .map(([key, value]) => [key, String(value)]),
      ),
    })
  }

  /** Full pre/post-mutation snapshot history, including the EXPIRE entries the reconciler writes. */
  async getConsentHistory(consentId: string, params: ConsentHistoryQueryParams = {}): Promise<APIResponse> {
    return this.request.get(consentHistoryApiUrl(`/consents/${consentId}/history`, this.tenantDomain), {
      headers: this.headers(),
      params: Object.fromEntries(
        Object.entries(params)
          .filter(([, value]) => value !== undefined)
          .map(([key, value]) => [key, String(value)]),
      ),
    })
  }
}
