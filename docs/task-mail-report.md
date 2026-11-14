# Task Mail — harden the `tour/` email pipeline

> Date: 2026-11-15
> Scope: `tour/` backend only. `fe-tour/` was read for route verification and **not modified**.
> No `.env` file was opened, printed, or changed. No mail secret is reproduced in this report.
> No commit was made.
> Status: all six tasks implemented and tested; full backend suite green.

## Summary

| # | Task | Result |
|---|---|---|
| 1 | Bounded async executor | `@EnableAsync` was already on `TourApplication`; new `AsyncConfig` supplies a bounded `mailTaskExecutor` (core 2 / max 4 / queue 50) that logs and drops on saturation instead of blocking. Off-thread test added. |
| 2 | SMTP timeouts | `connectiontimeout`, `timeout`, `writetimeout` = 5000 ms each, overridable by env. |
| 3 | Send after commit | New `AfterCommitMailDispatcher`; all five mail paths (register, profile email change, forgot password, booking confirm, booking cancel + departure cascade) now send only after commit. Rollback tests added. |
| 4 | Link correctness | `EmailService.buildLink` strips trailing slashes, so `APP_FRONTEND_URL` with or without `/` never produces `//`. `/verify-email` exists; `/reset-password` does **not** exist in `fe-tour` — report-only, not built. |
| 5 | UTF-8 | `spring.mail.default-encoding=UTF-8`; a MIME serialize → reparse test proves a Vietnamese subject and body survive unchanged. |
| 6 | Gmail gaps + checklist | Missing/optional Gmail properties documented below, plus a manual test checklist. Real test counts recorded. |

Changed files:

- `tour/src/main/java/com/j2ee/tour/common/AsyncConfig.java` (new)
- `tour/src/main/java/com/j2ee/tour/service/AfterCommitMailDispatcher.java` (new)
- `tour/src/main/java/com/j2ee/tour/service/EmailService.java`
- `tour/src/main/java/com/j2ee/tour/service/AuthService.java`
- `tour/src/main/java/com/j2ee/tour/service/ProfileService.java`
- `tour/src/main/java/com/j2ee/tour/service/BookingService.java`
- `tour/src/main/java/com/j2ee/tour/service/TourDepartureService.java`
- `tour/src/main/resources/application.properties`
- `tour/src/test/java/com/j2ee/tour/common/AsyncConfigTest.java` (new)
- `tour/src/test/java/com/j2ee/tour/service/AfterCommitMailDispatcherTest.java` (new)
- `tour/src/test/java/com/j2ee/tour/service/EmailServiceTest.java`
- `tour/src/test/java/com/j2ee/tour/service/AuthServiceTest.java`
- `tour/src/test/java/com/j2ee/tour/service/ProfileServiceTest.java`
- `tour/src/test/java/com/j2ee/tour/service/BookingServiceTest.java`
- `tour/src/test/java/com/j2ee/tour/service/TourDepartureCancelCascadeTest.java`
- `docs/task-mail-report.md` (this file)

---

## Task 1 — bounded async executor

### What was wrong

`EmailService` methods are `@Async`, but no executor was configured. Spring Boot therefore created its
default `SimpleAsyncTaskExecutor`, which starts a **new unbounded thread per task** and never queues or
rejects. A slow or hanging SMTP server (or a burst of bookings) could create unlimited mail threads and
starve the request threads.

`@EnableAsync` was already present on `TourApplication`, so no annotation change was needed:

```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TourApplication { ... }
```

### Fix

`common/AsyncConfig.java` implements `AsyncConfigurer` and defines the executor used by every `@Async`
method:

| Setting | Value | Reason |
|---|---|---|
| `corePoolSize` | 2 | A couple of concurrent SMTP conversations is plenty. |
| `maxPoolSize` | 4 | Hard ceiling on mail threads. |
| `queueCapacity` | 50 | Bounded backlog. |
| `threadNamePrefix` | `tour-mail-` | Makes mail threads obvious in logs/thread dumps. |
| rejected handler | custom | Logs `Mail task rejected: executor is saturated (...)` and **drops** the task. |
| `waitForTasksToCompleteOnShutdown` | `true` (10 s) | Let in-flight sends finish on graceful shutdown. |

The rejection handler deliberately does not block and does not throw: a saturated mail queue must not
slow down or fail a registration, booking, or cancellation. The trade-off is that the dropped email is
lost; the WARN line is the signal that the pool is undersized.

### Tests — `AsyncConfigTest` (3)

- `mailExecutorIsBounded` — asserts core 2, max 4, queue capacity 50, prefix `tour-mail-`.
- `saturatedExecutorDropsTheMailTaskWithAWarning` — invokes the real rejection handler and asserts the
  `dropping` warning is logged.
- `emailMethodsRunOffTheCallerThread` — boots a tiny `@EnableAsync` context with a mocked
  `JavaMailSender`, calls `sendEmailVerification`, and asserts the send happens on a `tour-mail-*`
  thread that is not the caller thread.

---

## Task 2 — SMTP timeouts

Before this change a blocked SMTP port could hold a mail thread indefinitely. `application.properties`
now sets all three JavaMail timeouts to **5000 ms**, inside the requested 5000–10000 ms window:

```properties
spring.mail.properties.mail.smtp.connectiontimeout=${SPRING_MAIL_SMTP_CONNECTIONTIMEOUT:5000}
spring.mail.properties.mail.smtp.timeout=${SPRING_MAIL_SMTP_TIMEOUT:5000}
spring.mail.properties.mail.smtp.writetimeout=${SPRING_MAIL_SMTP_WRITETIMEOUT:5000}
```

| Timeout | Meaning | Default |
|---|---|---|
| `connectiontimeout` | TCP connect to the SMTP server | 5000 ms |
| `timeout` | Socket read timeout | 5000 ms |
| `writetimeout` | Socket write timeout | 5000 ms |

Each value can be raised (for example to 10000) with the matching `SPRING_MAIL_SMTP_*` environment
variable without editing the file. `EmailServiceTest.smtpTimeoutsAndUtf8EncodingAreConfigured` reads
`application.properties` from the test classpath and asserts each default is between 5000 and 10000 ms.

---

## Task 3 — send only after the transaction commits

### What was wrong

All five mail calls happened **inside** the `@Transactional` service method. Because the mail methods
are `@Async`, the SMTP send started immediately, in parallel with the still-open transaction. Two bad
outcomes were possible:

1. A verification or reset email could be delivered with a token whose `auth_tokens` row was later
   rolled back — the user gets a permanently invalid link.
2. A booking confirmation/cancellation could be delivered for a booking that was rolled back — the
   user is told about a trip that does not exist.

### Fix

`service/AfterCommitMailDispatcher.java`:

```java
public void dispatch(Runnable mailTask) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        mailTask.run();                       // no transaction: keep old behaviour
        return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() {
            try { mailTask.run(); }
            catch (RuntimeException e) { log.warn("Mail task failed after commit: {}", e.getMessage()); }
        }
    });
}
```

- With an active transaction, the task is registered as an `afterCommit` callback, so it runs only if
  the commit succeeds.
- `afterCompletion(STATUS_ROLLED_BACK)` does nothing, so a rollback sends nothing.
- With no transaction (unit tests, non-transactional callers), the task runs immediately, preserving
  previous behaviour.
- The callback swallows `RuntimeException` so a mail failure can never turn a successful commit into a
  failure. `EmailService` already catches its own send exceptions, so this is a second safety net.

The task still runs through the `@Async` proxy, so `afterCommit` only **submits** the send; it does not
block the committing thread on SMTP.

### Call sites wired

| Service | Method | Email |
|---|---|---|
| `AuthService` | `register` | email verification |
| `AuthService` | `forgotPassword` | password reset |
| `ProfileService` | `updateProfile` (email changed) | email verification |
| `BookingService` | `create` | booking confirmation |
| `BookingService` | `cancel` | booking cancellation |
| `TourDepartureService` | `cascadeCancel` (departure cancelled) | cancellation per owner |

Each call site now captures the recipient/code/total into locals **before** registering the lambda, so
the callback never touches a lazy JPA association after the transaction has closed.

### Tests (8 new)

`AfterCommitMailDispatcherTest` (3):

- `runsImmediatelyWhenNoTransactionIsActive`
- `defersTheSendUntilTheTransactionCommits`
- `sendsNothingWhenTheTransactionRollsBack`

Service-level deferral/rollback tests:

- `AuthServiceTest`: `registerDefersTheVerificationEmailUntilCommit`,
  `rolledBackRegistrationSendsNoVerificationEmail`,
  `forgotPasswordDefersTheResetEmailUntilCommit`
- `ProfileServiceTest`: `changedEmailDefersTheVerificationEmailUntilCommit`
- `BookingServiceTest`: `createDefersTheConfirmationEmailUntilCommit`,
  `cancellationDefersTheEmailUntilCommit`, `rolledBackCancellationSendsNoEmail`
- `TourDepartureCancelCascadeTest`: `cascadeDefersCancellationEmailsUntilCommit`

The existing `registerSendsEmailVerificationToken`, `cancellationSendsEmail`,
`createSendsConfirmationEmail`, etc. still pass because the dispatcher runs immediately when no
transaction is active.

### Note for integration tests

The `@SpringBootTest` integration tests are `@Transactional`, so their service calls now defer mail to
a commit that the test rolls back. They therefore send **no real email** during tests. That is the
intended behaviour and removes the previous risk of tests hitting a configured SMTP server.

---

## Task 4 — link correctness against real frontend routes

### Trailing-slash bug

Before:

```java
String link = frontendUrl + "/verify-email?token=" + token;
```

With `APP_FRONTEND_URL=http://localhost:3000/` this produced
`http://localhost:3000//verify-email?token=...`. A double slash is not the same route for every
server/proxy and can 404.

After — `EmailService.buildLink`:

```java
static String buildLink(String baseUrl, String path) {
    String base = baseUrl == null ? "" : baseUrl.trim();
    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    String suffix = path.startsWith("/") ? path : "/" + path;
    return base + suffix;
}
```

Both token emails use it. Booking emails contain no links, so they need no change.

### Route verification (read-only check of `fe-tour/app`)

| Email link | Frontend route | Exists? |
|---|---|---|
| `/verify-email?token=...` | `fe-tour/app/verify-email/page.tsx` | **Yes** |
| `/reset-password?token=...` | `fe-tour/app/reset-password/` | **No — route is absent** |

`fe-tour/app` contains: `account`, `admin`, `invoices`, `login`, `my-trips`, `payment`, `register`,
`tours`, `verify-email` (and the root page). There is **no** `reset-password` directory.

**Report only:** the backend reset email points at `/reset-password`, but the frontend has no such
page. Until that page is built, the reset flow must be completed with
`POST /api/auth/reset-password` (token + new password) or the link will land on the app's 404. This
task did not add the page because `fe-tour/` is out of scope. Either add
`fe-tour/app/reset-password/page.tsx` later, or point the email at an existing route if the product
decision changes.

### Tests

- `frontendUrlWithTrailingSlashesNeverProducesADoubleSlash` — no slash, one slash, three slashes, and a
  base URL with a sub-path.
- `trailingSlashFrontendUrlLogsASingleSlashLink` — the dev fallback link logged when SMTP is
  unconfigured contains exactly one slash and never `//verify-email`.

---

## Task 5 — UTF-8 subject and body

`application.properties` now sets:

```properties
spring.mail.default-encoding=UTF-8
```

Boot passes this to `JavaMailSenderImpl`, which encodes the `SimpleMailMessage` subject and body with
UTF-8. The existing copy is English, but recipient names, Vietnamese tour names, or future localized
templates must not be mangled.

### How it is verified

`vietnameseSubjectAndBodySurviveMimeEncoding`:

1. Builds a real `JavaMailSenderImpl` (subclass captures the `MimeMessage` instead of opening a socket)
   with `defaultEncoding=UTF-8`.
2. Sends subject `Xác nhận đặt tour Hành Trình` and body
   `Chào bạn, chuyến đi Đà Nẵng đã được xác nhận.`
3. Serializes the MIME message to bytes, asserts the raw message contains `charset=UTF-8`, then
   reparses it in a fresh `Session`.
4. Asserts the reparsed subject and content equal the original Vietnamese strings exactly.

This proves the encoding on the wire, not just the in-memory object.

---

## Task 6 — Gmail configuration gaps and manual checklist

### Already configured (verified by reading `application.properties`, not `.env`)

| Property | Env var | Notes |
|---|---|---|
| `spring.mail.host` | `SPRING_MAIL_HOST` / `MAIL_HOST` | Sending is disabled when blank. |
| `spring.mail.port` | `SPRING_MAIL_PORT` / `MAIL_PORT` | Default 587. |
| `spring.mail.username` | `SPRING_MAIL_USERNAME` / `MAIL_USERNAME` | Gmail address. |
| `spring.mail.password` | `SPRING_MAIL_PASSWORD` / `MAIL_PASSWORD` | Must be a Gmail **App Password**. |
| `mail.smtp.auth` | `SPRING_MAIL_SMTP_AUTH` / `MAIL_SMTP_AUTH` | Default `true`. |
| `mail.smtp.starttls.enable` | `SPRING_MAIL_SMTP_STARTTLS` / `MAIL_SMTP_STARTTLS` | Default `true`. |
| `spring.mail.default-encoding` | — | `UTF-8` (new). |
| timeouts | `SPRING_MAIL_SMTP_*TIMEOUT` | 5000 ms (new). |
| `app.mail.from` | `MAIL_FROM` | Default `no-reply@tour.local`. |

### Gaps / recommendations (report only — not changed)

