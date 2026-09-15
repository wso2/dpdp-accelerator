// @ts-check
const { themes: prismThemes } = require('prism-react-renderer');

const baseUrl = process.env.BASE_URL || '/dpdp-accelerator/';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'WSO2 DPDP Accelerator Documentation',
  tagline: 'Documentation for the WSO2 DPDP (Digital Personal Data Protection) Accelerator',
  favicon: 'img/favicon.svg',
  url: 'https://wso2.github.io',
  baseUrl,
  organizationName: 'wso2',
  projectName: 'dpdp-accelerator',
  onBrokenLinks: 'throw',
  markdown: {
    hooks: {
      onBrokenMarkdownLinks: 'throw',
    },
  },
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },
  themes: [
    [
      '@easyops-cn/docusaurus-search-local',
      {
        hashed: true,
        language: ['en'],
        highlightSearchTermsOnTargetPage: true,
        explicitSearchResultPath: true,
        docsDir: 'content',
        docsRouteBasePath: 'docs',
        indexBlog: false,
        indexPages: true,
        searchBarShortcutHint: false,
      },
    ],
  ],
  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          path: 'content',
          routeBasePath: 'docs',
          sidebarPath: './sidebars.js',
          editUrl: 'https://github.com/wso2/dpdp-accelerator/edit/main/docs/',
          showLastUpdateTime: true,
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      }),
    ],
  ],
  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'img/logo.svg',
      colorMode: {
        defaultMode: 'light',
        disableSwitch: false,
        respectPrefersColorScheme: true,
      },
      docs: {
        sidebar: {
          hideable: true,
          autoCollapseCategories: true,
        },
      },
      navbar: {
        title: 'WSO2 DPDP Accelerator',
        logo: {
          alt: 'WSO2 DPDP Accelerator',
          src: 'img/logo.svg',
          srcDark: 'img/logo-dark.svg',
        },
        items: [
          { to: '/docs/quickstart', label: 'Quickstart', position: 'left' },
          { to: '/docs/setup-guide', label: 'Setup', position: 'left' },
          { to: '/docs/learn', label: 'Learn', position: 'left' },
          {
            href: 'https://github.com/wso2/dpdp-accelerator',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Guides',
            items: [
              { label: 'Setup', to: '/docs/setup-guide' },
              { label: 'Configuration', to: '/docs/configuration-guide' },
              { label: 'Event Notifications', to: '/docs/event-notification-guide' },
              { label: 'Localization', to: '/docs/localization-guide' },
              { label: 'Release', to: '/docs/release-guide' },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} WSO2 LLC. Built with Docusaurus.`,
      },
      prism: {
        theme: prismThemes.github,
        darkTheme: prismThemes.dracula,
        additionalLanguages: ['java', 'bash', 'json', 'yaml', 'toml', 'markup'],
      },
    }),
};

module.exports = config;
