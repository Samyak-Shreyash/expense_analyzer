# Expense Analyzer — Non-Goals

**Document ID:** PROD-004  
**Version:** 0.1  
**Status:** Draft  
**Depends on:** `PRD.md`, `MVP-REQUIREMENTS.md`

## 1. Purpose

This document explicitly defines functionality that is **not part of the initial MVP**.

These items may be valuable in later releases, but they must not become hidden MVP dependencies or cause premature architectural complexity.

---

## 2. Banking Integrations

The MVP will **not** require live financial-institution connectivity.

Deferred:

- Direct bank API integrations.
- Direct credit-card API integrations.
- Account Aggregator integration.
- Real-time bank-account synchronization.
- Real-time UPI transaction feeds.
- Automatic statement retrieval from financial institutions.
- Automatic transaction synchronization.

The MVP uses **user-provided CSV and supported PDF statements** as its primary ingestion mechanism.

---

## 3. Real-Time Financial Data

The MVP is not a real-time personal-finance platform.

Deferred:

- Real-time transaction notifications.
- Real-time UPI monitoring.
- Real-time account balances from banks.
- Streaming transaction feeds.
- Intraday financial monitoring.

The architecture may support asynchronous processing, but asynchronous statement processing should not be confused with real-time banking connectivity.

---

## 4. Investment Management

The MVP will recognize investment-related transactions where useful for categorization and analytics, but it will not become an investment-management platform.

Deferred:

- Portfolio management.
- Broker integrations.
- Demat-account integrations.
- Live holdings synchronization.
- Trade execution.
- Buy/sell recommendations requiring live portfolio data.
- Portfolio optimization.
- Automated investment transactions.
- Detailed securities-level performance analytics.

---

## 5. Payments and Financial Transactions

Expense Analyzer analyzes financial activity; it does not execute financial transactions.

Deferred:

- Making payments.
- UPI payment initiation.
- Bank transfers.
- Bill payments.
- Credit-card payments.
- Fund transfers.
- Money movement.
- Financial product purchase/execution.

---

## 6. Advanced AI and Machine Learning

The core financial system must work without an LLM.

Deferred:

- Fully autonomous financial agents.
- Autonomous financial decision-making.
- Arbitrary LLM access to the production database.
- LLM-generated financial calculations as the source of truth.
- Complex autonomous workflows involving money movement.
- Advanced personalized ML pipelines.
- Feature stores.
- Dedicated model-training infrastructure.
- Large-scale model experimentation platforms.
- Advanced predictive ML as an MVP prerequisite.

AI may be introduced as a controlled explanation and natural-language interface over verified backend analytics.

---

## 7. Universal Document Support

The MVP will support a deliberately limited set of document formats and layouts.

Deferred:

- Guaranteed support for every Indian bank statement.
- Guaranteed support for every credit-card statement.
- Universal PDF-layout recognition.
- Universal spreadsheet-format recognition.
- Arbitrary financial documents.
- Guaranteed extraction from corrupted or malformed documents.
- Full support for every scanned/image-only statement.

### OCR

OCR for scanned PDFs is deferred unless it can be introduced without materially increasing MVP complexity or reducing reliability.

The initial PDF target is **text-based/structured PDFs**.

---

## 8. Advanced Categorization

The MVP begins with deterministic categorization and merchant-normalization rules.

Deferred:

- Fully autonomous ML categorization.
- User-specific trained classification models.
- Complex semantic classification pipelines.
- Continual online learning.
- Large-scale model-training infrastructure.
- Guaranteed 100% categorization accuracy for ambiguous transactions.

The system should preserve confidence and allow user review/correction where classification is uncertain.

---

## 9. Advanced Financial Planning

The MVP focuses on understanding historical and currently available transaction data.

Deferred:

- Full financial planning.
- Retirement planning.
- Tax planning.
- Automated tax filing.
- Estate planning.
- Insurance planning/advisory.
- Goal-based financial planning.
- Net-worth planning beyond what can be reliably derived from supported data.
- Personalized regulated financial advice.

---

## 10. Business Accounting

The initial product is designed for personal finance.

Deferred:

- Business accounting.
- Invoicing.
- Accounts payable/receivable.
- Payroll.
- General ledger workflows.
- Corporate expense management.
- GST accounting.
- Business tax workflows.
- Multi-company accounting.

---

## 11. Household and Multi-User Finance

The initial MVP is centered on an individual user and their owned financial data.

Deferred unless explicitly added to product scope:

- Shared household financial workspaces.
- Spouse/partner accounts.
- Family financial hierarchies.
- Delegated financial access.
- Complex multi-user permissions.
- Family budgets.

