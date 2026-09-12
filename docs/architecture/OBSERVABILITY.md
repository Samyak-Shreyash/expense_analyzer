# Observability

The backend publishes structured JSON logs, Prometheus metrics, health probes, and OpenTelemetry traces. Do not include financial data, credentials, tokens, statement contents, or unnecessary PII in log fields or span attributes.

## Runtime endpoints

| Endpoint | Purpose |
| --- | --- |
| `/actuator/health` | Overall application health |
| `/actuator/health/liveness` | Process liveness probe |
| `/actuator/health/readiness` | Readiness probe, including database connectivity |
| `/actuator/prometheus` | Prometheus scrape endpoint |
| `/actuator/metrics` | Available Micrometer metric names |

Metrics are tagged with `application` and `environment`. HTTP request metrics include histogram buckets and SLO boundaries from 50 ms to 5 s. Hikari connection-pool metrics are enabled.

The health probe and Prometheus scrape endpoints are intentionally available without application credentials so orchestrators and the local Prometheus service can reach them. Other actuator endpoints remain authenticated; production deployments should also restrict the management port at the network boundary.

## Correlation and tracing

Every HTTP response includes an `X-Correlation-ID`. A valid inbound value is reused; otherwise the application creates a UUID. The ID is present in the logging MDC as `correlationId`, so Spring Boot's Logstash JSON output includes it. Micrometer Tracing additionally writes `traceId` and `spanId` to the MDC and propagates W3C trace context to downstream HTTP clients.

Traces are sampled at 10% by default. Change `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` to `1.0` temporarily when investigating an incident. Configure `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` for the deployment's OpenTelemetry Collector.

## Local stack

From `infrastructure/compose`, run `docker compose --env-file ../../.env --profile observability up --build`. The `--env-file` flag is required because Compose interpolation happens before a service-level `env_file` is applied. It keeps credentials in the repository-root `.env` while allowing Compose to use `SERVER_PORT`, database credentials, and other configuration safely. This starts Prometheus at [http://localhost:9090](http://localhost:9090), Grafana at [http://localhost:3000](http://localhost:3000), Tempo at [http://localhost:3200](http://localhost:3200), and an OpenTelemetry Collector on ports 4317 (gRPC) and 4318 (HTTP). Grafana provisions Prometheus and Tempo data sources automatically.

The optional Gradle task `gradlew bootRunWithOtel` requires the Java agent at `backend/agents/opentelemetry-javaagent.jar` and a collector listening on `localhost:4317`. It exports all three OTLP signals to that collector. Use either the agent or the built-in Spring instrumentation for tracing in a deployment; do not enable both without deliberately accepting overlapping spans.
