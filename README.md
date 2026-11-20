# pdfmedemo (backend)

Spring Boot API that stores PDF templates, with drafts, publishing and version history, for
the frontend's **Template Studio**. The endpoints match the frontend file
`src/pdfme-demo/api/API_CONTRACT.md`.

Spring Boot 4.1.2 · Java 21 · PostgreSQL 16 · Flyway · Maven wrapper

## Run it locally

1. **Start PostgreSQL** (needs Docker Desktop):
   ```powershell
   docker compose up -d
   ```
   Without Docker, install PostgreSQL 16 and create a database `procuratio` with user and
   password `procuratio` (or set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).

2. **Start the API** (needs JDK 21):
   ```powershell
   .\mvnw spring-boot:run
   ```
   Flyway creates the tables on start-up (and V2 copies any old `pdf_templates` rows into the
   new tables). The API runs on http://localhost:8082.

3. **Point the frontend at it**: add this line to the frontend's `.env`, then restart `npm run dev`:
   ```
   VITE_TEMPLATE_API_URL=http://localhost:8082
   ```

## Endpoints

| Method | Path | Success |
| --- | --- | --- |
| GET | `/api/document-types` | 200, active document types |
| GET | `/api/templates` | 200, list (newest first, no designs); filters `documentTypeId`, `tag`, `ownerUserId`, `includeArchived` |
| GET | `/api/templates/{id}` | 200, one template with its current and draft versions |
| POST | `/api/templates` | 201, created as DRAFT with draft version 1 |
| PATCH | `/api/templates/{id}` | 200, rename, retag, change type, archive or restore (needs `rowVersion`) |
| DELETE | `/api/templates/{id}?rowVersion=n` | 204, soft delete |
| PUT | `/api/templates/{id}/draft` | 200, save the draft (needs `draftRowVersion`, or null for a new draft) |
| DELETE | `/api/templates/{id}/draft?rowVersion=n` | 204, discard the draft |
| POST | `/api/templates/{id}/publish` | 200, the draft becomes the current version |
| GET | `/api/templates/{id}/versions` | 200, version history (newest first, no designs) |
| GET | `/api/templates/{id}/versions/{versionNo}` | 200, one version with its design |

Errors always look like `{ "message": "..." }`. A stale `rowVersion` or `draftRowVersion` gives 409.
Users: every `*By` column and `ownerUserId` comes from `audit/CurrentUser`, which returns the
system user `00000000-0000-0000-0000-000000000000` until login exists.

## Tests

```powershell
.\mvnw test
```

The tests start a real embedded PostgreSQL 16 automatically (no Docker needed). Test names start
with the scenario IDs from the "Template Scenarios" doc (for example `PV-04`).

## Code layout

```
src/main/java/com/ceybertech/pdfmedemo/
  template/  TemplateController and DocumentTypeController (endpoints), TemplateService (rules),
             Template, TemplateVersion, DocumentType (table rows), repositories, dto/ (JSON shapes)
  audit/     CurrentUser (who is doing this) and AuditConfig (fills created_by, updated_by ...)
  common/    GlobalExceptionHandler and error classes
  config/    CorsConfig (lets the React app call the API)
src/main/resources/
  application.properties              settings (database, CORS)
  db/migration/V1__create_pdf_templates.sql   old single table (dropped by a later migration)
  db/migration/V2__create_template_schema.sql template schema: document_type, template, template_version
```

## Settings (environment variables)

| Variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/procuratio` |
| `DB_USERNAME` / `DB_PASSWORD` | `procuratio` / `procuratio` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |
