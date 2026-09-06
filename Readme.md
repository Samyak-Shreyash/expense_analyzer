# Expense Analyzer

> **Understand your money. Not just track it.**

Expense Analyzer is an AI-powered personal finance analysis platform focused initially on **Indian users**.

Unlike traditional expense trackers that primarily answer *“How much did I spend?”*, Expense Analyzer aims to answer:

> **“Where is my money going, why is it changing, and what should I do about it?”**

The platform ingests financial statements, extracts and normalizes transactions, analyzes spending behavior, detects recurring expenses and anomalies, and eventually provides a natural-language interface for querying personal financial data.

---

## 🚧 Project Status

**Status:** Planning / Architecture

This project is currently in the product-definition and architecture phase.

The initial goal is to build a production-quality MVP using uploaded financial statements before introducing direct financial-institution integrations.

---

# 1. Vision

The long-term vision is to build a **personal AI financial analyst**.

A user should be able to provide several months of financial data and receive insights such as:

> Your spending increased 18% this month, primarily because of shopping and food delivery.

> You have 7 recurring subscriptions costing approximately ₹2,140/month.

> Your spending is consistently higher during the last week of the month.

> You spent ₹42,600 on your car during the last 12 months.

> Reducing dining expenses by ₹3,000/month would save approximately ₹36,000/year.

The system should combine **deterministic financial calculations** with **AI-powered interpretation**.

The AI should explain verified financial information rather than inventing financial facts.

---

# 2. Goals

## Primary Goals

* Import financial statements from multiple sources.
* Extract transactions reliably.
* Preserve original transaction information.
* Normalize merchants.
* Automatically categorize transactions.
* Detect recurring expenses.
* Analyze spending patterns.
* Detect unusual spending.
* Compare current spending with historical behavior.
* Provide useful financial insights.
* Support natural-language financial queries.
* Maintain strong privacy and security.
* Build an architecture capable of evolving toward larger scale.

---

# 3. Non-Goals for the Initial MVP

The first version will **not** attempt to solve everything.

The following are intentionally deferred:

* Direct integration with every Indian bank.
* Real-time bank transaction synchronization.
* Investment portfolio management.
* Tax filing.
* Loan management.
* Insurance management.
* Automated financial transactions.
* Payments.
* Financial advice requiring regulated advisory capabilities.
* Internet-scale infrastructure.

These may be considered after the core expense-analysis product is validated.

---

# 4. Target Users

The initial target audience is individuals who have financial data spread across:

* Bank accounts
* Credit cards
* Debit cards
* UPI
* Cash
* Multiple financial institutions

The product is particularly useful for users who want to understand their spending but don't want to manually enter every transaction.

---

# 5. India-First Considerations

The initial implementation will focus on transaction formats and financial behavior common in India.

Examples include:

* UPI transactions
* Indian bank statements
* Credit-card statements
* Debit-card transactions
* EMIs
* SIPs
* Insurance premiums
* Rent
* Utility payments
* Fuel
* Subscriptions
* Merchant-specific transaction formats

Example merchant descriptions:

```text
SWIGGY*ONLINE
SWIGGY INSTAMART
SWIGGY FOOD
```

The system should be capable of associating these transactions with an appropriate merchant hierarchy while preserving their original descriptions.

---

# 6. Core User Journey

```text
                    ┌───────────────────┐
                    │      Upload       │
                    │ CSV / PDF / XLSX  │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │     Extract       │
                    │   Transactions    │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │    Normalize      │
                    │     Merchant      │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │    Categorize     │
                    │   Transactions    │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │     Analyze       │
                    │ Spending Patterns │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │     Insights      │
                    │ + Recommendations │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │    AI Assistant   │
                    │ Ask About Money   │
                    └───────────────────┘
```

---

# 7. Example Questions

Eventually users should be able to ask:

```text
How much did I spend on Swiggy this year?

Why did I spend more this month?

What are my top 5 spending categories?

How much do I spend on subscriptions?

Which subscriptions do I have?

What were my unusual expenses this month?

How much did I spend on my car last year?

How much do I spend on food every month?

What changed between this month and last month?

If I reduce dining by ₹3,000 per month, how much will I save annually?
```

---

# 8. High-Level Architecture

```text
 ┌──────────────────────────────────────────────────────────┐
 │                        CLIENTS                           │
 │                                                          │
 │       Web App          Mobile App          Chat           │
 └────────────────────────────┬─────────────────────────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │    CDN / WAF      │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │    API Gateway    │
                    └─────────┬─────────┘
                              │
            ┌─────────────────┼─────────────────┐
            │                 │                 │
            ▼                 ▼                 ▼
     User Service     Statement Service   Transaction Service
            │                 │                 │
            │                 ▼                 │
            │          Event / Message Bus      │
            │                 │                 │
            │       ┌─────────┴──────────┐      │
            │       ▼                    ▼      │
            │ Document Worker      Enrichment Worker
            │       │                    │
            │       └─────────┬──────────┘
            │                 ▼
            │        Analytics Worker
            │                 │
            └─────────────────┼─────────────────
                              │
                              ▼
                      Analysis Service
                              │
                              ▼
                       AI / LLM Service
                              │
                              ▼
                      Notification Service


 ┌──────────────────────────────────────────────────────────┐
 │                       DATA LAYER                          │
 │                                                          │
 │ PostgreSQL │ Redis │ Object Storage │ Search │ Analytics │
 └──────────────────────────────────────────────────────────┘
```

The architecture is intentionally evolutionary.

The MVP does **not** need to deploy every component as an independent microservice.

---

# 9. Major Components

## User Service

Responsible for:

* Authentication
* User profile
* Preferences
* Authorization
* Account configuration

---

## Statement Service

Responsible for:

* Statement uploads
* File metadata
* Processing status
* Duplicate detection
* Document lifecycle
* Object-storage references

Long-running document processing should happen asynchronously.

---

## Document Processing

Responsible for:

* PDF parsing
* CSV parsing
* Excel parsing
* OCR where required
* Transaction extraction
* Data validation

---

## Transaction Service

Responsible for:

* Transaction persistence
* Transaction retrieval
* Transaction updates
* Merchant association
* Category association
* Transaction search

---

## Enrichment

Responsible for:

* Merchant normalization
* Merchant identification
* Transaction categorization
* Confidence scoring
* Recurring transaction detection

---

## Analysis Service

Responsible for deterministic financial calculations:

* Spending summaries
* Category analysis
* Merchant analysis
* Trends
* Historical comparisons
* Recurring expenses
* Anomaly detection
* Cash-flow analysis

The Analysis Service is the **source of truth for financial calculations**.

---

## AI / LLM Service

Responsible for:

* Natural-language queries
* Query interpretation
* Financial-context retrieval
* Insight explanation
* Summaries
* Recommendations
* Conversational interaction

The AI layer should not independently calculate financial facts when those facts can be obtained from structured data.

Preferred flow:

```text
User Question
      │
      ▼
AI / Intent Detection
      │
      ▼
Structured Tool / Query
      │
      ▼
Analysis Service
      │
      ▼
Verified Financial Data
      │
      ▼
LLM
      │
      ▼
Natural Language Response
```

---

## Notification Service

Responsible for:

* Email
* Push notifications
* Processing updates
* Spending alerts
* Recurring-expense alerts
* Scheduled reports

---

# 10. Data Architecture

The relational database should remain the **source of truth for transactional financial data**.

Initial domain entities:

```text
User
Account
Statement
Transaction
Merchant
Category
RecurringExpense
Budget
Insight
Notification
AIQuery
AuditLog
```

Conceptually:

```text
User
 │
 ├── Account
 │     └── Transaction
 │
 ├── Statement
 │
 ├── Budget
 │
 ├── Insight
 │
 └── AIQuery
```

A transaction should preserve both raw and normalized information.

Example:

```text
raw_description = "SWIGGY*ONLINE"

merchant_id      = "swiggy"
category_id      = "food_delivery"
amount           = 850.00
currency         = "INR"
transaction_date = ...
confidence_score = 0.97
```

The original transaction description must remain recoverable.

---

# 11. Storage

## Relational Database

Initial candidates:

* PostgreSQL
* MySQL

Used for:

