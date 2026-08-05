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

const commonEn = {
  app: {
    title: 'Consent Portal',
  },
  authorization: {
    loading: 'Loading your session',
    loadFailed: 'Unable to load your session.',
    tryAgain: 'Try again',
    noAccessTitle: 'No portal access',
    noAccessDescription: 'Your account does not have permission to access any portal pages.',
  },
  copyableText: {
    copy: 'Copy',
    copied: 'Copied',
    copyLabel: 'Copy {{label}}',
    copyValue: 'Copy {{label}} {{value}}',
    valueAriaLabel: '{{label}}: {{value}}',
  },
  pagination: {
    rowsPerPage: 'Rows per page',
    previous: 'Previous',
    next: 'Next',
  },
  sidebar: {
    ariaLabel: 'Primary navigation',
    dashboard: 'Dashboard',
    consent: 'Consent',
    allConsents: 'All Consents',
    pendingConsents: 'Pending Consents',
    catalog: 'Definitions',
    purposes: 'Purposes',
    elements: 'Elements',
    administration: 'Administration',
    adminConsents: 'Consents',
  },
  layout: {
    home: 'Home',
    breadcrumbAriaLabel: 'Breadcrumb',
    userMenu: {
      unknownUser: 'Unknown user',
      noEmail: 'No email available',
      signOut: 'Sign out',
      signOutError: 'Unable to sign out. Please try again.',
      tryAgain: 'Try again',
    },
  },
  dashboard: {
    active: 'Active consents',
    attention: 'Needs your attention',
    commonPurposes: 'Most commonly shared purposes',
    commonPurposesSubtitle: 'Purposes included in your active consents',
    consentCount: '{{count}} consents',
    consentCount_one: '{{count}} consent',
    consentCount_other: '{{count}} consents',
    loadFailed: 'Unable to load your dashboard right now.',
    noPending: 'You have no pending consents to review.',
    noPurposes: 'No shared purposes are available yet.',
    noServices: 'No services are available yet.',
    pending: 'Pending consents',
    pendingConsents: 'Pending consents',
    serviceBreakdown: 'Consent breakdown by service',
    serviceBreakdownSubtitle: 'All of your consents grouped by requesting service',
    subtitle: 'An overview of your consent activity and upcoming actions.',
    title: 'Dashboard',
    viewPending: 'View all pending consents',
  },
  adminConsents: {
    title: 'Consents',
    filters: {
      sectionAriaLabel: 'Administrative consent filters',
      consentIdSearchPlaceholder: 'Search by consent ID',
      removeConsentIdForAdvanced: 'Remove the Consent ID filter to use advanced filters.',
      removeConsentIdForState: 'Remove the Consent ID filter to use the state filter.',
      subjectId: 'User',
      subjectIdHelp: 'Username of the data subject',
      serviceId: 'Service',
      active: 'Active filters',
    },
  },
  consentRegistry: {
    title: 'All Consents',
    details: {
      title: 'Consent Details',
      consentId: 'Consent ID',
      subject: 'User',
      service: 'Service',
      language: 'Language',
      created: 'Created',
      validUntil: 'Valid Until',
      back: 'Back to Registry',
      notFound: 'Consent record not found',
      elementCount: '{{count}} elements',
      elementCount_one: '{{count}} element',
      elementCount_other: '{{count}} elements',
      noAuthorizations: 'No authorizations are recorded for this consent.',
      noElements: 'No elements are associated with this purpose.',
      noPurposes: 'No purposes are associated with this consent.',
      section: {
        purposes: 'Consent Purposes',
        authorizations: 'Authorizations',
      },
      table: {
        element: 'Element',
        displayName: 'Display name',
        user: 'User',
        state: 'State',
        updated: 'Updated',
      },
    },
    actions: {
      view: 'View',
      revoke: 'Revoke',
      approve: 'Approve',
      reject: 'Reject',
      copyConsentId: 'Copy consent ID',
      copyConsentIdAriaLabel: 'Copy consent ID {{id}}',
    },
    modals: {
      consentId: 'Consent ID',
      actions: {
        cancel: 'Cancel',
        processing: 'Processing...',
      },
      approval: {
        title: 'Confirm Approval',
        message: 'Are you sure you want to approve this consent?',
        note: 'Approving grants every purpose and element requested by this consent.',
        confirm: 'Approve Consent',
      },
      rejection: {
        title: 'Confirm Rejection',
        message: 'Are you sure you want to reject this consent?',
        note: 'Rejecting this consent means the requested permissions will not be granted.',
        confirm: 'Reject Consent',
      },
      revocation: {
        title: 'Confirm Revocation',
        message: 'Are you sure you want to revoke this consent?',
        note: 'Revoking withdraws every purpose and element previously granted by this consent.',
        confirm: 'Revoke Consent',
      },
    },
    status: {
      all: 'All',
      active: 'Active',
      pending: 'Pending',
      approved: 'Approved',
      rejected: 'Rejected',
      revoked: 'Revoked',
      expired: 'Expired',
    },
    filters: {
      sectionAriaLabel: 'Consent filters',
      serviceSearchPlaceholder: 'Search by service',
      state: 'State',
      advanced: 'Advanced filters',
      apply: 'Apply',
      cancel: 'Cancel',
      clear: 'Clear all',
      clearAriaLabel: 'Clear all filters',
    },
    messages: {
      loading: 'Loading consents...',
      loadFailed: 'Unable to load consents right now.',
      emptyTitle: 'No consents found',
      empty: 'No consents found for the selected filters.',
    },
    table: {
      tableAriaLabel: 'Consent registry table',
      consentIdAriaLabel: 'Consent ID: {{id}}',
      notApplicable: 'Not applicable',
      purposes: {
        more: '+{{count}} more',
        title: 'Consent purposes',
      },
      headers: {
        consentId: 'Consent ID',
        user: 'User',
        service: 'Service',
        state: 'State',
        purposes: 'Purposes',
        created: 'Created',
        actions: 'Actions',
      },
    },
  },
  catalog: {
    actions: {
      retry: 'Retry',
      view: 'View',
    },
    fields: {
      description: 'Description',
      displayName: 'Display name',
      element: 'Element',
      elementId: 'Element ID',
      elements: 'Elements',
      latestVersion: 'Latest version',
      name: 'Name',
      properties: 'Properties',
      purpose: 'Purpose',
      purposeId: 'Purpose ID',
      requirement: 'Requirement',
      tenantDomain: 'Tenant domain',
      type: 'Type',
      version: 'Version',
    },
    values: {
      latest: 'Latest',
      mandatory: 'Mandatory',
      noDescription: 'No description',
      optional: 'Optional',
    },
    elements: {
      back: 'Back to elements',
      empty: 'No consent elements are defined yet.',
      emptyTitle: 'No elements found',
      loadFailed: 'Unable to load elements right now.',
      subtitle: 'Browse the reusable consent elements defined in the Identity Server.',
      tableLabel: 'Consent elements',
      title: 'Elements',
    },
    purposes: {
      back: 'Back to purposes',
      empty: 'No consent purposes are defined yet.',
      emptyTitle: 'No purposes found',
      loadFailed: 'Unable to load purposes right now.',
      subtitle: 'Browse the reusable consent purposes and their element definitions.',
      tableLabel: 'Consent purposes',
      title: 'Purposes',
    },
    details: {
      elements: 'Elements',
      versions: 'Version history',
    },
    messages: {
      noElements: 'No elements are configured for this purpose.',
      noProperties: 'No custom properties.',
      noVersions: 'No versions are available.',
      versionsLoadFailed: 'Unable to load version history right now.',
    },
  },
} as const

export default commonEn
