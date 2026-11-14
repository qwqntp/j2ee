# Task Fix 1 — email delivery diagnosis + invoice discount verification

> Date: 2026-11-14
> Scope: `tour/` backend only. `fe-tour/` was not touched. No commit was made.
> Status: Task 1 fixed and tested; Task 2 verified as already correct, with a new integration test.

## Summary

| Task | Result |
|---|---|
| 1 — verification email never arrives | Root cause found: `EmailService` **silently skipped** sending (it never threw). Skip/failure reasons now log a specific `WARN`, and a dev-only token link is logged when SMTP is unconfigured. |
| 2 — invoice discount/total | All automatic invoice paths already copy `booking.bookingDiscountAmount` and `booking.bookingTotalAmount`. No production change and **no SQL `UPDATE`** are required. Added `paidBookingInvoiceMatchesPromotionDiscountAndTotal`. |

Changed files:

- `tour/src/main/java/com/j2ee/tour/service/EmailService.java`
- `tour/src/test/java/com/j2ee/tour/service/EmailServiceTest.java`
- `tour/src/test/java/com/j2ee/tour/BookingFlowIntegrationTests.java`
- `docs/task-fix-1-report.md` (this file)

---

# TASK 1 — verification email is not received

## What `EmailService` did before the fix

The `auth_tokens` row is created by `ProfileService`, so the failure was purely in delivery. `EmailService`
**did not throw** in any path. Every method is `@Async`, so the calling transaction already returned
success even if mail failed. Inside the shared private `send(...)` method:

| Condition | Old behavior | Effect |
|---|---|---|
| `to` is `null` or blank | `return` with **no log at all** | Completely silent skip |
| `spring.mail.host` blank (no SMTP configured) | one generic `WARN` guarded by an `AtomicBoolean`, so only the **first** skip in the JVM was logged | Email skipped; token link **never logged** |
| `spring.mail.host` set but no `JavaMailSender` bean | same one-time generic `WARN` | Email skipped; token link never logged |
| `JavaMailSender.send(...)` throws | `WARN "Failed to send email to {}: {}"` | Exception swallowed; token link not logged |

So the observed symptom is expected: the token exists in the database, the HTTP request returns `200`,
and no email is delivered. In a default dev setup (`SPRING_MAIL_HOST` empty) the only trace was a single
generic warning that did not identify which email was skipped and did not expose the link needed to
complete verification manually.

The same `send(...)` method is used by all four email types, so **register-verification** and
**forgot-password** had exactly the same behavior:

- `AuthService.register(...)` → `emailService.sendEmailVerification(...)`
- `AuthService.forgotPassword(...)` → `emailService.sendPasswordReset(...)`
- `ProfileService.updateProfile(...)` (email change) → `emailService.sendEmailVerification(...)`
- booking confirmation / cancellation → same `send(...)`

## What `EmailService` does now

`EmailService.send(to, subject, body, manualLink)` checks each condition separately and logs a specific
reason. It still never throws, so email can never roll back a registration, booking, profile change, or
cancellation.

| Condition | New log (exact message) | Token link logged? |
|---|---|---|
| blank recipient | `Skipped email '<subject>': no recipient address is set` | No |
| blank `spring.mail.host` | `Skipped email '<subject>': spring.mail.host is not configured (set SPRING_MAIL_HOST or MAIL_HOST)` | **Yes — dev only** |
| host set, no sender bean | `Skipped email '<subject>': no JavaMailSender bean is available; check spring.mail.* configuration` | No |
| send throws | `Failed to send email '<subject>' to <recipient>: <reason>` | No |
| dev fallback after blank host | `No SMTP configured; '<subject>' was not sent. Dev-only manual link: <link>` | Yes |

Only the verification and password-reset emails pass a `manualLink`:

- email verification → `${APP_FRONTEND_URL}/verify-email?token=<token>`
- password reset → `${APP_FRONTEND_URL}/reset-password?token=<token>`
- booking confirmation/cancellation pass `null`, so they never log a link.

The dev link is logged **only** when `spring.mail.host` is blank. It is deliberately suppressed when
SMTP is configured and when a send fails, so a one-time token is never leaked into production logs or
into logs from a configured-but-failing server.

### Secrets

The logger never prints `spring.mail.username`, `spring.mail.password`, the SMTP host credentials, or a
raw token on a failure. The only token-bearing line is the intentional dev fallback above, and only
while SMTP is unconfigured. Failure logs print the recipient address and the exception message, which
is what is needed to diagnose SMTP auth/connectivity problems.

### Manual dev flow

With no SMTP configured, change the profile email and read the backend log. It now contains:

```
WARN  c.j.t.service.EmailService : Skipped email 'Verify your tour email': spring.mail.host is not configured (set SPRING_MAIL_HOST or MAIL_HOST)
WARN  c.j.t.service.EmailService : No SMTP configured; 'Verify your tour email' was not sent. Dev-only manual link: http://localhost:3000/verify-email?token=<token>
```

Open that link (or `POST /api/auth/verify-email` with the token) to finish verification.

## Required configuration

`application.properties` maps Spring Mail from these environment variables (`.env` is imported via
`spring.config.import=optional:file:.env[.properties]`):

| Env var (preferred) | Fallback | Property | Default |
|---|---|---|---|
| `SPRING_MAIL_HOST` | `MAIL_HOST` | `spring.mail.host` | empty → sending disabled |
| `SPRING_MAIL_PORT` | `MAIL_PORT` | `spring.mail.port` | `587` |
| `SPRING_MAIL_USERNAME` | `MAIL_USERNAME` | `spring.mail.username` | empty |
| `SPRING_MAIL_PASSWORD` | `MAIL_PASSWORD` | `spring.mail.password` | empty |
| `SPRING_MAIL_SMTP_AUTH` | `MAIL_SMTP_AUTH` | `spring.mail.properties.mail.smtp.auth` | `true` |
| `SPRING_MAIL_SMTP_STARTTLS` | `MAIL_SMTP_STARTTLS` | `spring.mail.properties.mail.smtp.starttls.enable` | `true` |
| `MAIL_FROM` | — | `app.mail.from` | `no-reply@tour.local` |
| `APP_FRONTEND_URL` | — | `app.web.frontend-url` | `http://localhost:3000` |

`SPRING_MAIL_HOST` is the switch: leave it empty to disable sending safely (and get the dev link); set it
to enable real delivery.

### Gmail App Password setup

Gmail rejects normal account passwords for SMTP. Use an App Password:

1. Enable **2-Step Verification** on the Google account.
2. Go to <https://myaccount.google.com/apppasswords> and create an app password (name it e.g. `tour-dev`).
3. Copy the 16-character password (spaces are optional).
4. Add to `tour/.env`:

```dotenv
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your.address@gmail.com
SPRING_MAIL_PASSWORD=xxxxxxxxxxxxxxxx
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS=true
MAIL_FROM=your.address@gmail.com
APP_FRONTEND_URL=http://localhost:3000
```

Notes:

- `MAIL_FROM` should be the Gmail address itself, or a verified "Send mail as" alias; otherwise Gmail
  rewrites or rejects the sender.
- Do not commit `.env` or paste the app password into the report/chat. Rotate it if it leaks.
- Port `587` + STARTTLS is used here. Port `465` would require `mail.smtp.ssl.enable` instead, which this
  configuration does not set.
- After editing `.env`, restart the backend; `@Value` is resolved at startup.

## Test coverage for Task 1

`EmailServiceTest` was rewritten around a Logback `ListAppender` and now has 6 tests:

1. missing host logs the verification link
2. missing host logs the password-reset link
3. blank recipient → warning, no token in logs
4. missing `JavaMailSender` bean → warning, no token in logs
5. send failure → reason logged, no token in logs
6. configured sender → message is sent, no token in logs

Run: `.\mvnw.cmd -Dtest=EmailServiceTest test` → **6 tests, 0 failures**.

---

# TASK 2 — invoice discount and total

## Every path that creates or updates an invoice

| Path | Entry point | Discount | Total |
|---|---|---|---|
| Automatic, on payment success | `PaymentService.recomputeBookingStatus` → `InvoiceService.createForPaidBooking` | `booking.bookingDiscountAmount` | `booking.bookingTotalAmount` |
| Automatic, admin status → `PAID` | `BookingService.update` → `InvoiceService.createForPaidBooking` | `booking.bookingDiscountAmount` | `booking.bookingTotalAmount` |
| Admin create | `POST /api/invoices` → `InvoiceService.create` | request value | request value |
| Admin update | `PUT /api/invoices/{id}` → `InvoiceService.update` | request value | request value |