* Users
* Accounts
* Statements
* Transactions
* Merchants
* Categories
* Recurring expenses
* Insights
* Configuration

---

## Object Storage

Initial candidates:

* AWS S3
* MinIO

Used for:

* Uploaded PDFs
* CSV files
* Excel files
* Generated reports

Large files should not be stored directly inside the relational database.

---

## Redis

Potential uses:

* Caching
* Idempotency
* Rate limiting
* Frequently requested analytics
* Distributed locks where necessary

Redis should only be introduced where it provides a clear benefit.

---

## Search

Potential future options:

* OpenSearch
* Elasticsearch

Potential uses:

* Transaction search
* Merchant search
* Full-text search

The MVP should not introduce a search engine unless the requirements justify it.

---

## Analytics Database

Potential future options:

* ClickHouse
* BigQuery
* Snowflake

This should only be introduced when analytical workloads justify a separate data store.

---

# 12. Event-Driven Processing

Long-running workflows should be asynchronous.

Potential event flow:

```text
StatementUploaded
       │
       ▼
DocumentProcessingRequested
       │
       ▼
TransactionsExtracted
       │
       ▼
TransactionsEnriched
       │
       ▼
AnalyticsRequested
       │
       ▼
AnalysisCompleted
       │
       ▼
InsightsGenerated
       │
       ▼
NotificationRequested
```

Potential messaging technologies:

* Kafka
* RabbitMQ

The choice will be made during architecture evaluation.

Event processing must account for:

* Idempotency
* Retries
* Dead-letter queues
* Duplicate events
* Ordering
* Consumer failures
* Observability

---

# 13. Security & Privacy

Financial data is highly sensitive.

Security is a core product requirement rather than an afterthought.

## Authentication

Potentially:

* OAuth2
* OpenID Connect
* JWT where appropriate

## Authorization

Every financial-data operation must enforce user ownership.

A user must never be able to access another user's:

* Statements
* Transactions
* Accounts
* Insights
* AI query data

## Encryption

Protect data:

* In transit
* At rest

## Audit Logging

Audit important operations such as:

* Login
* Statement upload
* Statement access
* Transaction modification
* Financial data export
* AI queries involving financial data

## Sensitive Data

Never log:

* Passwords
* Access tokens
* Complete financial statements
* Unnecessary PII
* Sensitive financial information

---

# 14. AI Security

The LLM should not have unrestricted access to financial data.

Use:

```text
User
 ↓
Authentication
 ↓
Authorization
 ↓
Intent Detection
 ↓
Validated Tool Call
 ↓
Financial Data
 ↓
LLM
```

Consider protection against:

* Prompt injection
* Data exfiltration
* Unauthorized tool calls
* Cross-user data leakage
* Excessive data exposure
* Malicious instructions embedded in uploaded documents

Only the minimum required financial context should be provided to an external LLM.

---

# 15. Reliability

The system should gracefully handle:

```text
Database unavailable
Kafka unavailable
Redis unavailable
Object storage unavailable
PDF parser failure
Malformed statement
Worker crash
Duplicate event
Duplicate statement
LLM timeout
LLM unavailable
Network timeout
```

Relevant patterns include:

* Timeouts
* Retries
* Exponential backoff
* Circuit breakers
* Bulkheads
* Idempotency
* Dead-letter queues
* Graceful degradation
* Health checks

---

# 16. Observability

The project should eventually provide production-grade observability.

## Metrics

Potential stack:

```text
Prometheus
    +
Grafana
```

Track:

* Request rate
* Latency
* Error rate
* Queue depth
* Document processing time
* Transaction throughput
* LLM latency
* LLM error rate
* Database connection pool
* Cache hit rate

## Logging

Potential stack:

```text
ELK / OpenSearch
```

Use structured logging and correlation IDs.

## Tracing

Potential stack:

```text
OpenTelemetry
```

Example trace:

```text
API Request
    ↓
Statement Service
    ↓
Kafka
    ↓
Document Worker
    ↓
Transaction Service
    ↓
Analytics
```

---

# 17. Technology Direction

The initial backend will primarily use:

```text
Java
Spring Boot
Spring Security
Spring Data JPA
PostgreSQL
Docker
```

Potential supporting technologies:

```text
Redis
Kafka / RabbitMQ
MinIO / S3
OpenSearch
Prometheus
Grafana
OpenTelemetry
Kubernetes
```

Frontend:

```text
React / Next.js
```

The exact technology choices are subject to architectural evaluation.

---

# 18. Development Strategy

The project should **not start as a collection of dozens of microservices**.

The preferred approach is evolutionary:

```text
Phase 1
Modular MVP
       ↓
Phase 2
Asynchronous Processing
       ↓
Phase 3
AI Analysis
       ↓
Phase 4
Scale Individual Components
       ↓
Phase 5
Extract Services Where Justified
       ↓
Phase 6
Production Hardening
```

The rule is:

> **Build the simplest architecture that can evolve into the required architecture.**

Every technology introduced should solve a real problem.

---

# 19. Initial Development Phases

## Phase 0 — Product Definition

* Product requirements
* Personas
* Use cases
* MVP scope
* Non-goals
* Success metrics

## Phase 1 — Architecture

* HLD
* LLD
* API contracts
* Database model
* Event model
* Security model
* Technology decisions

## Phase 2 — Foundation

* Repository setup
* Spring Boot
* Docker Compose
* Database
* Database migrations
* Authentication
* CI/CD

## Phase 3 — Statement Processing

* File upload
* Object storage
* CSV parser
* PDF parser
* Transaction extraction
* Processing status
* Async processing

## Phase 4 — Transaction Intelligence

* Merchant normalization
* Category system
* Categorization
* Recurring expenses
* Duplicate detection

## Phase 5 — Analytics

* Spending summaries
* Trends
* Category analytics
* Merchant analytics
* Anomaly detection
* Cash-flow analysis

## Phase 6 — AI

* Natural-language queries
* Tool calling
* Financial-context retrieval
* Insight generation
* Recommendations
* Guardrails

## Phase 7 — UI

* Dashboard
* Upload interface
* Transaction explorer
* Insights
* AI chat

## Phase 8 — Production Hardening

* Load testing
* Resilience testing
* Security testing
* Performance optimization
* Observability
* Disaster recovery

## Phase 9 — Deployment

* Cloud architecture
* Infrastructure
* CI/CD
* Production deployment
* Monitoring
* Alerts

---

# 20. Testing Strategy

Testing will be performed at multiple levels.

### Unit Tests

For:

* Domain logic
* Calculations
* Categorization
* Parsers
* Validation

### Integration Tests

For:

* Database
* Messaging
* Object storage
* APIs

### Contract Tests

For:

* Service APIs
* Event schemas

### End-to-End Tests

Example:

```text
Upload Statement
       ↓
Process
       ↓
Extract Transactions
       ↓
Categorize
       ↓
Generate Analytics
       ↓
View Dashboard
```

### Resilience Tests

Test:

* Database failures
* Messaging failures
* Worker crashes
* Duplicate events
* LLM timeout
* Object storage failure

### Performance Tests

Eventually test:

* Concurrent uploads
* Transaction ingestion
* Analytics latency
* AI query latency

---

# 21. Repository Structure

The exact structure will be finalized during architecture planning.

A potential starting point:

```text
expense-analyzer/
│
├── backend/
│   ├── src/
│   ├── build.gradle
│   └── Dockerfile
│
├── frontend/
│
├── infrastructure/
│   ├── docker/
│   ├── nginx/
│   └── observability/
│
├── docs/
│   ├── architecture/
│   ├── api/
│   ├── database/
│   └── decisions/
│
├── scripts/
│
├── docker-compose.yml
│
├── README.md
└── .gitignore
```

This structure is intentionally provisional.

---

# 22. Architecture Decision Records

Important architectural decisions should be documented using ADRs.

Examples:

```text
ADR-001 Modular Monolith vs Microservices
ADR-002 PostgreSQL vs MySQL
ADR-003 Kafka vs RabbitMQ
ADR-004 PDF Processing Strategy
ADR-005 Transaction Categorization Approach
ADR-006 LLM Provider Strategy
ADR-007 Object Storage Strategy
ADR-008 Authentication Architecture
```

