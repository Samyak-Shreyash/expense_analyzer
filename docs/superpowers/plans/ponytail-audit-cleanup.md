# Ponytail audit cleanup

No separate spec: the user's audit list is the authority. Already done in fea8f76: authorization-server (replaced by a
direct resource-server dependency, which must stay), logstash encoder, mockito, ActuatorSecurityConfiguration stub.

## Global Constraints

- Behaviour-preserving. Edit only `backend/src/`, `backend/build.gradle` and the named docs.
- Do not read `.env`. Gradle only, run from `backend/`. Docker may be down: the only acceptable test failures are
  `FlywaySchemaValidationTest`, `StatementPipelineE2ETest`, `S3StatementStorageTest` with a Docker environment error.
  S3StatementStorageTest must still compile.
- Do not push. Commit with a conventional message ending `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

### Task 1: Mechanical removals

- `backend/build.gradle`: delete the `bootRunWithOtel` task and its "Optional local runner" comment, the `sourceSets`
  block, the `openTelemetryVersion` property, and the orphan comment `// Spring Boot Starter for @Value annotation support`.
- Remove the `bootRunWithOtel` row from `.claude/CLAUDE.md`, and the bootRunWithOtel/javaagent guidance from
  `docs/architecture/OBSERVABILITY.md`, leaving the surrounding doc coherent and describing the starter-based export.
- `Preference.java`: replace all hand-written getters/setters with class-level Lombok `@Getter @Setter`; keep any
  non-accessor members.
- Delete every tracked `.gitkeep` under `backend/src/main/java` and `backend/src/test/java`, and remove directories
  left empty. Packages with code stay.
- `.github/copilot-instructions.md`: remove `account`, `ai`, `enrichment`, `insight`, `notification` from the feature
  package list; add nothing else.

Verify: `./gradlew clean build`; `git ls-files '*.gitkeep'` shows none under those two trees.

### Task 2: Collapse StatementStorage

- Delete the `StatementStorage` interface. Move `keyFor` (static) and the method javadocs into `S3StatementStorage`,
  removing `implements` and `@Override`.
- Change all users (StatementProcessor, StatementService) and tests (AuditLogTest, StatementProcessorTest,
  CrossUserIsolationTest, StatementControllerTest, StatementUploadFailureModesTest, S3StatementStorageTest,
  MerchantAliasTest) to `S3StatementStorage`.

Verify: `./gradlew clean build`; `grep -rw StatementStorage backend/src` finds nothing.
