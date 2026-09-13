---
applyTo: "backend/src/test/**/*.java"
---
# Backend Tests — Expense Analyzer

- Use JUnit 5 and AssertJ. Existing integration tests use `@SpringBootTest`; MVC tests may use servlet test support.
- The checked-in test profile uses H2. Do not require Testcontainers, Reactor Test, WebTestClient, JaCoCo, or ArchUnit unless the task adds them.
- Tests are blocking because production code uses Spring MVC/JPA. Do not use `StepVerifier`.
- Name tests `methodName_stateUnderTest_expectedBehavior` and test observable behavior.
- Add unit tests for changed domain/service behavior and Spring tests for changed HTTP, security, persistence, or configuration behavior.
- Use synthetic data only. Test authorization and ownership failure paths for user-scoped endpoints.
- Avoid shared mutable static state, execution-order dependencies, and unnecessary wall-clock timing.

Run from `backend/`: `./gradlew.bat test` in PowerShell. CI runs `./gradlew clean build test --no-daemon` on JDK 17.