Each ADR should document:

```text
Context
Decision
Alternatives
Trade-offs
Consequences
```

---

# 23. Definition of Done

A feature is not considered complete merely because it compiles.

A typical Definition of Done includes:

* Implementation complete
* Unit tests
* Integration tests where appropriate
* Input validation
* Error handling
* Logging
* Metrics where appropriate
* Documentation
* Security review
* Code quality checks
* Local verification

For significant features also consider:

* Performance
* Concurrency
* Failure scenarios
* Backward compatibility

---

# 24. Project Management

The project will use **GitHub Projects** for project tracking.

Issues should contain:

```text
ID
Epic
Title
Description
Priority
Dependencies
Acceptance Criteria
Technical Notes
Status
```

Priorities:

```text
P0 — Critical
P1 — High
P2 — Medium
P3 — Low
```

Statuses:

```text
BACKLOG
READY
IN_PROGRESS
BLOCKED
IN_REVIEW
DONE
```

Tasks should be small enough to complete during a focused development session.

For example, avoid:

```text
Build Transaction Service
```

Prefer:

```text
TXN-001 Create Transaction entity
TXN-002 Create database migration
TXN-003 Implement repository
TXN-004 Implement transaction creation API
TXN-005 Add validation
TXN-006 Add integration tests
TXN-007 Implement transaction search
```

---

# 25. Engineering Principles

### 1. Correctness over complexity

Financial calculations must be correct.

### 2. Data is the source of truth

The database and deterministic analysis layer are authoritative.

### 3. AI explains; it does not invent

LLMs should operate on verified data.

### 4. Privacy by design

Minimize exposure of financial information.

### 5. Async where appropriate

Long-running workloads should not block API requests.

### 6. Idempotency everywhere it matters

Retries must not create duplicate financial records.

### 7. Observable systems

If we cannot understand what the system is doing, it is not production-ready.

### 8. Incremental architecture

Do not introduce infrastructure before it is justified.

### 9. Test failure paths

A production system must be designed for failure.

### 10. Avoid premature optimization

Scale based on measured requirements.

---

# 26. Future Possibilities

Once the core product is stable, potential extensions include:

* Bank integrations
* Account Aggregator integration
* Credit-card integrations
* UPI integrations
* Investment tracking
* Budget recommendations
* Financial goal tracking
* Automated monthly reports
* Personalized spending forecasts
* Advanced ML categorization
* Household/family finance
* Multi-currency support
* Mobile applications
* Offline/local financial analysis
* Privacy-preserving local LLM inference

These are **future possibilities, not MVP commitments**.

---

# 27. Success Criteria

The project should eventually demonstrate that a user can:

1. Upload a financial statement.
2. Have transactions extracted accurately.
3. Have transactions normalized and categorized.
4. View meaningful spending analytics.
5. Identify recurring expenses.
6. Identify unusual spending.
7. Compare spending across periods.
8. Ask natural-language questions about their finances.
9. Receive answers based on verified transaction data.
10. Trust that their financial data is properly protected.

---

# 28. Current Priority

The immediate priority is **not implementation**.

The first objective is to establish:

```text
Product Requirements
        ↓
MVP Scope
        ↓
Architecture
        ↓
Domain Model
        ↓
API Design
        ↓
Database Design
        ↓
Event Model
        ↓
Security Model
        ↓
Development Backlog
        ↓
Implementation
```

---

# 29. Project Philosophy

This project is being built as both a **useful product** and a **serious software-engineering project**.

The implementation should demonstrate practical knowledge of:

* Backend engineering
* System design
* API design
* Database design
* Asynchronous processing
* Distributed systems
* Event-driven architecture
* Resilience
* Security
* Observability
* AI/LLM integration
* Testing
* CI/CD
* Cloud infrastructure

However, technology should never be added merely for the sake of demonstrating it.

> **The goal is not to build the most complicated system.**
>
> **The goal is to build the simplest system that solves the problem correctly and can evolve as the product grows.**

---

## License

License to be determined.

---

## Project Status

🚧 **Architecture & Planning**

More documentation will be added as architectural and product decisions are finalized.
