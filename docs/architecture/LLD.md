# Expense Analyzer — Low-Level Design

**Document ID:** ARCH-004  
**Version:** 0.1  
**Status:** Draft  
**Depends on:** `PRD.md`, `MVP-REQUIREMENTS.md`, `HLD.md`, `DOMAIN-MODEL.md`, `API-CONTRACT.md`, `EVENT-MODEL.md`

## 1. Purpose

This document translates the high-level architecture into implementation-level design for the initial production-oriented MVP.

The design prioritizes:

- financial correctness
- user-data isolation
- deterministic calculations
- idempotent asynchronous processing
- testability
- clear module boundaries
- simple infrastructure
- future extraction of independently scalable components

The initial implementation is a **Spring Boot modular monolith** with asynchronous workers/jobs.

---

# 2. Technology Baseline

| Area | Initial Choice |
|---|---|
| Application | Java + Spring Boot |
| API | Spring Web / REST |
| Persistence | PostgreSQL |
| ORM/data access | Spring Data JPA / Hibernate |
| Database migrations | Flyway |
| Object storage | S3-compatible abstraction; MinIO locally |
| Async processing | Broker/job abstraction; RabbitMQ is a reasonable early implementation |
| Cache | Redis only when justified |
| Authentication | Token/session mechanism behind an auth abstraction |
| Observability | Structured logs + metrics + tracing |
| Testing | JUnit + Spring integration tests + Testcontainers where useful |
| Build | Maven or Gradle |
| Deployment | Containerized application |

The exact library versions are implementation decisions and should be pinned in the build configuration rather than this document.

---

# 3. Package / Module Structure

Recommended structure:

```text
com.expenseanalyzer
├── common
│   ├── error
│   ├── security
│   ├── idempotency
│   ├── audit
│   ├── events
│   └── observability
│
├── user
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
│
├── account
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
│
├── statement
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
│
├── transaction
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
│
├── enrichment
│   ├── application
│   ├── merchant
│   ├── categorization
│   └── infrastructure
│
├── analytics
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
│
├── insight
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
│
├── ai
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
│
└── notification
    ├── application
    ├── domain
    └── infrastructure
```

## 3.1 Dependency Rule

Prefer:

```text
API
 ↓
Application
 ↓
Domain
 ↓
Infrastructure adapters
```

Domain code must not depend directly on HTTP, database, broker, or external LLM implementations.

Cross-module access should happen through application services or explicit interfaces rather than reaching directly into another module's repositories.

---

# 4. Common Infrastructure

## 4.1 IDs

Use stable opaque identifiers for external resources.

Example:

```text
usr_...
acct_...
stmt_...
txn_...
mrc_...
cat_...
ins_...
```

The exact ID implementation can be UUID/ULID or another collision-resistant identifier.

## 4.2 Timestamps

Store timestamps in UTC.

Financial transaction dates should retain the statement's financial date separately from system timestamps.

Recommended fields:

```text
transaction_date   DATE
posting_date       DATE NULL
created_at         TIMESTAMPTZ
updated_at         TIMESTAMPTZ
```

## 4.3 Money

Never use floating-point types for financial amounts.

Use:

```text
DECIMAL / NUMERIC
```

in PostgreSQL and:

```java
BigDecimal
```

in Java.

Currency should be stored explicitly, initially with INR as the primary supported currency.

---

# 5. Database Design

## 5.1 users

```sql
id
email
name
status
created_at
updated_at
```

Constraints:

- unique normalized email
- valid status
- non-null timestamps

Indexes:

```text
unique(email_normalized)
```

## 5.2 accounts

```sql
id
user_id
type
institution_name
masked_identifier
currency
status
created_at
updated_at
```

Indexes:

```text
(user_id)
(user_id, type)
```

Foreign key:

```text
accounts.user_id -> users.id
```

## 5.3 statements

```sql
id
user_id
account_id
file_name
storage_key
file_hash
format
period_start
period_end
status
processing_error
created_at
completed_at
```

Indexes:

```text
(user_id, created_at)
(account_id, created_at)
(user_id, file_hash)
(status)
```