`PaymentService.recomputeBookingStatus` is the single point reached by the signed gateway callback,
the mock payment, and admin payment create/update/delete, so all automatic payment paths share the same
invoice creation.

`InvoiceService.createForPaidBooking` sets:

```java
invoice.setStatus("PAID");
invoice.setDiscountAmount(nonNull(booking.getBookingDiscountAmount()));
invoice.setTaxAmount(BigDecimal.ZERO);
invoice.setServiceFee(BigDecimal.ZERO);
invoice.setTotalAmount(nonNull(booking.getBookingTotalAmount()));
```

It also links `booking.getPromotion()` into `invoice_promotions`, and snapshots customer name/phone/email
from the first traveler, falling back to the account. `bookingTotalAmount` is already the amount actually
owed: `subtotal − discount` (clamped at zero). Tax and service fee are not modelled on `Booking`, so the
automatic invoice correctly uses `0` for both and `total = bookingTotalAmount`. It is idempotent: an
existing invoice for the booking is returned instead of creating a second one.

## Conclusion

The automatic path is **correct**. No production code change was needed, and **no one-off SQL `UPDATE`
is required**. The admin `POST/PUT /api/invoices` endpoints intentionally accept caller-supplied amounts
(`InvoiceRequest`), and `/api/invoices/**` is admin-only, so they are an explicit manual override rather
than a derived path.

If an audit is ever wanted, this is a **read-only** query (not executed):

```sql
SELECT i.invoice_id,
       i.booking_id,
       i.discount_amount AS invoice_discount,
       b.booking_discount_amount AS booking_discount,
       i.total_amount AS invoice_total,
       b.booking_total_amount AS booking_total
FROM invoices i
JOIN bookings b ON b.booking_id = i.booking_id
WHERE i.discount_amount <> b.booking_discount_amount
   OR i.total_amount <> b.booking_total_amount
ORDER BY i.invoice_id;
```

An empty result means every invoice matches its booking. Do not run an `UPDATE` unless this query
returns rows that should be corrected; if it does, the fix is a data correction of the affected invoices,
not a code change.

## Integration test

Added to `BookingFlowIntegrationTests`:

```java
@Test
void paidBookingInvoiceMatchesPromotionDiscountAndTotal()
```

It creates a promotion (`INVOICE10`, 10% off), books 2 guests (subtotal `1000.00`), completes the
payment callback for `900.00`, and asserts:

- booking status is `PAID`
- invoice status is `PAID`
- `invoice.discountAmount == booking.discountAmount == 100.00`
- `invoice.totalAmount == booking.totalAmount == 900.00`
- `invoice.taxAmount == 0` and `invoice.serviceFee == 0`
- the invoice links the promotion used by the booking

Run: `.\mvnw.cmd -Dtest=BookingFlowIntegrationTests#paidBookingInvoiceMatchesPromotionDiscountAndTotal test`
→ **1 test, 0 failures**.

## Known gap (documented, not changed)

`BookingService.update` allows an admin to change `guestCount` on a booking that is already `PAID`. It
recalculates `bookingSubtotal`, `bookingDiscountAmount`, and `bookingTotalAmount`, but the existing
invoice is **not** recalculated because invoice creation only runs on the transition into `PAID`. That can
leave a paid booking and its invoice out of sync after a post-payment guest-count edit.

This is outside the requested verification (no promotion invoice is wrong on the normal flow), so it was
left unchanged. If post-payment edits must stay consistent, the follow-up is to update the invoice in the
same transaction when `guestCount` changes on a booking that already has one, or to forbid guest-count
changes after `PAID`.

---

## Verification

Targeted runs:

- `EmailServiceTest` — 6 tests, 0 failures
- `BookingFlowIntegrationTests#paidBookingInvoiceMatchesPromotionDiscountAndTotal` — 1 test, 0 failures

Full suite: `.\mvnw.cmd test` → **253 tests, 0 failures, 0 errors, 1 skipped**, `BUILD SUCCESS`
(4:05 min). The skipped test is the pre-existing `TourApplicationTests.contextLoads`, which is skipped
because it needs PostgreSQL.

The full run also exercised the new dev fallback through
`ProfileAndPasswordIntegrationTests`: the log showed the skip reason and the
`/verify-email?token=…` link, confirming the fix end to end without SMTP.

No commit was made; `fe-tour/` was not touched.
