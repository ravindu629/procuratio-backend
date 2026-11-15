# pdfmedemo (backend)

Spring Boot API that saves pdfme templates in PostgreSQL for the frontend's **pdfme demo**
section. The endpoints match the frontend file `src/pdfme-demo/api/API_CONTRACT.md`.

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
   Flyway creates the tables on start-up. The API runs on http://localhost:8082.

3. **Point the frontend at it**: add this line to the frontend's `.env`, then restart `npm run dev`:
   ```
   VITE_TEMPLATE_API_URL=http://localhost:8082
   ```

## Endpoints

| Method | Path | Success |
| --- | --- | --- |
| GET | `/api/templates` | 200, list (newest first, no template JSON) |
| GET | `/api/templates/{id}` | 200, one template |
| POST | `/api/templates` | 201, created (version 1) |
| PUT | `/api/templates/{id}` | 200, updated (body needs the loaded `version`, otherwise 409) |
| DELETE | `/api/templates/{id}` | 204 |

Errors always look like `{ "message": "..." }`.

## Tests

```powershell
.\mvnw test
```

The tests start a real embedded PostgreSQL 16 automatically (no Docker needed).

## Code layout

```
src/main/java/com/ceybertech/pdfmedemo/
  template/  TemplateController (endpoints), TemplateService (rules),
             TemplateRepository (database), PdfTemplate (table row), dto/ (JSON shapes)
  common/    GlobalExceptionHandler and error classes
  config/    CorsConfig (lets the React app call the API)
src/main/resources/
  application.properties              settings (database, CORS)
  db/migration/V1__create_pdf_templates.sql
```

## Settings (environment variables)

| Variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/procuratio` |
| `DB_USERNAME` / `DB_PASSWORD` | `procuratio` / `procuratio` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |
