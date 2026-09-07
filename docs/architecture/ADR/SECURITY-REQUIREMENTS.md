# Expense Analyzer — Security Requirements & Security Model

**Document ID:** SEC-001  
**Version:** 1.0  
**Status:** Draft — Consolidated Baseline  
**Scope:** MVP and production-oriented foundation  
**Related Documents:** `PRD.md`, `MVP-REQUIREMENTS.md`, `HLD.md`, `LLD.md`, `DOMAIN-MODEL.md`, `API-CONTRACT.md`, `EVENT-MODEL.md`, `BACKLOG.md`

> This document combines the security architecture, threat model, access-control model, sensitive-data handling rules, implementation requirements, and security acceptance criteria into one security source of truth.

---

# 1. Purpose

Expense Analyzer processes highly sensitive personal financial information, including bank statements, transaction amounts, merchants, categories, recurring expenses, financial analytics, and potentially natural-language questions about a user's finances.

The security objective is:

> **Protect the confidentiality, integrity, availability, and accountability of financial data throughout its lifecycle, while guaranteeing strict isolation between users.**

The security model must be applied across:

```text
Client
  ↓
API / Authentication
  ↓
Application Services
  ↓
Database
  ↓
Object Storage
  ↓
Asynchronous Workers
  ↓
Analytics / Insights
  ↓
AI Tools / LLM
  ↓
Logs / Audit / Backups
```

---

# 2. Security Principles

## SEC-PRINCIPLE-001 — Financial Data Is Sensitive

Any information that reveals a user's financial activity is sensitive by default.

This includes:

- transaction amounts
- transaction dates
- descriptions
- merchants
- categories
- account identifiers
- statements
- recurring expenses
- spending analytics
- cash-flow information
- AI questions concerning finances

## SEC-PRINCIPLE-002 — User Isolation Is P0

No user may access another user's financial information through:

- REST APIs
- database queries
- object storage
- asynchronous processing
- analytics
- insights
- AI tools
- audit interfaces
- administrative interfaces

## SEC-PRINCIPLE-003 — Server-Side Authorization

The client must never determine the effective user identity used for authorization.

Authorization must be derived from authenticated server-side context.

## SEC-PRINCIPLE-004 — Defense in Depth

Security must not depend on a single control.

Use multiple layers:

```text
Authentication
    +
API authorization
    +
Service ownership checks
    +
Database scoping
    +
Private object storage
    +
AI tool authorization
```

## SEC-PRINCIPLE-005 — Least Privilege

Every user, service, worker, administrator, database credential, storage credential, and AI tool should have only the permissions required for its function.

## SEC-PRINCIPLE-006 — Fail Closed

When authentication, authorization, ownership, validation, or security policy cannot be established, deny the operation.

## SEC-PRINCIPLE-007 — AI Is Not Financial Truth

LLMs may interpret intent and explain verified results.

They must not be the source of truth for:

- transaction amounts
- totals
- percentages
- counts
- financial calculations
- authorization decisions

---

# 3. Security Scope

This document covers:

1. Authentication
2. Authorization
3. User isolation
4. Encryption
5. PII protection
6. Financial-data handling
7. File/upload security
8. Audit logging
9. Secrets management
10. Rate limiting
11. Asynchronous processing security
12. AI security
13. Logging and observability
14. Security testing
15. Data retention and deletion
16. Security review and acceptance

---

# 4. Assets and Data Classification

## 4.1 Asset Classification

| Asset | Classification | Examples |
|---|---|---|
| Authentication credentials | Critical | Password hashes, tokens, signing keys |
| Uploaded statements | Critical | PDF, CSV, XLSX |
| Financial transactions | High | Amount, date, merchant, description |
| Account identifiers | High | Masked account/card identifiers |
| Financial analytics | High | Spending totals, trends, cash flow |
| Recurring expenses | High | Subscription and payment patterns |
| AI financial queries | High | User questions and generated answers |
| User PII | High | Email, name, optional phone |
| Audit logs | High | Security and operational actions |
| Application secrets | Critical | DB, storage, broker, AI credentials |
| Operational metrics | Medium | Counts, latency, queue depth |

## 4.2 Data Minimization

Only data required for a defined product or operational purpose may be collected or stored.

The MVP should not collect government identifiers such as:

- Aadhaar
- PAN
- passport

unless a future approved requirement explicitly requires them.

---

# 5. Data Flow and Trust Boundaries

## 5.1 Main Financial Data Flow

```text
                   UNTRUSTED
                       |
                       v
                +-------------+
                | Client/User |
                +-------------+
                       |
                     TLS
                       |
                       v
              +------------------+
              | API/Application   |
              | Authentication    |
              | Authorization     |
              +------------------+
                 |            |
                 |            |
                 v            v
          PostgreSQL      Object Storage
                 |
                 v
          Async Processing
                 |
                 v
       Parser / Enrichment
                 |
                 v
             Analytics
                 |
          +------+------+
          |             |
          v             v
       Insights        AI
```

## 5.2 Trust Boundaries

Treat these as separate trust boundaries:

1. Internet/client → API
2. API → database
3. API → object storage
4. Application → worker/broker
5. Worker → uploaded document
6. Application → external AI provider
7. Application → logs/monitoring
8. Administrator → operational interfaces

Crossing a trust boundary requires explicit authentication, authorization, validation, and data minimization.

---

# 6. Threat Model

The security model uses STRIDE plus AI-specific and document-processing threats.

## 6.1 Spoofing

### Threat

An attacker impersonates another user.

### Examples

- stolen password
- stolen token
- credential stuffing
- compromised session

### Controls

- secure authentication
- password hashing
- token expiration
- secure token handling
- rate limiting
- session invalidation
- no credentials in logs

---

## 6.2 Tampering

### Threat

Financial information is modified without authorization.

### Examples

- transaction amount changed
- category changed without permission
- statement metadata altered
- event payload manipulated

### Controls

- TLS
- server-side validation
- authorization
- controlled mutation APIs
- database constraints
- audit logging
- immutable/raw source preservation
- idempotent event handling

---

## 6.3 Repudiation

### Threat

A user or administrator denies performing an important action.

### Controls

Audit important security and financial-data operations with:

```text
actor
action
resource
timestamp
requestId
correlationId
metadata
```

Where appropriate, also capture:

```text
IP
user agent
authentication result
```

Avoid capturing unnecessary financial payloads.

---

## 6.4 Information Disclosure

### Threat

Unauthorized party obtains financial information.

### Examples

- IDOR
- broken ownership checks
- public object-storage bucket
- database compromise
- excessive AI context
- sensitive logs
- admin overreach

### Controls

- ownership checks
- role-based access
- database user scoping
- private object storage
- encryption
- least privilege
- AI data minimization
- log redaction

---

## 6.5 Denial of Service

### Threat

An attacker consumes excessive application resources.

### Examples

- 2 GB upload
- malformed PDF
- decompression bomb
- millions of CSV rows
- repeated AI queries
- concurrent uploads

### Controls

- maximum upload size
- maximum rows/pages
- parser timeout
- memory/resource limits
- asynchronous processing
- rate limiting
- bounded retries
- circuit breakers where appropriate
- queue backpressure

Initial proposed upload limit:

```text
20 MB
```

This must remain configurable.

---

## 6.6 Elevation of Privilege

### Threat

A USER obtains ADMIN privileges or accesses resources outside their permissions.

### Controls

- explicit RBAC
- server-side role validation
- method-level authorization
- service-level ownership checks
- database scoping
- security integration tests
- no client-controlled role assignment

---

## 6.7 Prompt Injection

### Threat

User-controlled content attempts to manipulate the LLM.

Potential sources:

- transaction descriptions
- merchant names
- uploaded statement text
- user questions

### Control

All external content is untrusted data.

A statement containing:

```text
Ignore previous instructions...
```

must never alter system instructions, authorization, or tool permissions.

---

## 6.8 AI Data Exfiltration

### Threat

The LLM receives or reveals financial information outside the authorized scope.

### Controls

- authorized tool calls
- minimum required data
- no arbitrary SQL
- no arbitrary DB access
- no unrestricted object-storage access
- output validation
- audit logging
- AI rate limiting

---

## 6.9 Malicious Upload

### Threat

A malicious PDF/CSV/XLSX exploits a parser or consumes excessive resources.

### Controls

- extension validation
- MIME validation
- magic-byte validation
- size limits
- parser timeouts
- memory limits
- isolated processing
- dependency patching
- malware scanning where required

---

# 7. Authentication Model

## 7.1 Requirements

Protected APIs require authenticated user context.

Passwords:

- must never be stored in plaintext;
- must use a strong password-hashing algorithm;
- must never be logged.

The source security model proposes BCrypt as the initial password-hashing mechanism.

## 7.2 Token Model

JWT may be used for stateless API authentication.

Conceptual payload:

```json
{
  "sub": "user@example.com",
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "roles": ["ROLE_USER"],
  "iat": 1516239022,
  "exp": 1516239022
}
```

