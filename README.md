# Transactions Dispute Portal

A Spring Boot + Thymeleaf web app where customers view their transactions, dispute them, and follow
the outcome in a dispute history.

- Java 17, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway, Thymeleaf
- H2 for local development and tests, PostgreSQL for production
- Ships with a multi-stage `Dockerfile`, a `docker-compose.yml`, and a GitHub Actions workflow

## Contents

1. [Quick start](#quick-start)
2. [Build](#build)
3. [Run](#run)
4. [Test](#test)
5. [Configuration](#configuration)
6. [How it works](#how-it-works)
7. [Operations](#operations)
8. [Project structure](#project-structure)
9. [Known limitations](#known-limitations)

## Quick start

The fastest way to see it running (needs Docker with Compose v2):

```bash
cp .env.example .env        # then set DB_PASSWORD and DEMO_PASSWORD in .env
docker compose up --build
```

Open http://localhost:8080 and sign in with the `DEMO_PASSWORD` you chose:

| Username | Role | What you see |
|----------|------|--------------|
| `thandi` | Customer | Transactions, and disputes in every state |
| `sipho` | Customer | A second customer, to show customers only ever see their own data |
| `demo-admin` | Admin | The dispute queue, where you review and decide disputes |

## Build

**Prerequisites:** JDK 17+ and Maven 3.9+ (only needed for a local build; the Docker build brings its own).

```bash
mvn clean package            # compiles, runs the tests, produces target/dispute-portal-0.1.0-SNAPSHOT.jar
mvn clean package -DskipTests
```

**Docker image** (the build stage compiles and runs the tests, so a failing test fails the image build):

```bash
docker build -t dispute-portal .
docker build --build-arg SKIP_TESTS=true -t dispute-portal .   # skip tests for a quicker local build
```

The image uses a JRE-only base, runs as a non-root user, and has a health check on
`/actuator/health/readiness`.

## Run

### Option 1: Docker Compose (app + PostgreSQL)

```bash
docker compose up --build        # start
docker compose down              # stop, keep data
docker compose down -v           # stop and delete the database volume
```

This runs the `prod,demo` profiles: PostgreSQL, Flyway migrations, and demo data. The session cookie is
set to non-`Secure` in this file so login works over plain `http://localhost`.

### Option 2: Docker, single container, no database setup

```bash
docker run --rm -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev -e APP_DEMO_PASSWORD=choose-a-password dispute-portal
```

Uses in-memory H2 and demo data. Data is lost when the container stops.

### Option 3: Docker against your own PostgreSQL

```bash
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://<host>:5432/<database> \
  -e DB_USERNAME=<user> -e DB_PASSWORD=<password> \
  dispute-portal
```

Runs the `prod` profile with no demo data. Flyway creates the schema on first start. Add
`-e APP_ADMIN_PASSWORD=<12+ characters>` to create an admin account (see [Admin](#admin-back-office)).
There is no customer sign-up screen yet; see [Known limitations](#known-limitations).

### Option 4: Maven, for development

```bash
mvn spring-boot:run
```

Uses the `dev` profile (the default): in-memory H2, template caching off, demo data. Sign in as `thandi`,
`sipho` or `demo-admin` with password `change-me-dev`, or set `DEMO_PASSWORD` first to use your own.

## Test

```bash
mvn test        # unit and MVC/security tests
mvn verify      # the full build, as CI runs it
```

| Test | Covers |
|------|--------|
| `DisputeServiceTest` (JUnit 5 + Mockito) | Raising a dispute, blocking a second open dispute, the dispute window, ownership (another customer's id is "not found") |
| `AdminDisputeServiceTest`, `DisputeStatusTest` | The review workflow: allowed status changes, note required for decisions, closed disputes are final |
| `LoginAttemptServiceTest` | Lockout after repeated failures, expiry of the window and the lockout, success resets, separate tracking per client and user |
| `LoginFlowTest` (MockMvc) | Real form logins: customer and admin land on the right page, wrong passwords give a generic error, repeated failures lock the client out |
| `TransactionLoadServiceTest` | Atomic loads, idempotent replays, conflicts, unknown customers, admins, repeated references, future dates |
| `ApiClientServiceTest` | Key hashing, unknown/disabled/expired keys, length checks |
| `TransactionLoadApiTest` (MockMvc) | The API end to end: 401/429 authentication, loading and replay, 409/422 errors, malformed JSON, wrong content type, oversized bodies |
| `CustomerProvisioningServiceTest` | Atomic account creation, hashed passwords, idempotent replays, conflicts, admins untouched, password rules (72 bytes, not the username) |
| `CustomerProvisionApiTest` (MockMvc) | The customer API end to end: scope separation between the two keys (403), accounts can log in and are always customers, no password in any response |
| `AdminSecurityTest` (MockMvc) | Admin pages need the admin role, customers get 403 (including calling the service directly), admins are kept out of customer pages, deciding a dispute works and invalid decisions are refused |
| `EvidenceServiceTest`, `NotificationServiceTest` (Mockito) | Evidence rules (ownership, open disputes only, size and count limits, real file type vs name, duplicates, file-name cleaning) and who is notified about what |
| `EvidenceAndNotificationFlowTest` (MockMvc) | Upload, listing and attachment download, CSRF, customer/admin separation, closed disputes, the notifications inbox and mark-as-read |
| `WebSecurityTest` (MockMvc) | Anonymous redirect to login, public health probe, customer pages render, 404 for unknown ids, CSRF enforcement, form validation errors |

The MVC tests start the full application against in-memory H2 with the demo data, so no external services are needed.

## Configuration

| Setting | Where | Default | Purpose |
|---------|-------|---------|---------|
| `SPRING_PROFILES_ACTIVE` | env | `dev` locally, `prod` in the image | `dev`: H2 + demo data. `prod`: PostgreSQL. `demo`: adds demo data to any profile |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | env (prod) | none, required | JDBC connection |
| `APP_DEMO_PASSWORD` | env (`demo` profile) | `change-me-dev` in `dev` only | Password for the demo customers and `demo-admin` |
| `app.security.login.max-attempts`, `window-minutes`, `lockout-minutes` | `application.yml` or env `APP_SECURITY_LOGIN_MAXATTEMPTS`, `..._WINDOWMINUTES`, `..._LOCKOUTMINUTES` | `5`, `15`, `15` | Login throttling: failures allowed within the window before a lockout, and how long it lasts |
| `APP_API_LOADER_KEY` (Compose: `LOADER_API_KEY`) | env | empty (dev: built-in dev key) | API key for the loading API (32+ characters). Empty means the API can't be used |
| `APP_API_PROVISIONER_KEY` (Compose: `PROVISIONER_API_KEY`) | env | empty (dev: built-in dev key) | API key for the customer-creation API (32+ characters, different from the loader key). Empty means that API can't be used |
| `app.api.require-https` (env `API_REQUIRE_HTTPS`) | env (prod) | `true` in prod, `false` otherwise | Refuse API calls that didn't arrive over HTTPS |
| `app.api.max-body-bytes` | `application.yml` | `1048576` | Largest accepted API request body |
| `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD` | env | `admin` / empty | Creates an admin on startup if the password is set (12+ characters) |
| `SESSION_COOKIE_SECURE` | env (prod) | `true` | Set `false` only for plain-HTTP local trials |
| `app.dispute.window-days` | `application.yml` | `120` | How long after a transaction it can be disputed |
| `EVIDENCE_DIR` (`app.evidence.storage-dir`) | env | `/data/evidence` in prod, the temp folder otherwise | Where uploaded evidence is written. In Docker this is a volume; back it up with the database |
| `app.evidence.max-file-bytes` | `application.yml` or env `APP_EVIDENCE_MAXFILEBYTES` | `5242880` (5 MB) | Largest accepted evidence file. The web server's upload limit follows it |
| `app.evidence.max-files-per-dispute` | `application.yml` or env `APP_EVIDENCE_MAXFILESPERDISPUTE` | `5` | Files one dispute may hold |
| `JAVA_TOOL_OPTIONS` | env | `-XX:MaxRAMPercentage=75 ...` (image) | JVM tuning |

Never commit real secrets. `.env` is git-ignored and `.env.example` documents the variables.

## How it works

| Page | Path | Notes |
|------|------|-------|
| Sign in | `/login` | Form login, BCrypt password hashes, CSRF protection |
| Transactions | `/transactions` | Paginated, newest first, with how many disputes were logged and closed on each. "Dispute" appears only when allowed |
| Dispute form | `/transactions/{id}/dispute` | Validated server-side; Post/Redirect/Get on success; lists earlier disputes on the same transaction |
| Dispute history | `/disputes` | Every dispute ever raised, a summary card per dispute (status, reason, dates, outcome note) linking to its details |
| Dispute details | `/disputes/{id}` | The customer's own dispute with its full progress timeline |
| Dispute queue (admin) | `/admin/disputes` | All customers' disputes, filterable by status and by the date raised (`?status=`, `?from=`, `?to=`, as `yyyy-MM-dd`) |
| Review dispute (admin) | `/admin/disputes/{id}` | See the details, full history and uploaded evidence, move the dispute along, write a note to the customer |
| Upload evidence | `POST /disputes/{id}/evidence` | Customer attaches a PDF, PNG or JPG to their own open dispute (form on the dispute details page) |
| Download evidence | `/disputes/{id}/evidence/{evidenceId}`, `/admin/disputes/{id}/evidence/{evidenceId}` | Customer (own disputes) or admin (any); always sent as an attachment |
| Notifications | `/notifications` | Inbox for both roles, with an unread badge in the header, "Mark as read" and "Mark all as read" |

Rules enforced in `DisputeService`:

- A transaction can't be disputed again while a dispute is `SUBMITTED` or `UNDER_REVIEW`. After a dispute is
  resolved or rejected it can be disputed again, and both stay in the history.
- Transactions older than `app.dispute.window-days` can't be disputed.
- All lookups are scoped to the signed-in customer in the query itself, so another customer's id returns 404.
- Raising a dispute locks the transaction row, so two simultaneous submissions can't both create an open dispute.

### Notifications

In-app only (there is no email or SMS channel; accounts have no email address). A notification is written in the same
database transaction as the change it describes, so it exists only if the change was saved:

| Event | Who is notified |
|-------|-----------------|
| A customer raises a dispute | every admin |
| A customer uploads evidence | every admin |
| An admin changes a dispute's status | the customer who raised it |

Each person sees and changes only their own notifications (scoped by username in the query; someone else's id is a 404).
All wording lives in `NotificationService`, so adding another channel later means calling it from those three methods.
Notifications are kept indefinitely; there is no clean-up job yet.

### Evidence uploads

Customers can attach supporting files to their own **open** disputes. `EvidenceService` enforces, on every upload:

- the dispute is the signed-in customer's own (someone else's id is a 404) and still `SUBMITTED` or `UNDER_REVIEW`;
- at most `app.evidence.max-file-bytes` per file (default 5 MB) and `app.evidence.max-files-per-dispute` per dispute (default 5);
- the **real** type: the file must start with the PDF, PNG or JPEG signature and its extension must agree. The type the
  browser claims is ignored;
- the same content can't be attached twice to one dispute (SHA-256).

Files are stored under a random key (never the user's file name) by `EvidenceStorage`; the database keeps only the
metadata (`dispute_evidence`). The default implementation writes to `app.evidence.storage-dir`. If the database
transaction fails after the file was written, the file is removed. Downloads are always `Content-Disposition: attachment`
with the detected type, so the browser never renders an uploaded file inside the site. The dispute row is locked during an
upload, so concurrent uploads can't pass the limit and nothing can be added while an admin is closing the dispute.

### Admin (back-office)

Admins sign in at the same `/login` page and land on the dispute queue. Customers and admins are kept apart:
admins can't open customer pages, and customers get a 403 on anything under `/admin`. The admin service also
checks the role itself (`@PreAuthorize`), so it stays protected if a new URL is ever exposed by mistake.

Workflow: `SUBMITTED` → `UNDER_REVIEW` → `RESOLVED_IN_FAVOUR` or `REJECTED` (review can be skipped).
Resolved and rejected disputes are final. A note is required when deciding, and the customer sees it in their
history. Every change is also written to an append-only history (`dispute_events`: from/to status, note, who, when) and
logged. Admins see the real usernames; customers see their own actions as "You" and staff actions as
"Dispute team". Two admins can't decide the same dispute at
once (row lock). After a final decision, the customer can raise a new dispute on that transaction.

**Creating the first admin**

- Demo profiles: `demo-admin` is created with the demo password.
- Real deployments: set `APP_ADMIN_PASSWORD` (12+ characters) and optionally `APP_ADMIN_USERNAME` before the first
  start. The account is created only if that username doesn't exist. Changing the variable later does **not** reset
  an existing admin's password, and the app refuses to start if the password is too short.
- There is no screen for managing admin accounts yet. To add more, insert a customer row with `role = 'ADMIN'` and a
  BCrypt password hash, or start the app once with a different `APP_ADMIN_USERNAME`.

## Loading API

A machine-to-machine endpoint for loading transactions. Full contract: [`docs/api/openapi.yaml`](docs/api/openapi.yaml).

`POST /api/v1/transactions/batch` with `Authorization: Bearer <api key>` and a JSON body of 1 to 500 transactions:

```bash
curl -X POST https://your-host/api/v1/transactions/batch \
  -H "Authorization: Bearer $LOADER_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"transactions":[{"reference":"CORE-2026-000123","customerUsername":"thandi","merchant":"Checkers Hyper",
        "description":"Card purchase","amount":842.35,"currency":"ZAR","transactionDate":"2026-10-13T08:30:00Z"}]}'
# {"received":1,"created":1,"duplicates":0}
```

**Behaviour**

- **Atomic:** if any item is invalid or conflicts, nothing is saved and the response lists every problem with its path
  (for example `transactions[2].amount`).
- **Idempotent:** `reference` is your unique id for a transaction. Re-sending a transaction that was already loaded with
  identical details counts as a `duplicate` and is ignored, so retrying after a timeout is safe. Re-using a reference with
  different details is a `409` conflict.
- **Validation:** known customer (admins can't own transactions), amount of at least 0.01 with at most 2 decimals,
  3-letter uppercase currency, ISO-8601 date with a zone and not in the future, field lengths as in the OpenAPI file.

| Status | Meaning |
|--------|---------|
| 200 | Processed (see `created` and `duplicates`) |
| 400 / 415 | Body not valid JSON / wrong content type |
| 401 | Missing, invalid, disabled or expired key |
| 403 | HTTPS required |
| 409 | Reference exists with different details; nothing saved |
| 413 | Body larger than 1 MiB |
| 422 | Invalid items; nothing saved |
| 429 | Too many failed authentication attempts from your address |

**Security**

- **Separate from the web login.** The API is stateless: no sessions, cookies or login form, and no CSRF token (there is no
  browser credential to forge). CORS is not enabled. Browser logins can't call it, and API keys can't open web pages.
- **Keys are never stored.** Only a SHA-256 hash is kept in `api_clients`, so a database leak doesn't reveal usable keys.
  Keys must be at least 32 characters. Generate one with `openssl rand -base64 48`. They are not logged and not echoed.
- **HTTPS required in `prod`** (`API_REQUIRE_HTTPS`, default `true`). Behind a TLS-terminating proxy, make sure it sends
  `X-Forwarded-Proto` so the app can see the request was secure. The key is a bearer secret: anyone who has it can load data.
- **Brute-force protection:** 5 invalid keys within 15 minutes from one address locks that address out of the API for
  15 minutes (`429`). This is per instance; also rate-limit at your gateway.
- **Limits:** 500 transactions and 1 MiB per request (enforced even for chunked bodies), fixed-text error messages that
  never echo request data, and counts only (never amounts or customers) in the logs.
- **Defence in depth:** the load service also checks the API role itself (`@PreAuthorize`).

**Setting up keys**

- Set `LOADER_API_KEY` (Compose) or `APP_API_LOADER_KEY` (any other deployment) before starting. The app registers a client
  named `transaction-loader`, with scope `transactions:load`, using the hash of that key. For the customer-creation API set
  `PROVISIONER_API_KEY` / `APP_API_PROVISIONER_KEY` the same way (client `customer-provisioner`, scope `customers:create`). The `dev` profile uses a built-in development key; never use it for
  anything real.
- **Rotation:** change the variable and restart. The stored hash is replaced and the old key stops working immediately.
- **Revoking:** set `enabled = false` (or `expires_at`) on the row in `api_clients`, or remove the variable and delete the
  row. There is no screen for managing API clients yet.

## Customer-creation API

A second machine-to-machine endpoint, for creating customer accounts from another system. Contract:
[`docs/api/openapi.yaml`](docs/api/openapi.yaml). It uses the same transport and error format as the loading API, but a
**different API key with a different permission**.

`POST /api/v1/customers/batch` with `Authorization: Bearer <provisioner key>` and 1 to 100 accounts:

```bash
curl -X POST https://your-host/api/v1/customers/batch \
  -H "Authorization: Bearer $PROVISIONER_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"customers":[{"username":"thandi.nkosi","fullName":"Thandi Nkosi","password":"a-long-initial-password"}]}'
# {"received":1,"created":1,"duplicates":0}
```

**Rules**

- `username`: 3 to 50 characters, lowercase letters, digits, `.`, `_`, `-`. `fullName`: up to 100 characters.
  `password`: 12 to 72 characters (BCrypt ignores anything past 72 bytes, so longer ones are refused, not cut short) and not
  equal to the username.
- **Always a customer.** There is no way to create an admin through the API; a `role` field in the body is ignored.
- **Atomic and idempotent**, like the loading API. Re-sending an account that exists with the same name and password counts
  as a `duplicate`. An existing username with different details (or an existing admin) is a `409`, and nothing is saved.
- A batch is limited to 100 accounts because every password is hashed with BCrypt, which is deliberately slow.

**Security**

- **Scopes (least privilege).** Each API key can do one thing. The loader key has scope `transactions:load`; the provisioner key
  has `customers:create`. A key used on the other endpoint gets `403`, and the two keys must be different (the app refuses to
  start otherwise). Scopes are stored per client in `api_clients.scopes`.
- **The password is sensitive.** It travels only over HTTPS (enforced in `prod`), is passed straight to BCrypt, and is never
  stored in plain text, logged, or echoed in an error response. The request object's `toString()` masks it, so an accidental
  log of the object doesn't leak it. Logs record counts only, not the names of created accounts.
- All the protections of the loading API apply: hashed keys, lockout after repeated invalid keys, body size limit, no cookies
  or CSRF surface.
- **Set a strong initial password and make the user change it.** There is no password-change or reset screen yet, so an
  account's initial password stays its password until you add that. Treat the provisioner key as highly sensitive: whoever
  holds it can create accounts and therefore knows their initial passwords.

## Operations

- **Login protection:** after 5 failed logins within 15 minutes, that client address and username are locked out
  for 15 minutes (sent back to the login page with a "too many attempts" message). The error message is the same
  whether the username or the password was wrong. State is in memory, so it is per instance and resets on restart;
  with several instances, also rate-limit `/login` at your gateway. Behind a proxy, make sure it passes
  `X-Forwarded-For` so lockouts apply to the real client address.
- **Health:** `GET /actuator/health/liveness` and `/actuator/health/readiness` (public, no details). Readiness
  includes the database. Nothing else from Actuator is exposed.
- **Logging:** SLF4J/Logback to stdout. Each line carries a correlation ID, also returned as `X-Correlation-Id`.
  Passwords, tokens and stack traces are not logged or shown to users.
- **Shutdown:** graceful, with a 20 second drain.
- **Evidence storage:** uploaded files live outside the database (`app.evidence.storage-dir`; the `evidence-data` volume
  in Compose). Back the folder up together with the database and restore them as a pair: a database row without its file
  shows as "not found" on download. Check free disk space; the worst case is files-per-dispute x max size x disputes.
- **Schema changes:** add a new `src/main/resources/db/migration/V<n>__description.sql`. Hibernate never alters the
  schema. Take a database backup before deploying a migration.
- **Time:** all timestamps are stored and handled in UTC and shown to users in South African Standard Time (SAST, UTC+2), with "SAST" after times of day. The conversion is in `DisplayTime`; the admin queue's date filter also uses South African days.
- **CI:** `.github/workflows/ci.yml` runs `mvn verify` and a Docker build on every push and pull request.

## Project structure

```
Dockerfile, docker-compose.yml, .env.example, .github/workflows/ci.yml
src/main/java/com/example/disputeportal/
  config/      Clock, DisputeProperties, correlation-ID filter, demo seeder
  controller/  Thin MVC controllers
  dto/         Form and view records (entities never reach templates)
  entity/      JPA entities and enums
  exception/   Custom exceptions and @ControllerAdvice
  repository/  Spring Data repositories
  security/    SecurityConfig, UserDetailsService
  service/     Business logic (including evidence storage and notifications)
src/main/resources/
  db/migration/  Flyway SQL
  templates/     Thymeleaf pages, fragments, error pages
  static/css/    Stylesheet
  application*.yml
```

## Code tour

**Where to start reading.** `DisputePortalApplication` is the entry point. Then read in this order: `entity/` (the data),
`service/` (the rules), `controller/` (thin web layer) and `resources/templates/` (the pages), and finally
`security/SecurityConfig` (who may do what). Every class and non-trivial method has a comment explaining what it is for and,
where it isn't obvious, why it is written that way.

**How the main flows work**

- *A customer raises a dispute:* `DisputeController.submit` validates the form (`DisputeForm`) and calls
  `DisputeService.raise`. That locks the transaction row, checks the dispute window and the "one open dispute" rule,
  saves the `Dispute` and its first `DisputeEvent`, and the controller redirects to the history page.
- *An admin decides a dispute:* `AdminDisputeController.decide` calls `AdminDisputeService.updateStatus`, which locks the
  dispute, checks the workflow in `DisputeStatus.canTransitionTo`, requires a note for final decisions, updates the dispute
  and appends a `DisputeEvent`. The customer sees the note and the new timeline entry.
- *A customer uploads evidence:* `EvidenceController.upload` passes the file to `EvidenceService.upload`, which locks the
  dispute, checks ownership, status, size, type, count and duplicates, writes the file through `EvidenceStorage`, saves the
  `DisputeEvidence` row and asks `NotificationService` to tell the admins.
- *A system loads transactions:* `ApiKeyAuthenticationFilter` checks the API key and size limits, then
  `TransactionLoadController` hands the validated batch to `TransactionLoadService`, which saves it atomically.
- *A web login:* Spring Security's form login uses `CustomerUserDetailsService` to find the account;
  `LoginThrottleFilter`, `LoginFailureHandler` and `RoleBasedSuccessHandler` add lockout and the role-based landing page.

**Conventions**

- Business rules live in services; controllers only translate between HTTP and services; templates only display.
- Every lookup that returns a customer's data is scoped to the signed-in username in the query itself.
- Templates never touch lazily loaded entities: services return flat records (`dto/`).
- Comments in `.html` templates use Thymeleaf's `<!--/* ... */-->` form, which is removed before the page is sent, so they
  never reach the browser.

**Database schema** (the SQL migrations carry no comments on purpose: Flyway checksums the whole file, so editing an
applied migration would stop the app from starting. Add a new `Vn__*.sql` file for any change, never edit an old one.)

| Table | Purpose |
|-------|---------|
| `customers` | Everyone who can sign in. `role` is `CUSTOMER` or `ADMIN`; `password_hash` is BCrypt |
| `transactions` | A customer's card/account transactions. `reference` is unique (makes API loads idempotent); timestamps are UTC |
| `disputes` | A dispute raised on a transaction: reason, the customer's description, current status, the admin's note and who last changed it |
| `dispute_events` | Append-only history of every dispute: each status change with the note, who did it and when |
| `notifications` | In-app messages: recipient username, dispute, text, created and read times |
| `dispute_evidence` | Metadata of each uploaded file: name, detected type, size, random storage key, SHA-256, who and when. The bytes are in the evidence storage |
| `api_clients` | Machine clients of the loading API. Only a SHA-256 hash of each key is stored |

## Troubleshooting

- **The app container keeps restarting.** Run `docker compose logs app --tail 150` and look for the first
  `Caused by:` or `Application run failed` line. The app retries 5 times, then stays stopped so the error is easy to see.
- **`app.admin.password ... is only N characters`.** `ADMIN_PASSWORD` is set to something shorter than 12 characters.
  Common causes: a short value in `.env`, an `ADMIN_PASSWORD` exported in your shell (it overrides `.env`), or literal
  quote characters (`ADMIN_PASSWORD=""` is a 2-character password in some setups). Fix the value or delete the line,
  then run `docker compose up -d --force-recreate`.
- **`password authentication failed` for the database.** PostgreSQL keeps the password from its first start. After
  changing `DB_PASSWORD`, run `docker compose down -v` (this deletes the data) and start again.

## Known limitations

- **No customer sign-up or password reset.** Customers exist only through the `demo` profile. A real deployment needs
  an onboarding path or an integration with your identity provider.
- **No admin account management** or separate admin roles (all admins can decide all disputes). Notifications are in-app
  only (no email or SMS) and are never purged.
- **The audit history can still be edited by anyone with direct database access.** The app only inserts into it.
  For stricter compliance, restrict database permissions or ship the events to an external log store.
- **Login throttling is per instance** (in memory). It is not a substitute for gateway-level rate limiting, MFA or
  an identity provider for admin accounts.
- **No screen for API clients.** Keys are set through configuration: one loader and one provisioner client. Managing several clients, scoping
  a client to certain customers, and a per-batch audit table are not built (batches are logged as counts only).
- **Evidence is not virus-scanned.** Type and size are checked and files are only ever served as downloads, but a
  production deployment should scan uploads (for example with ClamAV) before admins open them. Only customers can upload;
  admins can't request or attach files, and there is no way to delete evidence.
- **An upload larger than the limit shows a 403 page, not a friendly message.** The web server stops reading the request
  before Spring Security can see the CSRF token. The form states the limit; smaller mistakes (wrong type, empty file,
  too many files) all get a clear message on the dispute page.
- **Transactions can be loaded through the API, but there is no scheduled feed** from a core banking system.
- **Only the health endpoint is exposed.** Add metrics and tracing to match your monitoring stack.
- **Not run by the author.** Run `mvn verify` and `docker build` in your environment before relying on it.
