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
      <div style={{ marginTop: '2rem' }}>
        <Link className="button button--primary button--lg" to="/docs/setup-guide">
          Get started
        </Link>
      </div>
    </header>
  );
}

const guides = [
  { title: 'Setup', description: 'Install the accelerator and start the Identity Server.', to: '/docs/setup-guide' },
  { title: 'Configuration', description: 'Register the Consent Portal application and assign roles.', to: '/docs/configuration-guide' },
  { title: 'Event Notifications', description: 'Create topics, publish events, and manage subscriptions.', to: '/docs/event-notification-guide' },
  { title: 'Localization', description: 'Fix wording and localize Purposes/Elements.', to: '/docs/localization-guide' },
  { title: 'Release', description: 'Cut a release with the Release builder workflow.', to: '/docs/release-guide' },
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