Signing may use a strong asymmetric scheme such as RS256, or another approved cryptographic design.

Signing keys/secrets must be securely stored and rotated.

## 7.3 Refresh Tokens

If refresh tokens are implemented:

- use secure storage;
- prefer HTTP-only, Secure cookies where appropriate;
- define expiration;
- define revocation;
- never log them.

## 7.4 Authentication Rate Limiting

Rate-limit:

- login
- registration
- password reset
- other credential-sensitive endpoints

Authentication failures must not unnecessarily reveal whether an account exists.

---

# 8. Authorization Model

## 8.1 Roles

Initial roles:

```text
USER
ADMIN
```

## 8.2 USER Permissions

USER may:

- register/login/logout
- manage own profile
- create/manage own accounts
- upload own statements
- view own statements
- view own transactions
- modify allowed fields on own transactions
- view own analytics
- view own recurring expenses
- view own anomalies
- view own insights
- execute AI queries against own data
- delete own account according to deletion policy

## 8.3 ADMIN Permissions

ADMIN may:

- access system health
- access operational metrics
- manage user account status where required
- inspect failed processing jobs
- reprocess failed events
- access authorized audit information

### Critical Rule

ADMIN does **not automatically inherit permission to view other users' raw financial data**.

If exceptional financial-data access is ever required, it must be:

- explicitly authorized;
- narrowly scoped;
- separately audited;
- justified by an approved operational/legal requirement.

---

# 9. Resource Ownership

The ownership model is:

```text
User
 ├── Account
 │    └── Transaction
 ├── Statement
 ├── RecurringExpense
 ├── Insight
 ├── AIQuery
 └── AuditLog
```

Every protected resource must have a resolvable owner.

## 9.1 Preferred Repository Pattern

Prefer:

```java
transactionRepository.findOwnedById(
    authenticatedUserId,
    transactionId
);
```

over:

```java
transactionRepository.findById(transactionId);
```

## 9.2 Database Enforcement

Sensitive queries should include user scope:

```sql
SELECT *
FROM transactions
WHERE id = :transactionId
  AND user_id = :authenticatedUserId;
```

The same principle applies to:

- accounts
- statements
- transactions
- insights
- recurring expenses
- AI queries

## 9.3 IDOR Protection

Never assume that an opaque ID provides authorization.

This request:

```text
GET /transactions/txn_123
```

must still verify:

```text
transaction.user_id == authenticated_user.id
```

---

# 10. Access-Control Matrix

| Resource | USER Own Data | USER Other Data | ADMIN Operational | ADMIN Financial |
|---|---:|---:|---:|---:|
| Profile | CRUD | Deny | Limited | Deny |
| Account | CRUD | Deny | Limited | Deny |
| Statement | CRUD/limited | Deny | Processing only | Deny |
| Transaction | Read/limited update | Deny | Operational only | Deny |
| Analytics | Read | Deny | Aggregate operational metrics | Deny |
| Recurring | Read | Deny | Operational only | Deny |
| Insights | Read/delete where allowed | Deny | Operational only | Deny |
| AI queries | Own | Deny | Operational metadata only | Deny |
| Audit logs | No direct access unless explicitly designed | Deny | Authorized | No raw financial data |

---

# 11. Enforcement Layers

| Layer | Security Control |
|---|---|
| API Gateway | TLS + token validation |
| Controller | Role/method authorization |
| Application service | Ownership/business authorization |
| Repository | User-scoped queries |
| Database | Constraints and optional row-level security |
| Object Storage | Private bucket + scoped access |
| Async Worker | Authenticated job context + ownership validation |
| AI Tool | Explicit user-scoped authorization |
| Audit | Append-only security records |

No single layer should be the only ownership control.

---

# 12. Encryption

## 12.1 Encryption in Transit

All external endpoints must use:

```text
TLS 1.2+
```

Prefer current secure TLS configurations.

Enable:

```text
HTTPS
HSTS
secure cookies where applicable
```

Internal traffic must use encryption where it crosses an untrusted boundary or deployment policy requires it.

## 12.2 Encryption at Rest

Protect:

- PostgreSQL volumes
- object storage
- backups
- snapshots
- audit logs where applicable

The source security model proposes:

- AWS KMS/encrypted RDS storage;
- SSE-S3 for S3;
- equivalent encrypted storage for MinIO/local development.

## 12.3 Application-Level Encryption

Do not introduce field-level encryption indiscriminately.

Use it when a specific threat model requires protection beyond infrastructure-level encryption, because field-level encryption can significantly affect querying and analytics.

