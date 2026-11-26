# Digital Personal Data Protection and the WSO2 DPDP Accelerator

India's [Digital Personal Data Protection Act, 2023](https://www.indiacode.nic.in/indiacode/handle/123456789/22037)
(DPDP Act) establishes a framework for processing digital personal data that
recognises both an individual's right to protect personal data and the need to
process it for lawful purposes. The WSO2 DPDP Accelerator extends WSO2
Identity Server with consent, complaint (grievance), audit, and event-notification
capabilities that help an organization put parts of that framework into
operation.

> Disclaimer: This page is a product introduction, not legal advice or a statement of
> compliance. An organization remains responsible for determining how the Act,
> its commencement notifications, and applicable rules affect its processing
> activities.

## What is the DPDP Act?

The DPDP Act applies to digital personal data processed in India, including
data collected in non-digital form and digitised later. It also applies to
processing outside India when that processing is connected with offering
goods or services to Data Principals (the individuals the personal data relates
to) in India. The Act excludes specified
personal or domestic processing and certain personal data made publicly
available by the Data Principal or by a person legally required to publish it.

The framework is built around several practical ideas:

- Process personal data for a lawful purpose, using consent or another use
  permitted by the Act.
- Give a clear notice and obtain consent that is free, specific, informed,
  unconditional, unambiguous, and expressed through clear affirmative action
  when consent is the basis for processing.
- Make withdrawing consent as easy as giving it and communicate the resulting
  change to the processors acting for the Data Fiduciary, the organization or
  person deciding why and how the data is processed.
- Keep personal data accurate where it is used to make decisions or disclosed
  to another Data Fiduciary, apply reasonable security safeguards, and respond
  to personal-data breaches.
- Provide ways for Data Principals to exercise their rights and raise
  grievances.

The Act gives Data Principals rights to obtain specified information about
processing, seek correction, completion, updating, or erasure, use grievance
redressal, and nominate another individual to exercise their rights in the
event of death or incapacity. It also places duties on Data Principals, such as
not impersonating another person or submitting a false or frivolous grievance.
The precise conditions and exceptions in the Act still apply to every right
and obligation.

