# AGENTS.md

Instructions for AI coding agents working on the Procuratio backend.
Procuratio is a UK leasehold property management app. This repo is the Spring Boot API;
the React frontend lives in the separate `property-manager` repo.

## Commands

Run from this folder (Windows PowerShell; on macOS or Linux use `./mvnw`).

| Task | Command |
| --- | --- |
| Start PostgreSQL 16 (Docker) | `docker compose up -d` |
| Run the API (http://localhost:8082) | `.\mvnw spring-boot:run` |
| Run all tests | `.\mvnw test` |
| Run one test class | `.\mvnw test -Dtest=TemplateApiTests` |
| Build the jar | `.\mvnw package` |
| Stop PostgreSQL | `docker compose down` |

Tests start their own embedded PostgreSQL 16, so Docker is not needed for tests.
Before finishing a change, run `.\mvnw test` and make sure everything passes.

## Tech stack

Spring Boot 4.1.2, Java 21, Spring MVC, Spring Data JPA (Hibernate), Bean Validation,
Flyway, PostgreSQL 16, Jackson 3 (`tools.jackson.*` packages), Maven wrapper.
Tests: JUnit 5, MockMvc, AssertJ, zonky embedded PostgreSQL.

## Project structure

```
src/main/java/com/ceybertech/pdfmedemo/
  template/     Feature package: Controller (endpoints), Service (rules),
                Repository (database), PdfTemplate (entity), dto/ (JSON records)
  common/       ApiException, ErrorResponse, GlobalExceptionHandler
  config/       CorsConfig
src/main/resources/
  application.properties     Settings, overridable by environment variables
  db/migration/              Flyway SQL migrations
src/test/java/...            API tests per feature (see template/TemplateApiTests)
```

New features get their own package next to `template/`, with the same layers:
Controller, Service, Repository, entity, `dto/` records, and a `<Feature>ApiTests` test class.
The package is still named `pdfmedemo`; a rename to `procuratio` is planned, so do not rename it
as a side effect of another change.

## API rules

- REST endpoints under `/api/<feature>`, JSON bodies, UUID ids.
- Every error response is `{ "message": "Human readable reason" }`. Throw `ApiException`
  (`badRequest`, `notFound`, `conflict`); `GlobalExceptionHandler` turns it into the response.
- Validate request DTOs with Bean Validation annotations and clear messages.
- Updates use optimistic locking (JPA `@Version`); a stale `version` returns 409.
- The frontend depends on the contract in `property-manager/src/pdfme-demo/api/API_CONTRACT.md`.
  If an endpoint changes, update that contract and the frontend in the same piece of work.

## Database rules

- All schema changes go through Flyway: add a new file `V<next number>__<description>.sql`.
- Never edit a migration that has already been committed; it has run on other machines.
- `spring.jpa.hibernate.ddl-auto=validate`: Hibernate never changes the schema, so entity
  fields must match the migrations exactly.
- JSON documents (pdfme templates) are stored as JSONB.

## Code style

- Follow the existing code: tab indentation, constructor injection, Java records for DTOs.
- Services hold the business rules and transactions (`@Transactional`); controllers stay thin.
- Short Javadoc on public classes and endpoints explaining what they do.
- Keep `.java`, `pom.xml` and `README.md` with Windows line endings (CRLF). `mvnw` must stay LF.

## Git and commits

- Never commit to `main`. Use a branch (`feature/...`, `fix/...`, `docs/...`) and a pull request.
- Conventional Commits: `feat(scope): ...`, `fix(scope): ...`, `refactor:`, `chore:`, `docs:`, `test:`.
- Add or update tests with every behaviour change.

## Security

- Never commit passwords, keys or tokens. Database settings come from `DB_URL`, `DB_USERNAME`
  and `DB_PASSWORD` (the defaults are for local Docker only).
- CORS origins come from `CORS_ALLOWED_ORIGINS`; do not allow `*`.
- Never return stack traces or internal details in error responses.