Recommended uniqueness:

```text
(user_id, file_hash)
```

if product semantics define identical files as duplicates within a user's data scope.

## 5.4 transactions

```sql
id
user_id
account_id
statement_id
transaction_date
posting_date
amount
currency
direction
raw_description
normalized_description
merchant_id
category_id
category_confidence
merchant_confidence
transaction_fingerprint
created_at
updated_at
```

Indexes:

```text
(user_id, transaction_date)
(account_id, transaction_date)
(statement_id)
(user_id, category_id, transaction_date)
(user_id, merchant_id, transaction_date)
(user_id, direction, transaction_date)
```

Transaction fingerprints should have a uniqueness strategy that matches the duplicate-ingestion rules.

## 5.5 merchants

```sql
id
canonical_name
parent_merchant_id
category_hint
created_at
updated_at
```

## 5.6 categories

```sql
id
parent_id
name
type
is_system_defined
```

Seed initial system categories.

## 5.7 recurring_expenses

```sql
id
user_id
merchant_id
category_id
expected_amount
frequency
confidence
last_occurrence
next_expected_date
status
created_at
updated_at
```

## 5.8 insights

```sql
id
user_id
type
title
description
severity
period_start
period_end
evidence
created_at
```

`evidence` should be structured JSON rather than an opaque free-form blob where practical.

## 5.9 ai_queries

```sql
id
user_id
question
intent
tool_calls
result_reference
response
created_at
```

Do not store unnecessary sensitive tool payloads.

## 5.10 audit_logs

```sql
id
user_id
action
resource_type
resource_id
timestamp
request_id
metadata
```

Avoid storing complete statements, credentials, access tokens, or unnecessary transaction payloads.

---

# 6. Transaction Processing Pipeline

## 6.1 End-to-End Flow

```text
POST /statements
       |
       v
Validate Upload
       |
       v
Create Statement
       |
       v
Store Original File
       |
       v
Create Outbox / Job
       |
       v
Document Worker
       |
       v
Parser
       |
       v
Extract Candidate Transactions
       |
       v
Validate
       |
       v
Fingerprint / Deduplicate
       |
       v
Persist Transactions
       |
       v
Merchant Normalization
       |
       v
Categorization
       |
       v
Recurring Detection
       |
       v
Anomaly Detection
       |
       v
Analytics
       |
       v
Insights
       |
       v
Statement COMPLETED
```

---

# 7. Statement State Machine

Recommended states:

```text
UPLOADED
   |
   v
QUEUED
   |
   v
PROCESSING
  /  v   v
FAILED COMPLETED
```

Retryable failures may transition:

```text
FAILED -> QUEUED
```

Non-retryable validation failures should remain failed until a user action or corrected upload occurs.

## 7.1 State Rules

- `UPLOADED`: metadata exists and original file is stored.
- `QUEUED`: processing work has been scheduled.
- `PROCESSING`: worker owns the current processing attempt.
- `COMPLETED`: expected processing stages succeeded.
- `FAILED`: processing cannot continue without retry or user intervention.

State transitions must be validated server-side.

---

# 8. Parser Design

## 8.1 Interface

Conceptually:

```java
interface StatementParser {
    boolean supports(StatementFormat format);
    ParsedStatement parse(InputStream input);
}
```

`ParsedStatement` should contain:

```text
statement metadata
candidate transactions
parser diagnostics
```

## 8.2 CSV Parser

Responsibilities:

- detect/validate expected headers
- handle configured delimiter
- normalize whitespace
- parse dates
- parse decimal amounts
- identify direction/sign semantics
- preserve original descriptions
- reject malformed records safely

Do not silently infer ambiguous financial values.

## 8.3 PDF Parser

Initial target:

- text-based/structured PDFs
- known supported layouts
- deterministic extraction rules

The parser should produce the same canonical `ParsedStatement` structure as CSV.

OCR is not part of the initial mandatory implementation.

---

# 9. Transaction Validation

Validation occurs before persistence.

Checks include:

