# Local / Portfolio Runtime Readiness v1

This is the normal, non-production local startup path for AIRA on Windows. It runs the Spring Boot API and Vite web application locally and uses an existing PostgreSQL server. Docker is not required.

The Official Demo Bootstrap is optional and separate from normal startup. Use it only after this runtime works: [Official Demo Bootstrap & Runbook v1](OFFICIAL_DEMO_BOOTSTRAP_V1.md).

## Prerequisites

- JDK 25 (`java --version`)
- PostgreSQL reachable on localhost (PostgreSQL 18 is the validated version)
- Node.js 24+ and npm (`node --version`, `npm.cmd --version`)
- A PostgreSQL login allowed to connect to a dedicated AIRA local database

Do not drop, clean, truncate, reset, or repurpose an existing database. In particular, the startup script rejects the database name `aira` to protect the existing development database.

## First-time database setup

Create a separate database named `aira_local`. You may use pgAdmin, or the PostgreSQL `createdb` client below. The client prompts for the password without putting it in the command or repository.

```powershell
createdb.exe --host 127.0.0.1 --port 5432 --username postgres --password aira_local
```

If the command reports that the database already exists, do not delete it. Confirm that it is the intended AIRA local database before continuing, or choose a new name and pass it with `-DatabaseName`.

## Start the API and initialize the schema

From the repository root, run this in the first terminal:

```powershell
.\scripts\start-local-api.ps1 -DatabaseName aira_local -InitializeSchema
```

The script prompts for `DB_PASSWORD` with masked input when it is not already available in the current process. It never writes the password to a file. It also generates distinct 256-bit recovery-email keys for the API child process when the two recovery key environment variables are absent. Those generated keys are transient and are not printed or stored.

Flyway is enabled only when `-InitializeSchema` is supplied. It applies the existing versioned migrations; Flyway clean is never used. Hibernate then validates the resulting schema. The API listens on `http://127.0.0.1:8080`, and health is available at `http://127.0.0.1:8080/actuator/health`.

For persistent recovery-email behavior across restarts, provide stable, distinct Base64-encoded 256-bit values as `AIRA_RECOVERY_EMAIL_ENCRYPTION_KEY` and `AIRA_RECOVERY_EMAIL_LOOKUP_KEY` in a secure process or secret manager before starting. Do not put them in a tracked `.env` or properties file. Transient generated keys are suitable only when recovery-email records do not need to survive key rotation.

The script sets the session cookie's `Secure` flag to false only inside this localhost HTTP process. Keep the application default (`true`) for HTTPS environments.

## Start the web application

Run this in a second terminal from the repository root. The install is needed once after a clean checkout or dependency change.

```powershell
npm.cmd install
npm.cmd run dev:web
```

Open `http://127.0.0.1:5173`. Vite proxies relative `/api` requests to `http://127.0.0.1:8080`; no global CORS change is required.

## Verify the integrated runtime

With both processes running, use a third terminal:

```powershell
.\scripts\test-local-runtime.ps1
```

This checks the existing Actuator health endpoint, the rendered web entry point, and `/api/companies` through the Vite proxy. All three checks must report `PASS`.

## Stop and restart

Stop Vite and Spring Boot with `Ctrl+C` in their respective terminals. They shut down without resetting PostgreSQL.

For subsequent API starts, omit `-InitializeSchema`:

```powershell
.\scripts\start-local-api.ps1 -DatabaseName aira_local
```

The existing schema is validated and retained. Re-run the integration check after both processes restart.

## Safe failure handling

- Connection or authentication failure: verify the host, port, user, database name, and masked password. Do not paste credentials into logs or chat.
- Database does not exist: create a new dedicated database; do not rename or reset an unrelated database.
- Schema validation failure on first start: stop the API and retry with `-InitializeSchema` only for the intended dedicated database.
- Port 8080 or 5173 already in use: stop the conflicting local process. The checked-in Vite proxy expects API port 8080.
- Recovery key validation failure: supply two different Base64-encoded 256-bit secrets through the process environment; never commit them.
- `npm` PowerShell execution-policy error: invoke `npm.cmd`, as shown above, instead of `npm`.

Normal runtime does not require `OPENDART_API_KEY`, Resend credentials, demo bootstrap flags, or any external AI call.

## Optional account-recovery email delivery

The account-recovery screens are available in the normal web application, but sending verification and password-reset links requires the existing Resend adapter. Before API startup, provide `RESEND_API_KEY`, `RESEND_FROM_EMAIL`, and an HTTPS `AIRA_PUBLIC_BASE_URL` through a secure process environment. The public base URL must route `/recovery-email/confirm` and `/password-reset/confirm` back to the Vite/Spring application.

Without those optional values, normal login, signup, and financial browsing still work; email-delivery requests fail safely and no recovery token is shown in the product UI or logs. Do not expose a token manually to bypass email delivery.