| Property | Status | Why it matters |
|---|---|---|
| `MAIL_FROM` | **Most common Gmail failure** | It must equal the Gmail account or a verified "Send mail as" alias. If it stays `no-reply@tour.local`, Gmail rewrites or rejects the sender. Set `MAIL_FROM=<same address as SPRING_MAIL_USERNAME>`. |
| `mail.smtp.starttls.required` | **Not set** | `starttls.enable=true` asks for STARTTLS but allows a plaintext fallback if the server does not offer it. For Gmail this is not a practical risk, but `starttls.required=true` is the stricter setting. Add it if the deployment must fail closed. |
| `mail.smtp.ssl.enable` | **Not set** | Only needed for port 465 (implicit TLS). Current config is 587 + STARTTLS, so do not set both. |
| `spring.mail.test-connection` | **Not set** | Optional startup check. Leave off: with it on, a temporarily unreachable Gmail would prevent the whole app from starting. |
| `mail.smtp.connectionpool` | **Not set** | Each send opens a new connection. Fine at this volume; a pool is an optimization, not a fix. |
| Gmail App Password | **Required** | A normal Google password is rejected. 2-Step Verification must be on, then create an app password. Rotate it if it leaks; never commit `.env`. |
| `spring.mail.protocol` | Not set | Defaults to `smtp`, correct here. |

No secret values are printed in this report. `tour/.env` was not read.

### Manual test checklist

Run the backend with real Gmail settings (`SPRING_MAIL_HOST=smtp.gmail.com`, port 587, app password,
`MAIL_FROM` = the Gmail address). Then:

1. **Register** a new account.
   - Response is `200`/`201` and returns promptly (mail is async).
   - A verification email arrives within a few seconds.
   - Open `/verify-email?token=...` → the page reaches "You are all set." (see Task Fix 2).
2. **Forgot password** for that account.
   - A reset email arrives.
   - The link is `APP_FRONTEND_URL/reset-password?token=...`; expect a **404** in the current
     frontend because that page does not exist. Complete the flow with
     `POST /api/auth/reset-password` to prove the token itself works.
3. **Profile email change** (logged in) to a second address.
   - A verification email arrives at the **new** address.
   - The old address receives nothing.
4. **Create a booking**.
   - A confirmation email arrives with the booking code and total.
5. **Cancel that booking**.
   - A cancellation email arrives for the same code.
6. **Admin cancels a departure** that has bookings.
   - Every affected owner receives a cancellation email, and refund rows are created. `COMPLETED`
     and already-`CANCELLED` bookings receive nothing.
7. **Trailing slash**: set `APP_FRONTEND_URL=http://localhost:3000/`, restart, repeat step 1.
   - The link in the email has a single slash (`...:3000/verify-email?...`), never `//`.
8. **SMTP failure**: temporarily use a wrong app password.
   - Register/booking still succeeds.
   - The log shows `Failed to send email '<subject>' to <address>: ...` and **no token**.
9. **No SMTP**: blank `SPRING_MAIL_HOST`, repeat step 1.
   - The log shows the skip reason plus the dev-only manual link; no email is sent.
10. **Rollback** (covered by tests, hard to force by hand): if a transaction fails after a mail was
    queued, no email is delivered. The automated rollback tests are the proof.

---

## Verification (real counts)

Targeted run from `tour/`:

```powershell
.\mvnw.cmd -q "-Dtest=EmailServiceTest,AfterCommitMailDispatcherTest,AsyncConfigTest,AuthServiceTest,ProfileServiceTest,BookingServiceTest,TourDepartureCancelCascadeTest" test
```

| Test class | Tests | Failures |
|---|---|---|
| `AsyncConfigTest` | 3 | 0 |
| `AfterCommitMailDispatcherTest` | 3 | 0 |
| `EmailServiceTest` | 10 | 0 |
| `AuthServiceTest` | 24 | 0 |
| `ProfileServiceTest` | 11 | 0 |
| `BookingServiceTest` | 34 | 0 |
| `TourDepartureCancelCascadeTest` | 3 | 0 |
| **Targeted total** | **88** | **0** |

Full suite from `tour/`:

```powershell
.\mvnw.cmd test
```

Result: **271 tests run, 0 failures, 0 errors, 1 skipped — BUILD SUCCESS** (3:34 min).

The single skipped test is the pre-existing `TourApplicationTests.contextLoads`, which is skipped
because it needs PostgreSQL. The suite grew from 253 to 271 (+18) exactly because of the new tests
listed above.

---

## Behavioural notes and trade-offs

- **Dropped email on saturation is intentional.** The executor logs and drops rather than blocking a
  user request. If mail loss becomes unacceptable, raise the queue or add a persistent outbox; do not
  switch back to an unbounded executor.
- **After-commit means a rolled-back transaction sends nothing.** This is the desired guarantee, and
  it also means `@Transactional` integration tests no longer deliver real mail.
- **`/reset-password` remains missing in the frontend.** The backend link is correct for the intended
  route; the page must be built in `fe-tour/` in a separate task.
- **No retry logic was added.** A failed send is logged and forgotten, matching the previous
  fire-and-forget contract. A retry/outbox design is a larger change.
- **No `.env` was read or modified; no secret is in this report; nothing was committed.**