---

# 13. Secrets Management

Secrets include:

```text
database credentials
JWT signing keys
S3 credentials
broker credentials
AI provider API keys
encryption keys
```

Store them in:

```text
AWS Secrets Manager
or
HashiCorp Vault
or
equivalent approved secret manager
```

Never commit them to:

- Git
- source files
- Docker images
- application configuration committed to source control
- logs

Secrets must be rotated according to operational policy.

---

# 14. PII Protection

## 14.1 Stored PII

The MVP should minimize stored PII to:

```text
email
name (optional)
```

Phone number is optional only if explicitly required.

## 14.2 Prohibited by Default

Do not store:

```text
Aadhaar
PAN
passport
government identity documents
```

unless an approved future requirement requires them.

## 14.3 Account Identifiers

Prefer:

```text
masked_identifier
```

over unnecessary storage of full account/card numbers.

---

# 15. Sensitive Data Handling

## 15.1 Data Lifecycle

```text
User Upload
    ↓
Validation
    ↓
Private Object Storage
    ↓
Parsing
    ↓
PostgreSQL
    ↓
Enrichment
    ↓
Analytics
    ↓
Insights
    ↓
Optional AI
    ↓
Backups / Audit / Operational Systems
    ↓
Retention / Deletion
```

At each stage determine:

- what data is stored;
- who can access it;
- whether it is encrypted;
- how long it is retained;
- whether it is sent externally;
- what is logged.

---

# 16. Sensitive-Data Matrix

| Data | Storage | Access | External Sharing | Logging |
|---|---|---|---|---|
| Password | PostgreSQL hash | Auth subsystem | Never | Never |
| Original statement | Private object storage | Authorized backend/user | No by default | Never |
| Transactions | PostgreSQL | Owner/backend | No by default | IDs/metadata only |
| Analytics | PostgreSQL | Owner/backend | No by default | Aggregate operational metrics |
| AI question | PostgreSQL | Owner/AI flow | Only minimum required provider data | Minimize |
| AI tool results | Application layer | Authorized AI flow | Minimum required | Metadata only |
| Account identifier | PostgreSQL | Owner/backend | No by default | Masked |
| Audit log | PostgreSQL/security system | Authorized operators | No by default | N/A |
| Secrets | Secret manager | Specific services | Never | Never |

---

# 17. Logging Rules

## 17.1 Never Log

```text
password
password reset token
access token
refresh token
JWT secret
API key
database password
complete statement
complete transaction list
full account/card number
unnecessary raw transaction description
```

## 17.2 Safe Operational Logging

Prefer:

```json
{
  "requestId": "req_123",
  "correlationId": "corr_123",
  "userId": "usr_123",
  "statementId": "stmt_123",
  "action": "STATEMENT_PROCESSING_STARTED"
}
```

Do not use transaction descriptions or financial amounts as metric labels.

---

# 18. Audit Logging

Audit logging answers:

> Who performed what action against which resource, and when?

## 18.1 Required Audit Events

### Authentication

```text
USER_LOGIN_SUCCESS
USER_LOGIN_FAILURE
USER_LOGOUT
```

Fields:

```text
userId where known
IP where policy permits
userAgent where policy permits
success/failure
timestamp
requestId
```

### Statement

```text
STATEMENT_UPLOADED
STATEMENT_ACCESS
STATEMENT_PROCESSING_FAILED
STATEMENT_DELETED
```

### Transactions

```text
TRANSACTION_UPDATED
CATEGORY_CHANGED
MERCHANT_CHANGED
```

### AI

```text
AI_QUERY_EXECUTED
AI_TOOL_EXECUTED
AI_QUERY_FAILED
```

### Administration

```text
ADMIN_USER_SUSPENDED
ADMIN_USER_UPDATED
ADMIN_JOB_REPROCESSED
```

### Account

```text
ACCOUNT_CREATED
ACCOUNT_UPDATED
ACCOUNT_DELETED
USER_ACCOUNT_DELETED
```

## 18.2 Audit Record

```json
{
  "userId": "usr_123",
  "action": "TRANSACTION_UPDATED",
  "resourceType": "TRANSACTION",
  "resourceId": "txn_123",
  "timestamp": "2026-01-01T10:00:00Z",
  "requestId": "req_123",
  "metadata": {}
}
```

## 18.3 Audit Integrity

Audit logs should be:

- append-only;
- access-controlled;
- encrypted at rest;
- protected against ordinary application-user modification.

For higher assurance, consider tamper-evident external storage later.

