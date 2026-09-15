import React from 'react';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

function Hero() {
  const { siteConfig } = useDocusaurusContext();
  return (
    <header style={{ padding: '4rem 0', textAlign: 'center' }}>
      <h1>{siteConfig.title}</h1>
      <p style={{ fontSize: '1.25rem' }}>{siteConfig.tagline}</p>
    </header>
  );
}

const guides = [
  { title: 'Introduction', description: 'Understand the DPDP Accelerator and its capabilities.', to: '/docs/introduction' },
  { title: 'Getting Started', description: 'Install the accelerator and open the Consent Portal.', to: '/docs/quickstart' },
  { title: 'Install and Setup', description: 'Prepare databases and configure portal roles and runtime features.', to: '/docs/setup-guide' },
  { title: 'Learn and Tryout', description: 'Explore real stories and follow feature walkthroughs.', to: '/docs/learn' },
  { title: 'Developer Guide', description: 'Explore roles, Event Notifications, grievances, and localization.', to: '/docs/role-guide' },
];

function GuideCard({ title, description, to }) {
  return (
    <Link
      to={to}
      style={{
        border: '1px solid var(--ifm-color-emphasis-300)',
        borderRadius: '8px',
        padding: '1.25rem',
        display: 'block',
        textDecoration: 'none',
        color: 'inherit',
      }}
    >
      <h3 style={{ marginBottom: '0.5rem' }}>{title}</h3>
      <p style={{ margin: 0, color: 'var(--ifm-color-emphasis-700)' }}>{description}</p>
    </Link>
  );
}

export default function Home() {
  return (
    <Layout title="Home" description="Documentation for the WSO2 DPDP Accelerator">
      <main className="container">
        <Hero />
        <section
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
            gap: '1rem',
            paddingBottom: '4rem',
          }}
        >
          {guides.map((guide) => (
            <GuideCard key={guide.title} {...guide} />
          ))}
        </section>
      </main>
    </Layout>
  );
}
