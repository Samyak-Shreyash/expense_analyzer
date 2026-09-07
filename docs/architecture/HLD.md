# High-Level Design

**Document ID:** ARCH-001  
**Version:** 0.1  
**Status:** Draft

## 1. Architecture Decision
Start with a **modular monolith** rather than full microservices. Keep module boundaries explicit so high-load or independently deployable components can later be extracted.

## 2. Logical Architecture

```text
Web / Mobile / Chat
        |
     API Layer
        |
+---------------------------+
| Spring Boot Application   |
|                           |
| Auth / User               |
| Account                   |
| Statement                 |
| Transaction               |
| Enrichment               |
| Analytics                 |
| Insight                   |
| AI                        |
| Notification              |
| Audit                     |
+---------------------------+
   |          |          |
PostgreSQL  Object     Queue/Broker
            Storage       |
                          +--> Document Worker
                          +--> Enrichment Worker
                          +--> Analytics Worker
```

## 3. Core Components

### API/Application
- Authentication and authorization.
- Resource ownership checks.
- CRUD APIs.
- Query/filter APIs.
- Orchestration of asynchronous processing.

### PostgreSQL
System of record for users, accounts, statements, transactions, merchants, categories, recurring expenses, insights, and audit metadata.

### Object Storage
Store original uploaded statements. Use an abstraction so S3/MinIO/provider can change without domain coupling.

### Queue/Broker
Used for long-running document processing and downstream asynchronous work. RabbitMQ is a reasonable early option for job/command semantics; Kafka can be evaluated when event-stream scale or replay requirements justify it.

### Workers
- Document parsing.
- Transaction enrichment.
- Analytics computation.

### Cache
Redis is optional and should be introduced only for a demonstrated caching/session/rate-limiting need.

### Search/Analytics Infrastructure
OpenSearch and a separate analytics database are deferred until PostgreSQL no longer meets measured requirements.

## 4. Processing Flow
1. Upload statement.
2. Validate file.
3. Persist statement metadata and object-storage reference.
4. Publish processing request through an outbox/job mechanism.
5. Parse document.
6. Validate and persist transactions.
7. Normalize merchant/description.
8. Categorize.
9. Detect duplicates.
10. Compute recurring/anomaly signals.
11. Refresh analytics.
12. Generate insights.
13. Mark processing complete.

## 5. Reliability
- Idempotent consumers.
- Retry with bounded attempts.
- Dead-letter handling.
- Correlation IDs.
- Transactional outbox where publishing must be coordinated with database state.
- Explicit processing state machine.
- Safe failure and retry semantics.

## 6. Security
- TLS.
- Authentication.
- Resource-level authorization.
- Encryption at rest where applicable.
- Secure file validation.
- Size/time/resource limits.
- Secrets management.
- No sensitive financial payloads in logs.
- Minimize financial data sent to external AI providers.

## 7. AI Architecture
```text
User Question
   -> Intent Detection
   -> Authorization
   -> Controlled Tool
   -> Analysis Service
   -> Verified Financial Data
   -> LLM Explanation
   -> Response Validation
```
LLMs are not financial systems of record.

## 8. Observability
Structured logs, metrics, tracing, queue depth, processing duration, DB health, API latency/error rates, and worker failures. OpenTelemetry/Prometheus/Grafana can be introduced as the observability stack.

## 9. Evolution Path
1. Modular monolith.
2. Async workers.
3. AI layer.
4. Scale individual bottlenecks.
5. Extract services only where justified.
6. Add production hardening and deployment automation.
