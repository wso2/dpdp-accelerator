// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docs: [
    {
      type: 'category',
      label: 'Guides',
      collapsible: false,
      items: [
        'setup-guide',
        'configuration-guide',
        'event-notification-guide',
        'localization-guide',
        'release-guide',
      ],
    },
  ],
};

module.exports = sidebars;
