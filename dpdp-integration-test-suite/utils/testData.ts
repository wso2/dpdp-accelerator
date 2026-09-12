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

// This suite runs against a real, persistent environment (no per-test tenant reset), so every
// scenario that creates a record stamps a unique marker into its name and asserts by that
// marker or by the server-issued ID - never by "the list is empty" or "there's exactly one
// record", both of which would be false against an environment with prior runs' data still in it.
export function uniqueMarker(label: string): string {
  return `${label}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

// Catalog-management/lifecycle tests create real Purposes/Elements/Consents as setup for what the
// UI is actually being tested on - Purposes and Elements through their real admin forms, Consents
// through the admin API since consent creation has no UI at all. Unique names keep those records
// distinguishable from whatever prior runs left in the shared environment.
export function uniquePurposeName(): string {
  return uniqueMarker('purpose')
}

export function uniqueElementName(): string {
  return uniqueMarker('element')
}

export function uniqueServiceId(): string {
  return uniqueMarker('service')
}

// Tenant domains are unique server-wide regardless of activation state, and there's no real
// delete available (no `Tenant.TenantDelete` in carbon.xml) - so a fixed domain would make every
// run after the first fail with a domain-already-exists conflict. A fresh one every run means
// there's nothing to collide with, and nothing to clean up either.
export function uniqueTenantDomain(): string {
  return `${uniqueMarker('dpdp-e2e')}.com`
}

// Realistic-looking labels for tests/03-consents/ - picked per-call and stamped with
// uniqueMarker so records made by this suite's disposable, per-test setup (created and torn down
// via seedConsent) never collide with each other or with anything else left in this shared
// environment.
export interface ElementProfile {
  slug: string
  displayName: string
  description: string
}

export const ELEMENT_PROFILES: ElementProfile[] = [
  { slug: 'email_address', displayName: 'Email Address', description: 'Used to send account and service notifications.' },
  { slug: 'phone_number', displayName: 'Phone Number', description: 'Used for SMS notifications and two-factor verification.' },
  { slug: 'shipping_address', displayName: 'Shipping Address', description: 'Delivery address used for order fulfillment.' },
  { slug: 'date_of_birth', displayName: 'Date of Birth', description: 'Used to verify age eligibility.' },
  { slug: 'payment_card_details', displayName: 'Payment Card Details', description: 'Used to process purchases and refunds.' },
  { slug: 'employment_status', displayName: 'Employment Status', description: 'Used for credit and eligibility assessments.' },
  { slug: 'loyalty_card_number', displayName: 'Loyalty Card Number', description: 'Used to track and redeem loyalty program rewards.' },
]

export interface PurposeProfile {
  name: string
  type: string
  description: string
}

export const PURPOSE_PROFILES: PurposeProfile[] = [
  { name: 'Order Fulfillment', type: 'Operational', description: 'Consent to use delivery details to fulfil and ship customer orders.' },
  { name: 'Marketing Communications', type: 'Marketing', description: 'Consent to send promotional offers and product updates.' },
  { name: 'Fraud Prevention', type: 'Security', description: 'Consent to analyze account activity to detect fraudulent transactions.' },
  { name: 'Customer Support', type: 'Operational', description: 'Consent to access account details when providing support assistance.' },
  { name: 'Payment Processing', type: 'Financial', description: 'Consent to process payments and issue refunds for purchases.' },
  { name: 'Loyalty Program Enrollment', type: 'Marketing', description: 'Consent to enroll in the loyalty rewards program.' },
]

export const SERVICE_NAMES = [
  'mobile-banking-app',
  'food-delivery-app',
  'loyalty-rewards-app',
  'health-tracker-app',
  'travel-booking-app',
  'insurance-portal',
]

function pick<T>(pool: T[]): T {
  return pool[Math.floor(Math.random() * pool.length)]
}

/** A random realistic Element profile, its slug stamped unique for this call. */
export function randomElementProfile(): ElementProfile & { name: string } {
  const profile = pick(ELEMENT_PROFILES)
  const marker = uniqueMarker(profile.slug)
  return { ...profile, name: marker, displayName: `${profile.displayName} ${marker}` }
}

/** A random realistic Purpose profile, its name stamped unique for this call. */
export function randomPurposeProfile(): PurposeProfile {
  const profile = pick(PURPOSE_PROFILES)
  return { ...profile, name: `${profile.name} ${uniqueMarker('purpose')}` }
}

/** A random realistic service id, stamped unique for this call. */
export function randomServiceId(): string {
  return `${pick(SERVICE_NAMES)}-${uniqueMarker('svc')}`
}