- required transaction date exists
- amount is valid decimal
- currency is valid
- direction is recognized
- description is available where required
- statement/account/user ownership is valid
- transaction is not already ingested

Invalid rows should produce diagnostics.

Do not convert invalid financial data into plausible-looking values.

---

# 10. Transaction Fingerprinting

A fingerprint should be derived from stable normalized attributes, for example:

```text
user/account scope
+ transaction date
+ normalized amount
+ direction
+ normalized description
+ statement/source context where required
```

The exact algorithm must be documented and tested against:

- identical reprocessing
- duplicate file upload
- same transaction appearing in two statements
- legitimate repeated transactions
- refunds/reversals

A fingerprint is an anti-duplication mechanism, not a universal transaction identity.

---

# 11. Merchant Normalization

## 11.1 Pipeline

```text
raw_description
      |
      v
normalize case/whitespace
      |
      v
remove known bank/reference noise
      |
      v
apply merchant rules
      |
      v
canonical merchant
```

Example conceptual transformation:

```text
UPI/AMZN/ABC123/AMAZON PAY
        ->
Amazon
```

Rules must be versioned/tested because merchant normalization directly affects analytics.

## 11.2 Confidence

Store confidence when normalization is uncertain.

Do not fabricate a canonical merchant when evidence is insufficient.

---

# 12. Categorization

Initial categorization should be deterministic.

Conceptual rule:

```java
CategoryResult categorize(Transaction transaction,
                          Merchant merchant,
                          RuleContext context)
```

Priority can be:

```text
explicit user correction
    >
merchant-specific rule
    >
known description rule
    >
category hint
    >
Other
```

User corrections must be distinguishable from system-generated classifications.

---

# 13. Recurring Expense Detection

Recurring detection operates over a user's historical transaction set.

Inputs:

- normalized merchant
- category
- amount
- transaction dates
- direction

Outputs:

```text
frequency
expected amount
confidence
last occurrence
next expected date
status
```

The algorithm must tolerate small amount/date variations.

Possible initial frequencies:

```text
WEEKLY
BIWEEKLY
MONTHLY
QUARTERLY
YEARLY
UNKNOWN
```

Results are probabilistic signals and should not be represented as guaranteed future charges.

---

# 14. Anomaly Detection

MVP anomaly detection should favor explainability.

Candidate rules:

- transaction amount substantially exceeds user's normal range
- unusual merchant
- unusual category spend
- category spending materially exceeds comparison period
- unusual transaction frequency

Every anomaly should produce evidence such as:

```json
{
  "baselinePeriod": "2026-01",
  "comparisonPeriod": "2026-02",
  "currentAmount": 25000,
  "baselineAmount": 9000,
  "changePercent": 177.78
}
```

The actual thresholds should be configuration rather than magic constants in business logic.

---

# 15. Analytics Service

Analytics calculations must be deterministic.

Example service boundaries:

```text
SpendingSummaryService
CategoryAnalyticsService
MerchantAnalyticsService
TrendAnalyticsService
CashFlowAnalyticsService
PeriodComparisonService
RecurringAnalyticsService
AnomalyAnalyticsService
```

Example:

```java
SpendingSummary getSummary(
    UserId userId,
    LocalDate from,
    LocalDate to
);
```

Analytics services should query repositories through well-defined interfaces and return typed domain/application DTOs.

---

# 16. Financial Calculation Rules

Use backend code for:

- totals
- counts
- percentages
- period comparisons
- category aggregations
- merchant aggregations
- cash-flow calculations
- deterministic projections

Example:

```text
changePercent =
    ((current - previous) / previous) * 100
```

Handle zero denominators explicitly.

For example:

```text
previous = 0
current > 0
```

must not produce an invalid/infinite percentage in the API.

---

# 17. Insight Generation

Insights are generated from analytics, not raw LLM guesses.

Conceptual pipeline:

```text
Analytics
   |
   v
Insight Rules
   |
   v
Insight Candidate
   |
   v
Evidence
   |
   v
Persist Insight
```

An insight should contain:

- type
- title
- description
- severity
- period
- evidence

The description may later be generated/refined by AI, but its factual inputs remain deterministic.

---

# 18. API Layer

Controllers should be thin.

Example:

```java
@PostMapping("/statements")
StatementResponse upload(
    @AuthenticationPrincipal UserPrincipal user,
    @RequestPart MultipartFile file,
    @RequestParam String accountId
)
```

Controller responsibilities:

- parse request
- validate basic request shape
- invoke application service
- map response
- map exceptions to API errors

Controllers should not:

- perform financial calculations
- query arbitrary repositories
- contain categorization logic
- publish events directly without application coordination

---

# 19. Application Services

Examples:

```text
RegisterUserService
AuthenticateUserService
CreateAccountService
UploadStatementService
ProcessStatementService
GetTransactionsService
UpdateTransactionService
CalculateAnalyticsService
GenerateInsightsService
AskFinancialQuestionService
```

Application services coordinate domain operations and infrastructure ports.

---

# 20. Repository Interfaces

Example:

```java
interface TransactionRepository {
    Transaction save(Transaction transaction);

    Page<Transaction> findByCriteria(
        UserId userId,
        TransactionFilter filter,
        Pageable pageable
    );

    Optional<Transaction> findOwnedById(
        UserId userId,
        TransactionId transactionId
    );
}
```

The repository API should encode ownership requirements where practical.

Avoid patterns that encourage:

```text
findById(transactionId)
```

followed by a forgotten authorization check.

Prefer ownership-aware queries.

---

# 21. Object Storage

Define an abstraction:

```java
interface StatementStorage {
    StoredObject store(...);
    InputStream read(...);
    void delete(...);
}
```

Database stores:

```text
storage_key
file_hash
metadata
```

The database should not store the entire statement binary.

Object storage should be private and accessed through controlled backend operations.

---

# 22. Asynchronous Processing

The upload request should only perform work required to safely accept the statement.

Typical request path:

```text
validate
-> checksum
-> persist metadata
-> store object
-> enqueue processing
-> return
```

Heavy parsing belongs to workers.

## 22.1 Idempotency

Workers must tolerate duplicate delivery.

Before inserting transactions:

```text
load statement processing context
check transaction fingerprint
insert only unseen transaction
```

Repeated processing of the same statement should converge to the same financial dataset.

---

# 23. Outbox

When statement persistence and job publication must be coordinated:

```text
DB transaction
 ├── insert statement
 └── insert outbox record

commit

Outbox publisher
    |
    v
broker/job queue
```

This avoids the failure mode where the database says a statement exists but the processing request was lost.

Outbox records should include:

```text
id
event_type
aggregate_id
payload
created_at
published_at
attempt_count
last_error
```

---

# 24. Event Consumers

Each consumer should:

1. validate event schema/version
2. check idempotency
3. load authoritative state
4. perform its operation
5. commit state changes
6. acknowledge the event only after successful processing

Do not acknowledge before durable processing.

---

# 25. Idempotency Strategy

Introduce an idempotency record or equivalent mechanism for externally retried operations where required.

Conceptual table:

```text
idempotency_key
user_id
operation
request_hash
response_reference
created_at
expires_at
```

Rules:

- Same key + same request → return prior result.
- Same key + different request → reject.
- Keys are scoped to user/operation as appropriate.

---

# 26. Security Design

## 26.1 Authentication

Authentication establishes:

```text
UserPrincipal
    -> userId
```

No financial resource should rely on a client-supplied user ID.

## 26.2 Authorization

Every resource access must verify ownership.

Example:

```text
GET /transactions/txn_123
        |
        v
transaction.user_id == authenticated.user_id
```

## 26.3 File Security

Validate:

- declared MIME type
- actual file signature where applicable
- file extension
- maximum file size
- parser/resource limits
- decompression limits where relevant

Use malware scanning/sandboxing where required by deployment threat model.

## 26.4 Sensitive Data

Never log:

- passwords
- access tokens
- session secrets
- complete statements
- unnecessary transaction descriptions
- raw uploaded document contents

Use redaction for sensitive fields.

---

# 27. Error Model

Define typed application errors.

Examples:

```text
INVALID_REQUEST
UNAUTHORIZED
FORBIDDEN
RESOURCE_NOT_FOUND
DUPLICATE_STATEMENT
UNSUPPORTED_FILE_FORMAT
FILE_TOO_LARGE
STATEMENT_PROCESSING_FAILED
INVALID_TRANSACTION
RATE_LIMITED
INTERNAL_ERROR
```

Map them consistently to HTTP responses.

Example:

```text
400 INVALID_REQUEST
401 UNAUTHORIZED
403 FORBIDDEN
404 RESOURCE_NOT_FOUND
409 DUPLICATE_STATEMENT
413 FILE_TOO_LARGE
422 UNSUPPORTED_FILE_FORMAT
429 RATE_LIMITED
500 INTERNAL_ERROR
```

Do not expose stack traces or internal parser/database details to clients.

---

# 28. Observability

Every request should have:

```text
requestId
correlationId
userId (where safe)
```

Logs should be structured.

Important metrics:

```text
http_requests_total
http_request_duration
http_errors_total

statement_uploads_total
statement_processing_duration
statement_processing_success_total
statement_processing_failure_total

transactions_extracted_total
transactions_rejected_total
transactions_deduplicated_total

queue_depth
worker_failures_total
worker_retry_total

analytics_duration
database_query_duration
```

Sensitive financial payloads must not be included in metric labels.

---

# 29. Configuration

Use externalized configuration for:

- maximum upload size
- supported formats
- processing retry count
- retry backoff
- anomaly thresholds
- parser settings
- object-storage settings
- database settings
- broker settings
- AI provider settings
- rate limits

Never hard-code secrets.

---

# 30. Testing Strategy

## 30.1 Unit Tests

Required for:

- money calculations
- date/period calculations
- transaction validation
- fingerprinting
- merchant normalization
- categorization rules
- recurring detection
- anomaly rules
- insight rules

## 30.2 Integration Tests

Cover:

- PostgreSQL persistence
- Flyway migrations
- object storage
- queue/broker
- authentication
- authorization
- REST APIs

## 30.3 End-to-End Test

Minimum critical path:

```text
register
-> create account
-> upload CSV
-> statement queued
-> worker processes
-> transactions persisted
-> merchant/category enrichment
-> analytics generated
-> insights generated
-> query transactions
```

## 30.4 Failure Tests

Test:

- duplicate uploads
- duplicate event delivery
- worker crash
- database failure
- object-storage failure
- broker failure
- malformed statement
- invalid transaction row
- authorization failure
- expired authentication
- retry exhaustion

## 30.5 Performance Tests

Measure:

- concurrent uploads
- transaction insertion throughput
- transaction filtering latency
- dashboard analytics latency
- worker processing time
- database connection-pool behavior

---

# 31. Transaction Boundaries

Keep database transactions short.

Example statement workflow:

```text
Transaction A:
  create statement metadata
  create outbox record

Commit

Worker:

Transaction B:
  claim processing state

Commit

Transaction C:
  persist validated transactions

Commit

Transaction D:
  persist enrichment changes

Commit

Transaction E:
  persist analytics/insights

Commit
```

Do not hold a database transaction open while parsing a large PDF or waiting on an external service.

---

# 32. Concurrency

Potential concurrent operations include:

- same statement processed twice
- two workers processing different statements for one account
- user editing a transaction while enrichment runs
- analytics refresh overlapping with transaction ingestion

Use:

- idempotency
- unique constraints
- optimistic locking where appropriate
- explicit processing ownership
- deterministic recomputation

Avoid global locks.

---

# 33. User Corrections

A user correction must not be silently overwritten by later automated enrichment.

Recommended distinction:

```text
classification_source =
SYSTEM
USER
```

When a user changes a category or merchant:

```text
source = USER
```

Future enrichment must respect that override according to the product's correction rules.

---

# 34. Data Deletion

Deletion behavior must be explicit before production launch.

At minimum, determine:

- whether deleting a statement deletes its transactions
- whether user deletion cascades through all financial data
- object-storage deletion semantics
- audit retention
- backup retention
- legal/compliance retention requirements