The Act's provisions commence in phases. Use the
[official commencement notification](https://www.meity.gov.in/static/uploads/2025/11/c56ceae6c383460ca69577428d36828b.pdf)
to check when a provision takes effect; this product guide does not define a
compliance deadline.

## Participants in the DPDP ecosystem

![DPDP ecosystem showing the relationships among a Data Principal, Data Fiduciary, Data Processor, Consent Manager, and Data Protection Board of India](../assets/images/diagrams/dpdp-ecosystem.svg)

The diagram shows consent communicated through a Consent Manager,
breach notification to the Board, and a complaint after exhausting grievance
redressal. These relationships are described in sections 6, 8, and 13 of the
[Act](https://www.meity.gov.in/static/uploads/2024/06/2bf1f0e9f04e6fb4f8fef35e82c42aa5.pdf).
They describe the wider ecosystem; the accelerator does not submit complaints
or breach notifications to the Board automatically.

| Participant | Role under the Act |
|---|---|
| **Data Principal** | The individual to whom the personal data relates. The definition includes a parent or lawful guardian acting for a child, and a lawful guardian acting for a person with disability. |
| **Data Fiduciary** | Determines the purpose and means of processing, alone or with others, and remains responsible for processing performed on its behalf. |
| **Data Processor** | Processes personal data on behalf of a Data Fiduciary. A Data Fiduciary may engage a processor under a valid contract. |
| **Consent Manager** | A person registered with the Data Protection Board of India that provides an accessible, transparent, and interoperable platform through which a Data Principal can give, manage, review, and withdraw consent. |
| **Significant Data Fiduciary** | A Data Fiduciary notified by the Central Government based on factors in the Act and subject to additional obligations. |
| **Data Protection Board of India** | The statutory body established under the Act to perform the functions assigned to it, including handling personal-data-breach intimations and complaints. |

The accelerator helps a Data Fiduciary provide consent-management experiences.
Installing it does **not** register the organization or the product as a
statutory Consent Manager.

### Scenario: Digital Healthcare & Teleconsultation

**Priya Sharma** uses **CarePulse**, an online healthcare app, to book an urgent virtual consultation with a specialist, order prescribed medications, and decide whether she wants her health trends analyzed for wellness perks.

---

#### Data Principal - Priya Sharma *(Patient)*

- **Reviews segregated notices:** Reads distinct requests for medical consultation, prescription home delivery, and optional predictive health tips.
- **Gives granular consent:** Approves processing for her consult and prescription delivery, while explicitly ticking an opt-in box for weekly health insights.
- **Exercises control:** Later opens her privacy settings to withdraw permission for marketing and health trend profiling.
- **Raises issues:** Files a complaint via the portal when she mistakenly receives an unrequested promotional SMS after revoking consent.

#### Data Fiduciary - CarePulse Telehealth *(The Healthcare Platform)*

- **Defines purposes and data needs:** Determines why data is required (e.g., vitals for consultation vs. address for courier delivery).
- **Captures & audits consent:** Employs the WSO2 DPDP Accelerator to record Priya's affirmative choices and record consent status and snapshot audit history.
- **Coordinates the ecosystem:** Communicates relevant consent changes to downstream consumers.
- **Maintains accountability:** Ensures legal basis compliance and oversees internal grievance resolution.

#### Data Processor - MedExpress *(Courier Partner)* & CloudEngage *(Marketing Vendor)*

- **Executes under contract:** Processes Priya's delivery address (MedExpress) or email/phone (CloudEngage) strictly under CarePulse's instructions.
- **Consumes lifecycle events:** CloudEngage responds to consent changes and updates its active campaign pipeline.

#### Grievance Officer / DPO - CarePulse Compliance Desk *(Internal Team)*

- **Investigates concerns:** Receives Priya's grievance ticket regarding the rogue SMS via the DPDP grievance portal.
- **Reviews audit history:** Checks the consent timeline and confirms that CloudEngage had a delivery lag when processing the revocation event.
- **Resolves & intimates:** Closes the gap with the vendor, updates the ticket, and delivers an official resolution within mandated statutory timelines.

#### Data Protection Board of India (DPBI) - Statutory Regulatory Body

- **Serves as final arbiter:** Available as an escalation authority if Priya finds CarePulse's grievance response unsatisfactory or unfulfilled.
- **Enforces accountability:** Evaluates systemic non-compliance or data breaches reported by either Principals or the Fiduciary.

## WSO2 Identity Server and the WSO2 DPDP Accelerator

WSO2 Identity Server provides the identity, authentication, authorization, and
core consent foundation, including APIs for purposes, data elements, and consent
records. See [Identity Server consent management](https://is.docs.wso2.com/en/latest/guides/consent-management/).
The WSO2 DPDP Accelerator extends that foundation with
DPDP-focused consent management, audit, grievance handling, and lifecycle event
capabilities that help organizations operationalize DPDP compliance.

![WSO2 Identity Server and DPDP Accelerator stack](../assets/images/diagrams/dpdp-accelerator-stack.svg)

## How the WSO2 DPDP Accelerator helps

The accelerator runs with WSO2 Identity Server and provides a tenant-aware
Consent Portal plus supporting services.

![WSO2 DPDP Accelerator solution overview connecting portal users, Identity Server capabilities, DPDP services, data stores, and downstream systems](../assets/images/diagrams/dpdp-accelerator-overview.svg)

| Operational need | Accelerator capability |
|---|---|
| Define why data is requested | Administrators maintain a reusable list of purposes and data elements, called the catalog, to describe what data is requested and why. |
| Capture and manage consent | Data Principals can view and manage their own consents, while authorized administrators can manage tenant-wide consent records. |
| Demonstrate consent history | Status audit and snapshot history preserve the recorded evolution of a consent for authorized review. |
| Provide a grievance channel | Data Principals can submit and track complaints; DPO and administrator views support assignment, messages, attachments, status changes, and due-date tracking. |
| Propagate lifecycle changes | Event Notification records consent and user lifecycle events and supports webhook or polling subscriptions for downstream consumers. |
| Separate responsibilities | Tenant roles distinguish personal history and complaint self-service, complaint handling, and broader portal administration. Dedicated integration roles can be created with narrower scopes. |
| Support accessible notices | The portal interface supports English and the languages listed in the Eighth Schedule to the Constitution; purpose and data-element descriptions can be localized separately. |

The accelerator also publishes lifecycle events for configured downstream
systems, which can receive them through webhook or polling subscriptions.

## Start using the accelerator

- [Quickstart](quickstart.md): install and try the solution locally with the
  default embedded H2 databases, then verify portal access.
- [Install and Set Up](install-and-setup/prerequisites.md): configure external
  databases, JDBC drivers, datasources, schemas, and migrations before
  starting the server.
- [Configuration Guide](install-and-setup/configuring-the-accelerator.md): configure provisioning, roles,
  email, and optional runtime features after installation.

## Official references

- [The Digital Personal Data Protection Act, 2023 - India Code](https://www.indiacode.nic.in/indiacode/handle/123456789/22037)
- [DPDP Act commencement notification, 13 November 2025 - Ministry of Electronics and Information Technology](https://www.meity.gov.in/static/uploads/2025/11/c56ceae6c383460ca69577428d36828b.pdf)
