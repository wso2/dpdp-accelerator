// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docs: [
    { type: 'doc', id: 'introduction', label: 'Introduction' },
    {
      type: 'category',
      label: 'Getting Started',
      items: [
        { type: 'doc', id: 'quickstart', label: 'Quickstart' },
      ],
    },
    {
      type: 'category',
      label: 'Install and Set Up',
      items: [
        { type: 'doc', id: 'install-and-setup/prerequisites', label: '1. Prerequisites' },
        { type: 'doc', id: 'install-and-setup/setting-up-servers', label: '2. Setting Up Servers' },
        { type: 'doc', id: 'install-and-setup/setting-up-the-database', label: '3. Setting Up the Database' },
        { type: 'doc', id: 'install-and-setup/configuring-the-accelerator', label: '4. Configuring the Accelerator' },
        { type: 'doc', id: 'install-and-setup/configuring-users-and-roles', label: '5. Configuring Users and Roles' },
      ],
    },
    {
      type: 'category',
      label: 'Learn',
      items: [
        { type: 'doc', id: 'learn/introduction', label: 'Introduction' },
        { type: 'doc', id: 'learn/managing-access', label: 'Managing Access' },
        { type: 'doc', id: 'learn/consent', label: 'Consent' },
        { type: 'doc', id: 'learn/complaint', label: 'Complaint' },
        { type: 'doc', id: 'learn/event', label: 'Event Notifications' },
      ],
    },
    {
      type: 'category',
      label: 'Try Out',
      items: [
        { type: 'doc', id: 'try-out/consent', label: 'Consent' },
        { type: 'doc', id: 'try-out/complaint', label: 'Complaint' },
        { type: 'doc', id: 'try-out/event', label: 'Event Notifications' },
      ],
    },
    {
      type: 'category',
      label: 'Developer Guide',
      link: {
        type: 'generated-index',
        slug: '/developer-guide',
        description: 'Reference guides for roles, consent lifecycle, Event Notifications, grievances, and localization.',
      },
      items: [
        { type: 'doc', id: 'role-guide', label: 'Role Guide' },
        { type: 'doc', id: 'consent-lifecycle-guide', label: 'Consent Lifecycle' },
        { type: 'doc', id: 'event-notification-guide', label: 'Event Notification' },
        { type: 'doc', id: 'grievances-guide', label: 'Grievance' },
        { type: 'doc', id: 'localization-guide', label: 'Localization' },
      ],
    },
  ],
};

module.exports = sidebars;