## 18.4 Retention

The source security model proposes an initial configurable retention of 30 days.

Before production, retention must be explicitly confirmed against:

- product requirements;
- operational requirements;
- applicable legal/regulatory requirements;
- deletion requirements.

---

# 19. File Security

Uploaded statements are untrusted input.

## 19.1 Allowed Formats

Potential supported formats:

```text
.csv
.xlsx
.pdf
```

Actual MVP support must match `MVP-REQUIREMENTS.md`.

Unsupported formats must be rejected.

## 19.2 Validation

Validate:

1. extension;
2. declared MIME type;
3. magic bytes/file signature;
4. file size;
5. structural validity;
6. parser resource requirements.

Never trust MIME type or extension alone.

## 19.3 File Size

Initial proposed maximum:

```text
20 MB
```

The value must be configurable.

## 19.4 Storage

Use:

```text
private object-storage bucket
random/generated storage key
server-side encryption
```

Do not use the original filename as the object-storage key.

## 19.5 Download

If files are exposed through presigned URLs:

- authorization must happen before issuing the URL;
- URLs must be short-lived;
- URLs must not be logged;
- object storage must remain private.

A suggested initial expiration is one hour, but production policy may use a shorter duration.

---

# 20. Document Processing Security

The parser is a security boundary because uploaded files are attacker-controlled.

## 20.1 Processing

Do not parse large documents synchronously inside the API request.

Use:

```text
Upload
  ↓
Validation
  ↓
Private Storage
  ↓
Async Worker
  ↓
Parser
```

## 20.2 Resource Limits

Apply limits for:

- execution time;
- memory;
- file size;
- number of rows;
- PDF pages/objects where applicable;
- decompression expansion.

## 20.3 Libraries

The source security model identifies:

```text
Apache PDFBox
OpenCSV
Apache POI
```

as candidate libraries.

Before production:

- pin versions;
- scan dependencies for known vulnerabilities;
- use secure parser configuration;
- disable unsafe external resource behavior where supported;
- test malformed documents.

## 20.4 Malware Scanning

For the MVP, malware scanning can be treated as a deployment/security decision rather than blindly assuming it is unnecessary.

Potential pipeline:

```text
Upload
 ↓
Quarantine
 ↓
Validation
 ↓
Malware Scan
 ↓
Approved Storage
 ↓
Processing
```

Possible tooling includes ClamAV or a managed scanning service.

---

# 21. CSV Security

CSV is data, not executable content.

Protect against:

- malformed input;
- excessive row counts;
- extremely large fields;
- encoding problems;
- formula injection when exported later;
- parser resource exhaustion.

If transaction data is later exported to spreadsheet-compatible formats, protect against spreadsheet formula injection by escaping dangerous leading characters where appropriate.

---

# 22. PDF Security

Protect against:

- malformed PDFs;
- parser vulnerabilities;
- embedded resources;
- decompression/resource exhaustion;
- password-protected files;
- unsupported structures.

Do not automatically execute or extract arbitrary embedded content.

OCR, if introduced later, must have the same resource and isolation controls.

---

# 23. Asynchronous/Event Security

Events must contain only data required for processing.

Do not put:

```text
raw statement content
passwords
tokens
unnecessary transaction lists
```

into events.

Every event should contain controlled metadata such as:

```text
eventId
eventType
eventVersion
occurredAt
producer
correlationId
userId
aggregateId
payload
```

## 23.1 Event Authorization

Workers must not trust a client-supplied user ID.

They should resolve authoritative state from the statement/account/transaction being processed.

## 23.2 Idempotency

Assume at-least-once delivery.

Consumers must tolerate duplicate events.

A duplicate event must not result in duplicate financial records.

---

# 24. AI Security

## 24.1 Security Boundary

Required architecture:

```text
User
 ↓
AI API
 ↓
Authentication
 ↓
Intent Detection
 ↓
Authorization
 ↓
Controlled Tool
 ↓
Analysis Service
 ↓
Verified Financial Data
 ↓
LLM
 ↓
Output Validation
 ↓
User
```

## 24.2 No Arbitrary Database Access

The LLM must never have:

```text
database credentials
SQL execution
arbitrary repository access
unrestricted object-storage access
```

## 24.3 Controlled Tools

Potential tools:

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

Each tool must receive authenticated user context and enforce ownership.

## 24.4 Data Minimization

Bad:

```text
Send entire transaction history to LLM.
```

Good:

```text
User:
"Where did I spend the most last month?"

Backend:
Food      ₹18,200
Shopping  ₹12,100
Travel     ₹8,400

LLM:
Explain verified results.
```

