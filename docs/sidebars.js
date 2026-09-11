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
      label: 'Install and Setup',
      items: [
        { type: 'doc', id: 'setup-guide', label: 'Setup' },
        { type: 'doc', id: 'configuration-guide', label: 'Configure' },
      ],
    },
    {
      type: 'category',
      label: 'Learn and Tryout',
      items: [
        { type: 'doc', id: 'learn', label: 'Learn' },
        { type: 'doc', id: 'tryout-flows', label: 'Tryout' },
      ],
    },
    {
      type: 'category',
      label: 'Developer Guide',
      items: [
        { type: 'doc', id: 'role-guide', label: 'Role Guide' },
        { type: 'doc', id: 'event-notification-guide', label: 'Event Notification' },
        { type: 'doc', id: 'grievances-guide', label: 'Grievance' },
        { type: 'doc', id: 'localization-guide', label: 'Localization' },
      ],
    },
  ],
};

module.exports = sidebars;