Do not claim immediate physical deletion if backups retain copies.

---

# 35. AI Integration Boundary

AI should consume typed application-level tools.

Example:

```java
interface FinancialAnalysisTool {
    AnalysisResult execute(
        UserId userId,
        AnalysisRequest request
    );
}
```

Potential controlled tools:

```text
get_spending_summary
get_category_spending
get_merchant_spending
get_recurring_expenses
get_transaction_count
compare_periods
find_anomalies
project_cash_flow
```

Tool execution must:

1. authenticate user context
2. authorize requested scope
3. validate parameters
4. query deterministic backend services
5. return structured results
6. pass only necessary data to the LLM

The LLM must never receive arbitrary SQL/database credentials.

---

# 36. API DTO vs Domain Objects

Do not expose JPA/domain entities directly from controllers.

Use:

```text
Request DTO
    ->
Application command
    ->
Domain
    ->
Application result
    ->
Response DTO
```

This prevents persistence structure from becoming the public API contract.

---

# 37. Database Migration Rules

Every schema change requires a Flyway migration.

Rules:

- never modify an already-applied migration
- use additive changes where possible
- deploy backward-compatible schema changes before application changes when needed
- add indexes deliberately
- test migrations from a clean database
- test upgrades from a representative previous version

---

# 38. Performance Design

Initial optimization priorities:

1. correct SQL and indexes
2. pagination
3. bounded result sets
4. batch transaction insertion
5. efficient analytics queries
6. connection-pool tuning
7. asynchronous document processing
8. caching only after measurement

Do not introduce specialized databases or distributed caching solely for anticipated scale.

---

# 39. Deployment Topology

Initial production topology can be:

```text
Internet
   |
CDN / WAF
   |
Load Balancer
   |
Spring Boot Application
   |
+----------+-------------+
|          |             |
Postgres  Object       Queue
           Storage
                         |
                    Worker Process
```

The API and worker can initially use the same codebase with different runtime roles.

Later, workers can scale independently.

---

# 40. Extraction Boundaries for Future Services

Potential future extraction candidates:

```text
Document Processing Service
Analytics Service
AI Service
Notification Service
```

Extraction should occur only when justified by:

- independent scaling
- deployment independence
- failure isolation
- security boundary
- team ownership
- resource profile

Module boundaries in the monolith should mirror these potential boundaries without forcing distributed systems complexity prematurely.

---

# 41. Implementation Order

Recommended low-level implementation sequence:

1. project/module skeleton
2. database + Flyway
3. common security/error/observability primitives
4. User + authentication
5. Account
6. Statement + object storage
7. CSV parser
8. Transaction persistence
9. async processing
10. merchant normalization
11. categorization
12. analytics
13. recurring/anomaly detection
14. insights
15. PDF parser
16. hardening/performance/resilience
17. AI controlled-tool layer

Do not start with AI or infrastructure that is not required for the core vertical slice.

---

# 42. Open Implementation Decisions

The following require explicit ADRs or product decisions before the affected implementation is considered final:

- authentication/session technology
- exact broker choice
- exact S3-compatible storage provider
- CSV format/column mapping strategy
- supported PDF layouts
- OCR decision
- credit-card payment semantics
- transfer semantics
- refund/reversal semantics
- EMI representation
- user-correction persistence behavior
- retention/deletion policy
- exact transaction fingerprint algorithm
- anomaly thresholds
- category hierarchy and customization
- AI provider and data-retention policy

---

# 43. Low-Level Definition of Done

An implementation is complete only when:

- code follows module boundaries
- database migrations exist
- validation is implemented
- authorization is enforced
- idempotency is addressed
- failure behavior is defined
- unit tests cover domain logic
- integration tests cover infrastructure interactions
- API/event contracts are updated
- sensitive data is not logged
- observability is adequate
- local execution is verified
- concurrency/retry behavior is tested where applicable
- documentation is updated

For financial calculations, expected results must be asserted with exact decimal semantics rather than approximate floating-point comparisons.