## 24.5 Tool Argument Validation

Validate:

- date ranges;
- amounts;
- merchant strings;
- categories;
- pagination;
- query limits.

Prevent excessively broad queries.

## 24.6 Prompt Injection

Treat all external text as untrusted:

```text
user question
transaction description
merchant name
statement text
```

The following must never be allowed to change:

```text
system instructions
authorization
tool permissions
data scope
security policies
```

## 24.7 Output Validation

Validate AI responses for:

- unsupported claims;
- invented numbers;
- unauthorized identifiers;
- raw transaction IDs where inappropriate;
- accidental PII exposure;
- policy violations.

If validation fails, return a safe fallback or deterministic analytics result.

---

# 25. AI Audit Logging

Record enough information for security and operational investigation without storing unnecessary financial payloads.

Recommended fields:

```text
userId
queryId
intent
tool names
sanitized tool parameters
result metadata
LLM provider
model
token usage
latency
timestamp
success/failure
```

Avoid storing complete raw transaction lists.

Whether the complete natural-language question and response are retained must follow the final privacy/retention policy.

---

# 26. AI Provider Data Handling

Before production use of any external LLM provider, explicitly verify:

- data-retention policy;
- training/use-of-customer-data policy;
- encryption;
- regional processing where relevant;
- deletion behavior;
- subprocessors;
- contractual/privacy requirements.

Only the minimum required financial information should be transmitted.

---

# 27. Rate Limiting

At minimum rate-limit:

```text
login
registration
statement upload
AI queries
expensive analytics
```

Use per-user and/or per-IP controls as appropriate.

Redis + Bucket4j is a possible implementation.

Rate limits should be configurable.

---

# 28. Error Handling

Never expose security-sensitive internal details.

Do not return:

```text
SQL exception
stack trace
filesystem path
database hostname
internal parser error
another user's existence
```

Example:

```text
Resource not found.
```

rather than:

```text
Transaction txn_123 belongs to user usr_456.
```

Security failures should fail closed.

---

# 29. Data Integrity

Financial information must be protected against accidental or unauthorized modification.

Requirements:

- use `BigDecimal`/`NUMERIC` for monetary values;
- validate amounts;
- preserve raw descriptions;
- preserve source statement references;
- use database constraints;
- use transaction fingerprints for duplicate detection;
- distinguish user corrections from system classifications;
- audit important financial-data modifications.

User corrections should not be silently overwritten by later automated enrichment.

---

# 30. Database Security

## 30.1 Database Access

Application database credentials must have only required permissions.

Workers should not automatically receive broader privileges than the application requires.

## 30.2 Database Encryption

Use encrypted storage/managed database encryption.

## 30.3 Database Network Access

Database should not be publicly exposed.

Prefer:

```text
Internet
   |
API
   |
Private network
   |
PostgreSQL
```

## 30.4 Optional Row-Level Security

PostgreSQL Row-Level Security may be considered as an additional defense-in-depth control.

However, application ownership checks remain mandatory.

---

# 31. Object Storage Security

Bucket requirements:

- private by default;
- public access blocked;
- server-side encryption enabled;
- least-privilege IAM;
- generated storage keys;
- lifecycle policy;
- access logging where required.

The application should access objects through a storage abstraction rather than exposing storage implementation details to the domain.

---

# 32. Backup Security

Backups contain financial information and therefore require the same security classification as the source data.

Requirements:

- encrypted backups;
- restricted backup access;
- defined retention;
- tested restore process;
- no public backup endpoints;
- audit backup-access operations where supported.

---

# 33. Data Deletion

The product must define deletion semantics before production.

Questions requiring explicit decisions:

1. Does deleting a statement delete its transactions?
2. Does deleting an account delete associated statements?
3. Does deleting a user delete all financial data?
4. When are object-storage files deleted?
5. What happens to backups?
6. How long are audit records retained?
7. Are AI records deleted with the user?
8. What data must be retained for legal/operational reasons?

Do not claim immediate physical deletion if copies remain in backups.

---

# 34. Security Testing

## 34.1 Authentication Tests

Test:

- valid login;
- invalid credentials;
- expired token;
- invalid token signature;
- missing token;
- logout;
- rate limiting;
- password handling.

## 34.2 Authorization Tests

Test:

```text
User A → User A transaction     ALLOW
User A → User B transaction     DENY

User A → User A statement       ALLOW
User A → User B statement       DENY

User A → User B analytics       DENY
USER   → ADMIN operation        DENY
```

