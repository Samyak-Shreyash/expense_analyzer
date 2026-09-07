# Event Model

**Document ID:** ARCH-003  
**Version:** 0.1

## 1. Purpose
Define asynchronous processing contracts for document ingestion, enrichment, analytics, insights, and notifications.

## 2. Envelope

Every event contains:

```json
{
  "eventId": "evt_123",
  "eventType": "StatementUploaded",
  "eventVersion": 1,
  "occurredAt": "2026-01-01T10:00:00Z",
  "producer": "statement-module",
  "correlationId": "corr_123",
  "userId": "user_123",
  "aggregateId": "stmt_123",
  "payload": {}
}
```

## 3. Events

### StatementUploaded
Emitted when statement metadata and object-storage reference are persisted.

Payload:
`statementId, accountId, storageKey, format, fileHash`

### DocumentProcessingRequested
Requests asynchronous parsing.

Payload:
`statementId, attempt`

### TransactionsExtracted
Indicates extraction produced candidate transactions.

Payload:
`statementId, transactionCount, extractionMetadata`

### TransactionsEnriched
Indicates merchant normalization/category enrichment completed.

Payload:
`statementId, transactionCount, enrichmentVersion`

### AnalyticsRequested
Requests analytics refresh for a user/account/period.

Payload:
`userId, accountId, periodStart, periodEnd, reason`

### AnalysisCompleted
Indicates deterministic analytics are ready.

Payload:
`userId, analysisId, periodStart, periodEnd, metricsReference`

### InsightGenerated
Indicates an insight was created.

Payload:
`insightId, userId, insightType, severity`

### NotificationRequested
Requests delivery through a configured notification channel.

Payload:
`userId, notificationType, resourceId`

## 4. Delivery Semantics
- At-least-once delivery should be assumed.
- Consumers must be idempotent.
- Duplicate events must not duplicate financial records.
- Retry transient failures with bounded backoff.
- Poison messages go to a dead-letter mechanism.
- Ordering requirements must be explicit per aggregate.

## 5. Transactional Outbox
When a database mutation and event publication must be atomic from the application's perspective, write the event to an outbox in the same database transaction, then publish asynchronously.

## 6. Versioning
- Event type + version define the schema.
- Consumers must tolerate additive fields.
- Breaking schema changes require a new version.
- Event schemas should be contract-tested.

## 7. Security
Events must carry only data required for processing. Do not place raw statement contents, credentials, access tokens, or unnecessary financial details into the event envelope.

## 8. Correlation
`correlationId` follows one business operation through API, persistence, queue, workers, analytics, and insight generation.
