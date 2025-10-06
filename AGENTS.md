# Repository Guidelines


## Project Structure & Module Organization
- `src/main/java/org/yanhuang/ai`: Entry `ClaudeKiroApplication` and packages for `config`, `controller`, `model`, `parser`, and `service`.
- `src/main/resources/application.yml`: Central config (port 7860, Kiro credentials); mirror changes in `application-*.yml` profiles.
- `src/test/java`: Layered suites—`unit`, `integration`, `service`, `e2e`—with shared helpers (`TestDataFactory`, `TestUtil`).
- `run-e2e-tests.ps1`: Quota-aware PowerShell harness; `claudedocs/` hosts supporting Anthropic compliance notes.

## Build, Test, and Development Commands
- `mvn spring-boot:run` — start the WebFlux service locally.
- `mvn clean verify` — compile plus unit and integration cycle via Surefire/Failsafe.
- `mvn test -Dtest=E2ETestRunner` — execute the full end-to-end suite; append `#method` for targeted checks.
- `pwsh .\run-e2e-tests.ps1 -TestClass ClaudeChatE2ETest` — scripted runner validates Java 21 and env vars before invoking Maven.

## Coding Style & Naming Conventions
- Target Java 21, four-space indent, `UpperCamelCase` types, `lowerCamelCase` members, and `CONSTANT_CASE` for env keys.
- Keep reactive boundaries: controllers return `Mono`/`Flux`, services encapsulate orchestration, parsers stay deterministic.
- Introduce config through `AppProperties`, document defaults, and prefer constructor injection with `final` collaborators.
- Log with SLF4J, keep messages in English, and extend existing key patterns instead of inventing new ones.

## Testing Guidelines
- Use JUnit 5 with AssertJ and Reactor `StepVerifier`; mock WebClient or token refresh paths for units.
- Name fast tests `*Test`, integration tests `*IT`/`*IntegrationTest`, and E2E classes under `e2e` with scenario-based names.
- Reuse fixtures from `TestDataFactory` and `application-*.yml` to keep payloads and settings consistent.
- Export `CLAUDE_API_KEY` (and optional `KIRO_*`) before E2E runs; quota guards (`@EnabledIfKiroQuotaAvailable`) skip when limits hit.

## Commit & Pull Request Guidelines
- Write concise, present-tense subjects (e.g., `Tighten tool-call parser`) aligned with existing history.
- Reference issues when available, call out config or contract changes, and invite security review for token handling.
- Confirm `mvn clean verify` and relevant E2E commands before push; note the exact commands in the PR and attach key logs or responses.

## Environment & Security Notes
- Keep secrets in env vars; never commit token files. Extend `.gitignore` before introducing new secret locations.
- Update README and `run-e2e-tests.ps1` comments when adding required variables, and review `CLAUDE.md` for agent-facing rules.