## 34.3 File Tests

Test:

- valid CSV;
- valid PDF;
- valid XLSX if enabled;
- oversized file;
- wrong extension;
- wrong MIME type;
- incorrect magic bytes;
- malformed PDF;
- malformed CSV;
- excessive rows;
- parser timeout;
- malicious document fixtures.

## 34.4 AI Tests

Test:

- prompt injection;
- unauthorized tool invocation;
- cross-user tool access;
- excessive query scope;
- fabricated calculations;
- sensitive-output leakage;
- unsupported question;
- provider failure;
- timeout;
- rate limiting.

## 34.5 Logging Tests

Automated tests should verify that:

```text
passwords
tokens
secrets
raw statements
unnecessary financial data
```

do not appear in logs.

## 34.6 Event Tests

Test:

- duplicate event;
- replay;
- malformed event;
- unauthorized user context;
- retry;
- dead-letter behavior.

---

# 35. Security Monitoring

Monitor at least:

```text
authentication failures
authorization failures
rate-limit violations
statement upload failures
parser failures
malicious-file detections
AI tool authorization failures
AI rate-limit violations
worker failures
dead-letter queue growth
unusual administrative activity
```

Alerts should be based on operationally meaningful thresholds.

Do not put sensitive financial values into metric labels.

---

# 36. Dependency and Vulnerability Management

Production dependencies must be regularly checked for known vulnerabilities.

Pay particular attention to:

- Spring/Spring Security
- PDF parsing libraries
- Apache POI
- CSV parsing libraries
- database drivers
- authentication libraries
- AI SDKs
- container base images

Security patches must be evaluated promptly according to severity.

---

# 37. Secure Development Rules

Developers must:

- never commit secrets;
- never use production financial data for local development;
- use synthetic fixtures for tests;
- avoid copying customer data into tickets;
- avoid sensitive information in screenshots/logs;
- perform authorization checks before data retrieval;
- use parameterized queries;
- validate all external input;
- update security documentation when architecture changes.

---

# 38. Security Architecture Decision Rules

## Rule 1 — No Financial Data Without User Context

Any operation returning financial information must have authenticated user context.

## Rule 2 — No Client-Controlled Authorization

Never trust:

```text
userId
role
account owner
```

from the request body as authorization truth.

## Rule 3 — No Arbitrary AI Data Access

LLMs interact only through explicitly approved tools.

## Rule 4 — No Raw Financial Data in Logs

Operational observability must use IDs and metadata.

## Rule 5 — No Unbounded Document Processing

Every parser has resource limits.

## Rule 6 — No Public Financial Objects

Uploaded statements remain in private storage.

## Rule 7 — No Security by Obscurity

Opaque IDs are useful but never substitute for authorization.

---

# 39. Security Implementation Roadmap

| Issue | Priority | Description |
|---|---:|---|
| SEC-001 | P0 | Document consolidated security requirements |
| SEC-002 | P0 | Implement authentication with Spring Security |
| SEC-003 | P0 | Implement resource-level ownership authorization |
| SEC-004 | P0 | Add authorization integration tests |
| SEC-005 | P0 | Implement file size/type/signature validation |
| SEC-006 | P0 | Add security tests for cross-user access |
| SEC-007 | P1 | Enable TLS/HSTS in deployment |
| SEC-008 | P1 | Enable encrypted database storage |
| SEC-009 | P1 | Configure private encrypted object storage |
| SEC-010 | P1 | Implement audit logging |
| SEC-011 | P1 | Implement sensitive-log redaction |
| SEC-012 | P1 | Secure secrets management |
| SEC-013 | P1 | Implement AI tool authorization |
| SEC-014 | P1 | Implement AI data minimization |
| SEC-015 | P1 | Add AI prompt-injection/security tests |
| SEC-016 | P1 | Add parser/resource limits |
| SEC-017 | P1 | Add worker/event idempotency controls |
| SEC-018 | P2 | Add rate limiting |
| SEC-019 | P2 | Add malware scanning |
| SEC-020 | P2 | Add automated dependency vulnerability scanning |
| SEC-021 | P2 | Define and implement account/data deletion |
| SEC-022 | P2 | Add automated sensitive-log detection |

---

# 40. Threat-to-Control Traceability