The data model should avoid making future extension impossible, but these capabilities are not MVP requirements.

---

## 12. Advanced Notifications

The MVP does not require a sophisticated notification platform.

Deferred:

- Complex notification journeys.
- Personalized notification schedules.
- Multi-channel campaign management.
- Marketing automation.
- Push-notification infrastructure.
- SMS automation.
- WhatsApp automation.
- Advanced alert rules.

Basic notification capabilities may be introduced later where they directly support a validated product workflow.

---

## 13. Large-Scale Search and Analytics Infrastructure

Do not introduce specialized infrastructure without a measured requirement.

Deferred as MVP dependencies:

- OpenSearch/Elasticsearch.
- Dedicated analytical databases.
- ClickHouse.
- BigQuery.
- Snowflake.
- Data warehouses.
- Data lakes.
- Feature stores.

PostgreSQL remains the initial system of record and should be used for analytics until measured scale or workload characteristics justify another system.

---

## 14. Full Microservice Architecture

The MVP will **not** require decomposing the platform into many independently deployed services.

Deferred:

- Full microservice decomposition.
- Service mesh.
- Distributed configuration platforms.
- Complex inter-service orchestration.
- Independent deployment pipelines for every domain.
- Cross-region service architecture.

The initial architecture is a **modular monolith with asynchronous workers**. Individual components may later be extracted when there is a demonstrated scaling, reliability, deployment, ownership, or security reason.

---

## 15. Kubernetes and Multi-Region Infrastructure

Deferred:

- Kubernetes as an MVP requirement.
- Service mesh.
- Multi-region deployment.
- Active-active architecture.
- Global traffic management.
- Complex disaster-recovery topology.
- Multi-cloud deployment.

Production infrastructure should still meet appropriate security, backup, recovery, and availability requirements for the MVP deployment environment.

---

## 16. Native Mobile Applications

A native iOS/Android application is not required to validate the initial backend and product workflow.

Deferred:

- Native iOS application.
- Native Android application.
- Mobile-specific offline synchronization.
- Mobile push-notification infrastructure.

A responsive web experience can support the initial product validation.

---

## 17. Advanced Budgeting

Basic analytics may inform spending awareness, but a complete budgeting system is outside MVP scope.

Deferred:

- Envelope budgeting.
- Complex budget hierarchies.
- Shared budgets.
- Budget forecasting engines.
- Automated budget allocation.
- Budget optimization.
- Advanced budget alerts.

---

## 18. Advanced Forecasting

The MVP may provide basic deterministic cash-flow analysis where supported by available data.

Deferred:

- Long-horizon financial forecasting.
- Probabilistic financial simulations.
- Monte Carlo financial planning.
- Sophisticated income prediction.
- Complex future-liability modeling.
- AI-generated forecasts without deterministic supporting calculations.

---

## 19. Advanced Data Science Platform

The MVP does not require a dedicated data-science platform.

Deferred:

- Feature engineering platforms.
- Model registries.
- Training pipelines.
- Experiment tracking platforms.
- Feature stores.
- Large-scale batch ML infrastructure.
- Dedicated data lake architecture.

These may become relevant after sufficient product usage generates a meaningful training/evaluation dataset.

---

## 20. Scope-Control Rules

The following rules apply to MVP planning:

1. **Do not add a technology solely because it may be useful at future scale.**
2. **Do not make bank integrations a prerequisite for statement ingestion.**
3. **Do not make an LLM a prerequisite for core financial correctness.**
4. **Do not replace deterministic financial calculations with AI-generated numbers.**
5. **Do not introduce microservices before there is a concrete reason to extract a module.**
6. **Do not introduce specialized databases before PostgreSQL limitations are measured.**
7. **Do not expand document support until supported formats are reliable.**
8. **Do not expand product scope merely because a capability is technically interesting.**
9. **Every deferred capability must have a clear product or technical trigger before moving into scope.**

---

## 21. Graduation Criteria

A non-goal can move into the roadmap when at least one of the following is demonstrated:

- A validated user need exists.
- MVP metrics show a meaningful product gap.
- Current architecture has a measured performance/scalability limitation.
- Reliability requirements cannot reasonably be met with the existing design.
- A business/compliance requirement makes it necessary.
- Sufficient data exists to justify an advanced ML/AI capability.
- The capability has a clearly defined owner, acceptance criteria, and implementation plan.

When a non-goal becomes active scope, update:

- `PRD.md`
- `MVP-REQUIREMENTS.md` or the relevant release requirements
- `HLD.md`
- `DOMAIN-MODEL.md`
- `API-CONTRACT.md`
- `EVENT-MODEL.md`
- `BACKLOG.md`

before implementation begins.