| Threat | Requirement | Implementation Area | Test |
|---|---|---|---|
| Account takeover | Authentication | Spring Security | Auth tests |
| IDOR | Ownership authorization | Service/repository | Cross-user tests |
| Privilege escalation | RBAC | Security layer | Role tests |
| DB leak | Encryption/least privilege | PostgreSQL | Deployment review |
| Object leak | Private storage | S3/MinIO | Storage tests |
| Malicious upload | File validation | Statement API | Upload tests |
| Parser DoS | Resource limits | Worker/parser | Resilience tests |
| Log leakage | Redaction | Logging | Log tests |
| AI leakage | Data minimization | AI service | AI security tests |
| Prompt injection | Untrusted-content model | AI layer | Injection tests |
| Event replay | Idempotency | Worker | Replay tests |
| Admin abuse | Restricted admin model | Admin APIs | Admin authorization tests |

---

# 41. Acceptance Criteria

The security-design task is complete when:

- [ ] Threat model is documented.
- [ ] Assets are classified.
- [ ] Trust boundaries are documented.
- [ ] STRIDE threats are documented.
- [ ] AI-specific threats are documented.
- [ ] File-processing threats are documented.
- [ ] Authentication requirements are documented.
- [ ] Authorization model is documented.
- [ ] USER/ADMIN roles are documented.
- [ ] Resource ownership rules are documented.
- [ ] Access-control matrix is documented.
- [ ] User isolation rules are documented.
- [ ] Encryption requirements are documented.
- [ ] Secrets-management requirements are documented.
- [ ] PII-minimization rules are documented.
- [ ] Sensitive-data handling matrix is documented.
- [ ] Logging/redaction rules are documented.
- [ ] Audit events are documented.
- [ ] Audit retention requirements are identified.
- [ ] File-upload security requirements are documented.
- [ ] Parser/resource limits are documented.
- [ ] Async/event security requirements are documented.
- [ ] AI authorization is documented.
- [ ] AI data minimization is documented.
- [ ] Prompt-injection controls are documented.
- [ ] AI output validation is documented.
- [ ] Rate-limiting requirements are documented.
- [ ] Backup/deletion requirements are identified.
- [ ] Security test cases are documented.
- [ ] Security monitoring requirements are documented.
- [ ] Implementation backlog is defined.
- [ ] Related architecture/domain/API/event documents have identified security impacts.

---

# 42. Security Definition of Done

A security-sensitive feature is complete only when:

1. Authentication is enforced where required.
2. Authorization is enforced at resource level.
3. Ownership is verified server-side.
4. Financial data is encrypted appropriately.
5. Sensitive values are excluded from logs.
6. Important actions are auditable.
7. External input is validated.
8. Uploaded documents are processed with resource limits.
9. AI access is controlled through authorized tools.
10. AI receives only necessary data.
11. Failure behavior does not reveal sensitive information.
12. Security tests cover expected abuse cases.
13. Relevant metrics and audit events exist.
14. Documentation matches implementation.
15. No known P0 security issue remains open.

---

# 43. Final Security Invariants

These invariants must remain true throughout the life of the product:

```text
1. A user can access only their own financial data.

2. An ADMIN cannot automatically access another user's raw financial data.

3. No client-provided userId can override authenticated identity.

4. No LLM can directly query the production database.

5. No uploaded document can modify security instructions.

6. No password, token, or secret appears in logs.

7. No original financial statement is publicly accessible.

8. No financial calculation depends on an LLM-generated number.

9. Duplicate events/uploads cannot silently duplicate financial records.

10. Security failures fail closed.

11. Backups receive appropriate protection because they contain financial data.

12. Every significant security decision is documented and traceable.
```

---

# 44. Review and Sign-Off

This document should be reviewed by:

- **Lead Architect** — architecture alignment
- **Engineering Lead** — implementation feasibility
- **Security Lead** where available — threat model and controls
- **Product Manager** — privacy/product requirements
- **Operations/DevOps** — deployment, secrets, monitoring, backups

Any significant change to:

- authentication;
- authorization;
- data storage;
- document processing;
- event architecture;
- AI integration;
- administrative access;
- retention/deletion

requires a security review.

---

## 45. Document Relationship

Security requirements must propagate into the rest of the engineering documentation:

```text
PRD
 |
 +--> MVP-REQUIREMENTS
 |
 +--> SECURITY-REQUIREMENTS
          |
          +--> HLD
          |
          +--> LLD
          |
          +--> DOMAIN-MODEL
          |
          +--> API-CONTRACT
          |
          +--> EVENT-MODEL
          |
          +--> BACKLOG
```

If an implementation decision conflicts with this document, the security impact must be reviewed before implementation.

---

**Status:** Draft — Consolidated Security Baseline  
**Next step:** Convert P0 security requirements into implementation tasks and security integration tests.
