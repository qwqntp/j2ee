# Tour Booking API — v1

Base path: `/api`  
Default local base URL: `http://localhost:8080`

> **Scope:** endpoints marked **Implemented** exist in the backend today. Booking, payment callback,
> refund, promotion, traveler, invoice and tour-rating endpoints are implemented. The historical
> “Planned booking and payment contract” section at the end is retained for design history and is
> superseded by the Batch 1–3 additions below.

## Conventions

- JSON, UTF-8, `Content-Type: application/json`.
- IDs are positive integers.
- Money is in VND and is serialized as a JSON number with two decimals. There is no currency field.
- `ApiResponse.timestamp` is an ISO-8601 UTC instant (for example `2026-10-26T15:55:38.262582800Z`).
- Tour and departure dates are `LocalDateTime` values **without an offset**, interpreted in
  `Asia/Ho_Chi_Minh` (for example `2026-11-24T08:00:00`). Clients should format the wall-clock
  parts rather than converting through the browser timezone.
- Pagination is zero-based. `page=0` is the first page.
- Unknown JSON properties are ignored by the default Jackson configuration.

### Success envelope

Controller responses use this envelope except for the VNPay IPN, which uses the provider's
acknowledgement format:

```json
{
  "success": true,
  "message": "Success",
  "data": {},
  "timestamp": "2026-10-26T15:55:38.262582800Z"
}
```

### Error envelope

`data` is `null` for general errors, or a field-to-message map for validation errors:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "list.size": "must be greater than or equal to 1"
  },
  "timestamp": "2026-10-26T15:55:32.736931900Z"
}
```

Spring Security's JSON `401`/`403` responses use the same envelope with `data: null`.

### Pagination object

```json
{
  "content": [],
  "page": 0,
  "size": 12,
  "totalElements": 0,
  "totalPages": 0,
  "last": true,
  "hasNext": false,
  "hasPrevious": false
}
```

`totalPages` is `0` when there are no results.

## Authentication

### Access token

- HS256 JWT, 15-minute TTL.
- Claims: `sub` (account id), `username`, `role`, `iat`, `exp`.
- Send it as `Authorization: Bearer <accessToken>`.
- A missing or invalid token returns `401` with `"Authentication required"` or
  `"Invalid or expired token"`.

### Refresh token

- Opaque 32-byte random value, base64url encoded. Only its SHA-256 hex hash is stored.
- Delivered only as the `refresh_token` cookie:
  - `HttpOnly`
  - `SameSite=Lax`
  - `Path=/api/auth`
  - 7-day TTL
  - `Secure` is configurable (`APP_COOKIE_SECURE`, default `false`)
- Every successful refresh revokes the old row and inserts a new token (rotation).
- Presenting a revoked token revokes **all** tokens for that account and returns `401`
  (`"Refresh token reuse detected"`).
- Logout revokes the presented token and clears the cookie. It is idempotent.
- The access token is stateless and remains valid until its 15-minute expiry, even after logout.

### CORS and cookies

- Allowed origins come from `APP_CORS_ALLOWED_ORIGINS` (default `http://localhost:3000`).
- Allowed methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`.
- Allowed request headers: `Authorization`, `Content-Type`.
- Credentials are allowed, so the browser may send the refresh cookie cross-origin.
- CSRF protection is disabled because the API is stateless and the refresh cookie is
  `SameSite=Lax`. A production cross-site deployment needs `SameSite=None; Secure` plus a CSRF
  strategy, or a same-site proxy.

### Rate limits

The in-memory fixed-window limiter applies to these `POST`s:

| Bucket | Routes | Limit |
|---|---|---|
| credentials | `/api/auth/login`, `/api/auth/register` | 10 per minute per IP (shared) |
| google | `/api/auth/google` | 10 per minute per IP |
| refresh | `/api/auth/refresh` | 30 per minute per IP |
| change-password | `/api/auth/change-password` | 10 per minute per IP |
| forgot-password | `/api/auth/forgot-password` | 5 per minute per IP |
| reset-password | `/api/auth/reset-password` | 10 per minute per IP |

- Exceeding a limit returns `429` with `Retry-After: <seconds>` and
  `{"success":false,"message":"Too many requests","data":null,...}`.
- Public `GET`/`OPTIONS` requests are not rate limited.
- The limiter is per application instance. A multi-instance deployment needs a shared store
  (for example Redis).

## Implemented endpoints

| Method | Path | Auth | Success |
|---|---|---|---|
| `GET` | `/api/tours` | Public | `200` |
| `GET` | `/api/tours/destinations` | Public | `200` |
| `GET` | `/api/tours/{id}` | Public | `200` |
| `GET` | `/api/tours/{id}/departures` | Public | `200` |
| `POST` | `/api/auth/register` | Public | `201` |
| `POST` | `/api/auth/login` | Public | `200` |
| `POST` | `/api/auth/google` | Public | `200` |
| `POST` | `/api/auth/refresh` | Refresh cookie | `200` |
| `POST` | `/api/auth/logout` | Refresh cookie (optional) | `200` |
| `GET` | `/api/auth/me` | Bearer | `200` |
| `GET` | `/api/admin/accounts` | Bearer `ROLE_ADMIN` | `200` |

### Batch 1–3 additions (implemented)

#### Auth, booking, payment, invoice and rating endpoints

| Method | Path | Auth | Success |
|---|---|---|---|
| `POST` | `/api/auth/forgot-password` | Public | `200` |
| `POST` | `/api/auth/reset-password` | Public | `200` |
| `POST` | `/api/auth/verify-email` | Public | `200` |
| `POST` | `/api/auth/change-password` | Bearer | `200` |
| `GET` | `/api/profile` | Bearer | `200` |
| `PUT` | `/api/profile` | Bearer | `200` |
| `POST` | `/api/bookings` | Bearer | `201` |
| `GET` | `/api/bookings` | Bearer | `200` |
| `GET` | `/api/bookings/{id}` | Bearer | `200` |
| `GET` | `/api/bookings/code/{bookingCode}` | Bearer | `200` |
| `PUT` | `/api/bookings/{id}` | Bearer | `200` |
| `POST` | `/api/bookings/{id}/cancel` | Bearer | `200` |
| `DELETE` | `/api/bookings/{id}` | Bearer | `200` |
| `POST` | `/api/bookings/{bookingId}/payments/callback` | Bearer `ROLE_ADMIN` | `200` |
| `POST` | `/api/bookings/{bookingId}/payments/vnpay` | Bearer; booking owner | `200` |
| `POST` | `/api/bookings/{bookingId}/payments/vnpay/reconcile` | Bearer; booking owner | `200` |
| `POST` | `/api/payments/vnpay/return` | Bearer; booking owner | `200` |
| `GET` | `/api/payments/vnpay/status/{reference}` | Bearer; booking owner | `200` |
| `GET` | `/api/payments/vnpay/ipn/{routeToken}` | Registered HTTPS callback URL + VNPay signature | `200` with `RspCode` |
| `GET` | `/api/invoices/me` | Bearer | `200` |
| `GET` | `/api/tours/{tourId}/ratings` | Public | `200` |
| `POST` | `/api/tours/{tourId}/ratings` | Bearer | `201` |

Admin CRUD for accounts, roles, departures, promotions, travelers, tour guides, images,
accommodations, vehicles, destinations, invoices and refunds remains under the existing admin-only
paths (`/api/admin/**`, `/api/invoices/**`, `/api/payments/**`, and the other resource paths listed
in `SecurityConfig`).

#### Bookings and promotions

- `POST /api/bookings` keeps the original frontend payload
  `{ "departureId": 1, "guestCount": 2, "note": "optional" }` working.
- Optional additions: `travelers[]` and `promotionCode`.
- If `travelers` is supplied, its size must equal `guestCount`.
- Pricing is server-side; the client never sends a price or total.
- Promotion lookup is case-insensitive, the code is stored uppercase, and the discount is calculated
  from the subtotal, rounded to 2 decimals and capped at the subtotal.
- `GET /api/bookings` returns only the caller's bookings (admins see all) and defaults to newest first.
- `GET /api/bookings/{id}` returns `404` for another user's booking.
- `POST /api/bookings/{id}/cancel` is allowed only for `PENDING` bookings with no successful
  payments; it releases seats and promotion usage and returns `409` otherwise.
- A booking becomes `PAID` when the sum of `SUCCESS` payments reaches its total amount.

#### VNPay Sandbox

Uses [VNPay PAY 2.1.0](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
The app never collects card or bank details. All VNPay API responses are non-cacheable.

**Start:** `POST /api/bookings/{bookingId}/payments/vnpay` takes no body. Before creating an
attempt, the backend asks VNPay's QueryDr API about any owned `PENDING` attempt. The owner
receives `data: { reference, paymentUrl, expiresAt }` (`expiresAt` is an ISO-8601 UTC instant)
only after that attempt is released or absent. An owned `PENDING` booking with an upcoming
`OPEN` or `FULL` departure must have a positive outstanding balance of at least
`10,000 VND` (`VNPayGateway.MINIMUM_AMOUNT`), calculated from the stored total less `SUCCESS`
payments. The URL uses VND × 100 without rounding, a 15-minute GMT+7 window, and an
HMAC-SHA512 over sorted form-encoded fields. Every existing `PENDING` attempt blocks a new
reference until a verified IPN or QueryDr answer releases it; an attempt URL is never reopened.
Expiry of the URL, a local timeout, and QueryDr "not found" (`91`) are not evidence of
nonpayment, so they do not release the attempt.
Errors: `401` no session, `404` missing/other owner's booking, `409` balance below the minimum,
no payable balance, or unresolved/ineligible booking, `503` missing or invalid Sandbox
configuration.

**QueryDr reconciliation:** `POST /api/bookings/{bookingId}/payments/vnpay/reconcile` takes no
body and calls VNPay's signed server-to-server QueryDr endpoint
(`https://sandbox.vnpayment.vn/merchant_webapi/api/transaction`) about the booking's open
attempt. It returns
`data: { bookingId, reference, status, checked, reason, providerCode, providerStatus }`:
`checked: false` means no managed attempt is `PENDING`; otherwise `status` is the stored
`PENDING`, `SUCCESS`, or `FAILED` state after the answer is applied.

`reason` explains the actual result of the lookup instead of only repeating the stored status.
`providerCode` and `providerStatus` carry the documented two-digit VNPay codes when received;
provider text, payloads, credentials, and signed URLs are never returned.

| `reason` | Meaning | Effect |
|---|---|---|
| `VERIFIED_SUCCESS` | Signed `vnp_ResponseCode=00`, `vnp_TransactionStatus=00` | Settles the attempt and booking like a success IPN |
| `VERIFIED_NONPAYMENT` | Signed `00` with transaction status `02` | Marks the attempt `FAILED`; releases retry and cancellation |
| `TRANSACTION_NOT_FOUND` | VNPay response code `91` | Stays `PENDING`; not proof of nonpayment |
| `TRANSACTION_UNRESOLVED` | Signed `00` with status `01`, `04`, `05`, `06`, `07`, or `09` | Stays `PENDING` |
| `PROVIDER_REJECTED` | VNPay API-level error such as `97` or `99`; `providerCode` carries it | Stays `PENDING` |
| `PROVIDER_MISMATCH` | Signed answer describes another merchant, reference, amount, or transaction type | Stays `PENDING` |
| `INVALID_PROVIDER_SIGNATURE` | Bad response hash, or data-bearing response without a signature | Stays `PENDING` |
| `PROVIDER_MALFORMED_RESPONSE` | Not JSON, unexpected fields/types, or an unsigned `00` | Stays `PENDING` |
| `PROVIDER_TIMEOUT` | The 5-second connect/read deadline expired | Stays `PENDING` |
| `PROVIDER_UNREACHABLE` | DNS, TLS, connection, or HTTP failure | Stays `PENDING` |

Only `VERIFIED_SUCCESS` and `VERIFIED_NONPAYMENT` may change the attempt. VNPay can return an
unsigned `{ "vnp_ResponseCode": "...", "vnp_Message": "..." }` envelope for API-level errors
(for example Sandbox `99 Unexpected error`). That envelope is reported as `PROVIDER_REJECTED`
with only the safe code and can never settle or release an attempt. A signature-less success or
any unsigned response carrying transaction data is rejected.


**Browser return and status:** VNPay redirects the browser to the configured frontend URL
(locally `http://localhost:3000/payment/vnpay-return`). The frontend sends the serialized
`vnp_` query without the leading `?` to `POST /api/payments/vnpay/return` in JSON `{ "query": "..." }`.
This authenticated endpoint verifies the signature, merchant, reference, amount and status
fields, then reconciles the owned attempt through QueryDr. The browser fields are a hint, not
authoritative evidence: a missing or ambiguous IPN is resolved only by the signed provider
answer. `GET /api/payments/vnpay/status/{reference}` likewise rechecks the owned attempt through
QueryDr without a signed query. Both return
`data: { bookingId, reference, status, expired, returnOutcome, reason, providerCode, providerStatus }`:

- `status` is the recorded `PENDING`, `SUCCESS`, or `FAILED` payment state after reconciliation.
- `expired` describes the URL's time window, not a final payment outcome.
- `returnOutcome` is `SUCCESS`, `FAILED`, `CANCELLED`, `EXPIRED`, or `UNRESOLVED` for a
  verified browser return; it is `null` for a reference-only recheck. Even a verified
  `returnOutcome: "SUCCESS"` is not settlement by itself.
- `reason`, `providerCode`, and `providerStatus` use the reconciliation table above. `reason`
  is `null` when the read did not need to query VNPay (for example an already settled attempt).
- `400` means invalid return data; `401` means the browser must authenticate; `404` hides absent
  or foreign attempts. Reauthentication carries only the opaque reference in the login URL.

**IPN:** VNPay calls `GET /api/payments/vnpay/ipn/{routeToken}` with the provider query.
The backend verifies the registered route credential, HMAC-SHA512, merchant code, managed
reference, exact stored amount, and provider statuses. Both provider codes must be `00` to settle.
A definitive nonpayment (such as a signed cancellation or expiry with failed transaction status)
marks the attempt `FAILED`; ambiguous outcomes stay `PENDING` for reconciliation.
The booking lock serializes concurrent confirmations. A verified success from IPN or QueryDr
promotes the booking to `PAID` when covered and creates or settles one invoice in that
transaction. An already-paid invoice is left untouched.

The provider receives HTTP `200` with `{ "RspCode": "00", "Message": "Confirm Success" }`
or the corresponding acknowledgement:

| `RspCode` | Meaning |
|---|---|
| `00` | First definitive result committed (success or nonpayment) |
| `02` | Same terminal outcome already processed |
| `01` | Managed reference not found |
| `04` | Invalid amount |
| `97` | Invalid signature, merchant, or registered route credential |
| `99` | Malformed/ambiguous/contradictory notification or processing failure |

**Configuration:** Set `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PAY_URL` (the official
HTTPS Sandbox pay URL), and `VNPAY_RETURN_URL` on the backend only. Set `VNPAY_IPN_TOKEN` to
a separate, randomly generated, URL-safe credential (32–128 ASCII letters, digits, `_` or `-`).
Register the exact `https://<backend-host>/api/payments/vnpay/ipn/<route-token>` with VNPay
Sandbox. It accepts a standard GET with no extra headers. The bare IPN path does not authorize
settlement. **VNPay cannot reach localhost:** deploy or forward only the intended callback through
a public HTTPS ingress. Redact the entire credential-bearing path and query in proxy, application
and APM logs. Do not disclose the token or hash secret to the frontend. Keep this callback URL
separate from the browser return URL and use a trusted proxy configuration if forwarding client IPs.

For genuine local Sandbox tests, run the frontend with `npm run build` and `npm run start -- --port 3000`,
not `npm run dev`. Next.js development logging and tracing can record the incoming URL before
the result page can remove its query. Checkout does not launch VNPay in development mode. Redact
return query strings in any frontend ingress or telemetry used in deployed environments.

#### Email verification and password reset

- Verification and reset tokens are random, one-time values. Only their SHA-256 hashes are stored in
  `auth_tokens`; the raw token appears only in the email link.
- Default expiries: password reset 30 minutes, email verification 2 days.
- `POST /api/auth/forgot-password` always returns:
  ```json
  {
    "success": true,
    "message": "If the email exists, a password reset link has been sent",
    "data": null,
    "timestamp": "..."
  }
  ```
  The same response is returned for unknown emails, so account existence is not revealed.
- `POST /api/auth/reset-password` consumes the token, updates the BCrypt hash, and revokes all
  refresh tokens for the account. A reused, expired or unknown token returns `400`
  `Invalid or expired token`.
- `POST /api/auth/verify-email` consumes the token and sets `emailVerified = 1`.
- Email sending is asynchronous and failure-tolerant. If SMTP is not configured, the API continues
  to work and logs a warning; mail failures never fail registration, booking, reset or cancellation.
- Mail is configured through `SPRING_MAIL_HOST`/`MAIL_HOST` and the other `SPRING_MAIL_*`/`MAIL_*`
  variables, `MAIL_FROM`, and `APP_FRONTEND_URL`.

Example reset request:

```json
{
  "token": "raw-token-from-email",
  "newPassword": "new-password"
}
```

Example verification request:

```json
{ "token": "raw-token-from-email" }
```

#### Google sign-in

`POST /api/auth/google` is public and accepts:

```json
{ "idToken": "credential-from-google-identity-services" }
```

The backend verifies the ID token with Google's JWKS
(`https://www.googleapis.com/oauth2/v3/certs`): RS256 signature, issuer is `accounts.google.com`
or `https://accounts.google.com`, audience equals `GOOGLE_CLIENT_ID`, the token is not expired
(server `Clock`), and `email_verified` is `true`. No Spring OAuth2 redirect flow, session or client
secret is involved.

- Success returns the same body and `Set-Cookie` contract as `POST /api/auth/login`: a 15-minute
  access token plus the HttpOnly `refresh_token` cookie (`200`).
- `google_sub` is the identity key. If no account has it, an existing account is linked by
  case-insensitive email; otherwise a new `ROLE_USER` account is created with a unique username
  derived from the email local part and an unusable random password.
- If an existing matched account has `emailVerified = 0`, its password hash is replaced with a fresh
  unusable value and all its refresh tokens are revoked before Google is linked (a pre-registered
  attacker may know that password). `emailVerified` becomes `1` and `password_set` becomes `false`.
- A disabled account returns `403` (`Account is disabled`). An invalid token returns `401`
  (`Invalid Google ID token`). If `GOOGLE_CLIENT_ID` / `app.google.client-id` is blank the endpoint
  fails closed with `503` (`Google sign-in is not configured`).
- There is no email-domain allowlist: any Google account with a valid, verified email may sign in.

Configuration: backend `GOOGLE_CLIENT_ID`; frontend `NEXT_PUBLIC_GOOGLE_CLIENT_ID` for
`https://accounts.google.com/gsi/client`. Values belong in the deployment environment, never in
committed files.

#### Profile and change password

- `GET /api/profile` returns the authenticated account plus its customer row:
  `{ customerId, username, email, emailVerified, name, phone, address, dateOfBirth, passwordSet,
  googleLinked }`. Customer fields are `null` when the account has no customer row yet.
  `passwordSet` is `false` for Google-only accounts and for accounts whose password was replaced
  during a Google link; `googleLinked` is `true` when `google_sub` is set. Neither value exposes the
  password hash or the Google subject.
- `PUT /api/profile` accepts `{ name, email, phone, address, dateOfBirth }`. `name` is required and
  non-blank; the other fields are optional. A blank email keeps the current one. Changing the email
  updates `accounts.account_email`, resets `emailVerified` to `0` and sends a new verification link.
  Unknown fields are ignored.
- `POST /api/auth/change-password` accepts `{ currentPassword, newPassword }`. The new password follows
  the register rules (8–100 characters, at most 72 UTF-8 bytes). A wrong current password returns `400`
  `Current password is incorrect` without revoking anything. An account with `password_set = false`
  (Google-only, or whose password was replaced during a Google link) returns `409`
  `This account has no password set. Use "Forgot password" to set one.` On success every refresh token
  is revoked and a new access/refresh pair is returned through the normal body/cookie contract; existing
  access tokens remain valid until they expire (at most 15 minutes). A successful password reset sets
  `password_set = true`.

#### Invoices

- An invoice is created automatically and idempotently whenever a booking becomes `PAID`, through
  either the payment callback/update path or an admin status update.
- Automatic invoices are `PAID`, copy the booking discount and total, snapshot the customer name /
  email / phone, and link any applied promotion.
- `GET /api/invoices/me` returns only invoices for bookings owned by the authenticated account,
  newest first:
  ```json
  {
    "success": true,
    "message": "Success",
    "data": {
      "content": [
        {
          "id": 1,
          "bookingId": 30,
          "createdAt": "2026-11-07T10:00:00",
          "status": "PAID",
          "discountAmount": 0.00,
          "taxAmount": 0.00,
          "serviceFee": 0.00,
          "totalAmount": 1000.00,
          "paymentMethod": null,
          "customerName": "traveler",
          "customerPhone": "0901234567",
          "customerEmail": "traveler@example.com",
          "promotionIds": []
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "last": true,
      "hasNext": false,
      "hasPrevious": false
    },
    "timestamp": "..."
  }
  ```
- Admin invoice CRUD remains under `/api/invoices/**` and requires `ROLE_ADMIN`.

#### Tour ratings

- One rating per booking, enforced by a unique constraint and a service check.
- Only the owner of a `PAID` or `COMPLETED` booking for that tour may rate it.
- Non-owner or unknown booking: `404`. Unpaid/invalid status: `403`. Duplicate rating: `409`.
  Booking/tour mismatch: `400`.
- Rating value is 1..5; comment is optional up to 2000 characters.

Create rating:

```json
{
  "bookingId": 30,
  "rating": 5,
  "comment": "Great trip"
}
```

Public list response includes pagination, `averageRating` rounded to 2 decimals, and `ratingCount`:

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "content": [
      {
        "id": 1,
        "tourId": 1,
        "accountId": 2,
        "accountUsername": "traveler",
        "bookingId": 30,
        "rating": 5,
        "comment": "Great trip",
        "createdAt": "2026-11-07T10:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "hasNext": false,
    "hasPrevious": false,
    "averageRating": 5.00,
    "ratingCount": 1
  },
  "timestamp": "..."
}
```

### GET /api/tours/destinations

Public. Returns every tourist destination as a minimal `{ id, name }` option, sorted by name. It is
intended for the catalogue destination filter. Unlike `/api/tourist-destinations`, it needs no token
and returns only the two fields.

```json
{
  "success": true,
  "message": "Success",
  "data": [
    { "id": 2, "name": "Cat Ba Island" },
    { "id": 1, "name": "Lan Ha Bay" }
  ],
  "timestamp": "2026-11-14T09:00:00Z"
}
```

The literal `/destinations` segment is matched before `/{id}`, so it is never parsed as a tour id.

### GET /api/tours

Public tour catalogue. Only tours with `status = OPEN` are returned.

Query parameters:

| Name | Type | Default | Rules |
|---|---|---|---|
| `page` | integer | `0` | `>= 0` |
| `size` | integer | `12` | `1..50` |
| `keyword` | string | none | max 100 characters; `%` and `_` are stripped; case-insensitive name search |
| `destinationId` | integer | none | only tours linked to this tourist destination |
| `minPrice` | decimal | none | `>= 0`; must be `<= maxPrice` when both are present |
| `maxPrice` | decimal | none | `>= 0` |
| `startFrom` | date (`YYYY-MM-DD`) | none | must be on or before `startTo` |
| `startTo` | date (`YYYY-MM-DD`) | none | |
| `minDays` | integer | none | `>= 1`; must be `<= maxDays` when both are present |
| `maxDays` | integer | none | `>= 1` |
| `sort` | string | none | `newest`, `priceAsc`, `priceDesc` or `dateAsc`; invalid values return `400` |

One upcoming `OPEN`/`FULL` departure must satisfy every supplied price and date predicate.
Aggregate sorts (`priceAsc`, `priceDesc`, `dateAsc`) use the same departure definition as the card
stats, with `NULLS LAST` and tour id ascending as the tie-breaker. Without `sort`, results are ordered
by tour id ascending. Invariant violations return `400` with the specific message (for example
`minPrice must be less than or equal to maxPrice`).

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "Lan Ha Bay Overnight",
        "description": "Slip past the day-trippers into the quieter half of the bay.",
        "status": "OPEN",
        "imageUrl": "https://cdn.example.com/tours/lan-ha.jpg",
        "priceFrom": 3450000.00,
        "nextDepartureDate": "2026-11-24T08:00:00",
        "seatsAvailable": 18,
        "departureCount": 3
      }
    ],
    "page": 0,
    "size": 12,
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-10-26T15:55:38.262582800Z"
}
```

Summary stats only consider departures that are in the future (`startDate > now`) and have status
`OPEN` or `FULL`:

- `priceFrom` — minimum departure price.
- `nextDepartureDate` — earliest departure start.
- `seatsAvailable` — sum of `capacity - bookedSlots` across `OPEN` departures only.
- `departureCount` — number of upcoming `OPEN` + `FULL` departures.

When there are no upcoming departures, `priceFrom` and `nextDepartureDate` are `null`,
`seatsAvailable` is `0`, and `departureCount` is `0`.

The seeded database currently has only past departures, so live seed responses show those empty
stats. The non-empty aggregate path has not been exercised against the shared database.

### GET /api/tours/{id}

Returns one `OPEN` tour. A missing or non-public tour returns `404` (`"Tour not found"`).

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 1,
    "name": "Lan Ha Bay Overnight",
    "description": "Slip past the day-trippers into the quieter half of the bay.",
    "status": "OPEN",
    "accommodationName": "Yến Sail Boutique Cruise",
    "accommodationRating": 4.8,
    "accommodationType": "Overnight cruise",
    "guideName": "Minh Trần",
    "guidePhone": "+84 912 345 601",
    "imageUrls": ["https://cdn.example.com/tours/lan-ha.jpg"],
    "itinerary": [
      "Day 1 · Hanoi pickup, kayak through Lan Ha's limestone channels, sunset on deck.",
      "Day 2 · Sunrise swim, breakfast at a floating village, return to Hanoi."
    ],
    "destinationNames": ["Lan Ha Bay", "Cat Ba Island"],
    "vehicleTypes": ["Shuttle bus", "Boat"],
    "departures": []
  },
  "timestamp": "2026-10-26T15:55:38.262582800Z"
}
```

Notes:

- `description` is the first `|`-separated segment of the stored description.
- `itinerary` is the full description split on `|`, trimmed, with empty segments removed.
- `departures` contains only future `OPEN`/`FULL` departures, sorted by start date ascending.
- `imageUrls` contains every tour image in storage order; `imageUrl` in the summary is the first one.

### GET /api/tours/{id}/departures

Returns the same future `OPEN`/`FULL` departures as the detail response, without the tour wrapper.
A missing or non-public tour returns `404`.

```json
{
  "success": true,
  "message": "Success",
  "data": [
    {
      "id": 123,
      "tourId": 1,
      "startDate": "2026-11-24T08:00:00",
      "endDate": "2026-11-25T17:00:00",
      "capacity": 12,
      "bookedSlots": 6,
      "seatsAvailable": 6,
      "price": 3450000.00,
      "meetingPoint": "Hanoi Opera House, 08:00",
      "status": "OPEN",
      "createdAt": "2026-09-01T10:00:00"
    }
  ],
  "timestamp": "2026-10-26T15:55:38.262582800Z"
}
```

`seatsAvailable = capacity - bookedSlots`. A `FULL` departure remains visible with
`seatsAvailable = 0`; clients should render it as sold out.

### POST /api/auth/register

Request:

```json
{
  "username": "traveler",
  "password": "correct-horse-battery",
  "email": "traveler@example.com"
}
```

Rules:

- `username`: 3–50 characters, required.
- `password`: 8–100 characters, required, and at most 72 UTF-8 bytes (BCrypt limit).
- `email`: valid address, at most 255 characters, required.
- Username uniqueness is case-sensitive.
- Email uniqueness is case-insensitive, checked in the service. There is no database unique
  constraint for the case-insensitive form, so concurrent registrations can race.
- New accounts get `ROLE_USER`, `status = 1` (active), and `emailVerified = 0`.

Response `201` with `Set-Cookie: refresh_token=...`:

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "account": {
      "id": 3,
      "username": "traveler",
      "email": "traveler@example.com",
      "status": 1,
      "roleName": "ROLE_USER"
    }
  },
  "timestamp": "2026-10-26T15:55:38.262582800Z"
}
```

Errors: `400` validation or password over 72 bytes, `409` username/email already exists, `429`
rate limited.

### POST /api/auth/login

Request:

```json
{
  "username": "traveler",
  "password": "correct-horse-battery"
}
```

Response `200` with the same body shape as register and a fresh refresh cookie.

Errors:

- `401` — invalid username or password (same message for both).
- `403` — account is disabled.
- `429` — rate limited.

Unknown usernames still run a dummy BCrypt comparison to reduce timing differences.

### POST /api/auth/google

Request:

```json
{ "idToken": "eyJhbGciOiJSUzI1NiJ9..." }
```

The token comes from Google Identity Services (`google.accounts.id`) on the frontend. The backend
verifies it with Google's JWKS and returns `200` with the same body shape as login plus a fresh
refresh cookie. New accounts are created with `ROLE_USER`, `emailVerified = 1`, `password_set =
false` and a unique username derived from the email local part. Existing accounts are matched by
`google_sub` first and case-insensitive email second.

Errors:

- `400` — missing/blank `idToken`.
- `401` — invalid signature, issuer, audience, expired token, unverified/missing email, or missing
  `email_verified`.
- `403` — account is disabled.
- `429` — more than 10 Google sign-ins in a minute from the same IP.
- `503` — `GOOGLE_CLIENT_ID` is not configured.

### POST /api/auth/refresh

No body. The browser sends the `refresh_token` cookie.

- `200` — new access token in the body, rotated refresh cookie in `Set-Cookie`.
- `401` — missing, invalid, expired, or reused refresh token.
- `403` — account is disabled.
- `429` — more than 30 refreshes in a minute from the same IP.

Reuse detection is intentional: a stolen old token invalidates the whole account's token family.

### POST /api/auth/logout

No body. Revokes the cookie token when present and always returns `200` with a clearing
`Set-Cookie`:

```json
{
  "success": true,
  "message": "Logged out",
  "data": null,
  "timestamp": "2026-10-26T15:55:38.262582800Z"
}
```

### GET /api/auth/me

Requires `Authorization: Bearer <accessToken>`.

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 3,
    "username": "traveler",
    "email": "traveler@example.com",
    "status": 1,
    "roleName": "ROLE_USER"
  },
  "timestamp": "2026-10-26T15:55:38.262582800Z"
}
```

`emailVerified` is stored but not exposed. `status` is `1` for active and `0` for disabled.

### GET /api/admin/accounts

Requires `Authorization: Bearer <accessToken>` with `ROLE_ADMIN`.

Query parameters: `page` (default `0`, `>= 0`), `size` (default `20`, `1..100`). Sorted by account
id ascending.

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "content": [
      {
        "id": 1,
        "username": "admin",
        "email": "admin@example.com",
        "status": 1,
        "roleName": "ROLE_ADMIN"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-10-26T15:55:38.262582800Z"
}
```

Errors: `401` unauthenticated, `403` authenticated without `ROLE_ADMIN`.

## Status codes

| Status | Meaning |
|---|---|
| `200` | Success |
| `201` | Account created |
| `400` | Validation failure, malformed JSON, wrong parameter type, password over 72 bytes, invalid/expired one-time token, or a rating booking/tour mismatch |
| `401` | Missing/invalid access token, bad credentials, or invalid/reused refresh token |
| `403` | Authenticated but not allowed, disabled account, or rating a non-paid/non-completed booking |
| `404` | Tour, booking or invoice not found, not public, or not owned by the caller |
| `409` | Username/email conflict, duplicate rating, invalid booking-state transition, or another data-integrity conflict |
| `429` | Rate limit exceeded; see `Retry-After` |
| `500` | Unexpected server error; no stack trace is returned |

## Enumerations

- Tour status: `DRAFT`, `OPEN`, `CLOSED`, `CANCELLED`. Public APIs expose `OPEN` only.
- Departure status: `OPEN`, `FULL`, `CLOSED`, `CANCELLED`. Public APIs expose future `OPEN` and
  `FULL` only.
- Account status: `1` active, `0` disabled.
- Roles: `ROLE_USER`, `ROLE_ADMIN`.
- Booking status: `PENDING`, `PAID`, `CONFIRMED`, `COMPLETED`, `CANCELLED`.
- Payment status: `PENDING`, `SUCCESS`, `FAILED` (plus the existing payment-model values).
- Invoice status: `PAID`, `UNPAID`.
- Auth token types: `PASSWORD_RESET`, `EMAIL_VERIFICATION`.

## OpenAPI / Swagger

- Swagger UI: `GET /swagger-ui.html` (redirects to `/swagger-ui/index.html`).
- OpenAPI JSON: `GET /v3/api-docs`.
- Both are public in `SecurityConfig`.
- `OpenApiConfig` sets the title, version, description and the `bearerAuth` HTTP bearer scheme.
- Use the **Authorize** button in Swagger UI to paste an access token for `/api/auth/me`,
  `/api/bookings/**`, `/api/invoices/me`, `/api/tours/{tourId}/ratings` (`POST`) and the
  admin-only endpoints.
- For production, disable the docs or put them behind an admin/network boundary:
  `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`.

## Planned booking and payment contract (historical)

> **Historical design note — superseded.** The booking, payment-callback, promotion, traveler and
> refund backend is now implemented; see “Batch 1–3 additions (implemented)” above. This section is
> kept only as design history and is not normative. Use the **VNPay Sandbox** section above for
> the current creation, browser-return, and IPN paths and payloads.

### Proposed endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/bookings` | Bearer | Create a booking and hold seats |
| `GET` | `/api/bookings/{id}` | Bearer | Read one booking owned by the caller |
| `GET` | `/api/bookings` | Bearer | Paginated booking history for the caller |
| `POST` | `/api/bookings/{id}/payments` | Bearer | Create a VNPay payment URL |
| `GET` | `/api/payments/vnpay/return` | Public, signed | Browser return from VNPay |
| `GET` | `/api/payments/vnpay/ipn` | Public, signed | Server-to-server VNPay IPN |
| `GET` | `/api/admin/bookings` | Bearer `ROLE_ADMIN` | Paginated booking list for operations |

### POST /api/bookings

The server owns pricing. The request must not contain a price or total.

```json
{
  "departureId": 123,
  "guestCount": 2,
  "travelers": [
    { "fullName": "Nguyen Van A", "dateOfBirth": "1990-01-01", "nationality": "VN" },
    { "fullName": "Tran Thi B" }
  ],
  "contact": {
    "fullName": "Nguyen Van A",
    "email": "a@example.com",
    "phone": "+84 912 345 678"
  },
  "notes": "Vegetarian meals"
}
```

Validation:

- `departureId` must exist and be `OPEN` with a future start date.
- `guestCount >= 1` and **must equal `travelers.size()`**. A mismatch is `400`.
- Every traveler needs a `fullName`.
- The departure must have at least `guestCount` seats left.
- The server reads `unitPrice` from the departure and calculates
  `totalAmount = unitPrice * guestCount`.
- Seat reservation must be atomic: lock the departure row (`SELECT ... FOR UPDATE`), re-check
  availability, then hold the seats. A dedicated hold/booking table is preferable to incrementing
  `departureBookedSlots` immediately, so expired holds can be released without rewriting history.
- The hold expires after a configurable window (for example 15 minutes).

Response `201`:

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 501,
    "code": "HT-2026-000501",
    "status": "PENDING_PAYMENT",
    "departureId": 123,
    "guestCount": 2,
    "unitPrice": 3450000.00,
    "totalAmount": 6900000.00,
    "holdExpiresAt": "2026-10-26T16:10:00",
    "payment": { "status": "UNPAID", "provider": "VNPAY" }
  },
  "timestamp": "2026-10-26T15:55:38.262582800Z"
}
```

Booking statuses: `PENDING_PAYMENT`, `CONFIRMED`, `CANCELLED`, `EXPIRED`, `COMPLETED`.

### POST /api/bookings/{id}/payments

Creates a payment attempt for an owned booking. The request body is empty; the server derives the
amount from the booking. Response:

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "paymentId": 77,
    "provider": "VNPAY",
    "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...",
    "expiresAt": "2026-10-26T16:00:00"
  },
  "timestamp": "2026-10-26T15:55:38.262582800Z"
}
```

- `409` if the booking is not payable, the hold expired, or seats are no longer available.
- Never accept an amount from the client. Recompute from the stored booking.
- Use a unique, non-guessable `vnp_TxnRef` and persist it with the payment row.
- Rate limit payment creation per account and per IP.

### VNPay return and IPN

- `GET /api/payments/vnpay/return` is the browser redirect. Verify the HMAC-SHA512 signature,
  then redirect to the frontend result page. **Do not confirm the booking from the browser return
  alone.**
- `GET /api/payments/vnpay/ipn` is the server-to-server notification. Verify the signature,
  validate the amount and order reference, and confirm the booking idempotently.
- IPN must return VNPay's expected body, not the normal `ApiResponse` envelope:

```json
{ "RspCode": "00", "Message": "Confirm Success" }
```

- Duplicate IPNs must be safe: a unique constraint on the provider transaction reference and a
  status check make confirmation idempotent.
- Do not log secrets, full signatures, or raw provider payloads containing personal data.

### Payment security checklist

- Verify every VNPay signature with the configured secret.
- Compare the provider amount against the stored `totalAmount`.
- Confirm only after a valid IPN, not only after the browser return.
- Keep card data out of the application; VNPay hosts the payment page.
- Release expired holds in a scheduled job; make the job idempotent.
- Add integration tests for success, duplicate IPN, wrong amount, bad signature, expired hold, and
  concurrent seat reservation.
- Add a refund flow only after the payment flow is stable. Refunds are a separate state machine
  and must never be inferred from a cancelled booking.

### Open questions before implementation

- Authenticated checkout only, or guest checkout with an email link? The current UI collects contact
  details but has no login step.
- Hold duration and whether a user can extend it.
- Cancellation/refund policy and who can trigger refunds.
- Promotion codes and whether they change the stored unit price or add a discount line.
- Email/SMS confirmation delivery.
- Invoice generation and tax rules.

## Frontend integration

- The Next.js app in `fe-tour/` reads the API on the server. `API_URL` is the preferred environment
  variable; `NEXT_PUBLIC_API_URL` is a fallback; the default is `http://localhost:8080`.
- Public tour reads use `use cache` with `cacheLife("minutes")` and the tags `tours` and
  `tour-{id}`. `cacheComponents` and `partialPrefetching` remain enabled.
- Sample/demo tours are selected **outside** the cached function, so demo data is never stored as
  live inventory. When the API is unavailable, the UI shows a "Showing sample journeys" notice.
- Checkout creates a real booking, offers reserve-only or VNPay Sandbox, and can resume payment
  from booking details. The VNPay result page reconciles through the backend and never infers
  settlement from the redirect alone. Booking details expose "Check payment status" so a
  traveller can release retry or cancellation after VNPay confirms nonpayment; an unresolved
  attempt keeps both blocked with an explanation.
- Dates are formatted from the API's wall-clock parts without timezone conversion; money is
  formatted as VND.

## Known limitations

- In-memory rate limiting is single-instance only.
- Refresh-token cleanup is scheduled in-process; expired `auth_tokens` rows are not purged yet.
- Email uniqueness is a service-level case-insensitive check with a documented concurrent race.
- The seeded departures are in the past, so the non-empty summary/aggregate path is unverified
  against the shared database.
- SMTP delivery is implemented but was not verified against a real mail server in the final run.
  When SMTP is unconfigured, sending is skipped with a warning.
- Password-reset has the required generic response but is not separately rate-limited.
- Automatically created invoices omit `paymentMethod` for paths that do not supply a settlement
  method; a newly issued or promoted VNPay invoice sets `paymentMethod` to `VNPAY`.
- Ratings cannot be edited or deleted through the API.
- VNPay reconciliation is automatic through signed QueryDr for retry, status, return and
  reconciliation requests; provider refunds remain manual. A missing or ambiguous IPN leaves the
  attempt unresolved even after the payment URL expires, and customer retries are intentionally
  blocked until VNPay confirms the outcome. If VNPay rejects the lookup itself (for example
  Sandbox `99 Unexpected error`), the API reports `reason: "PROVIDER_REJECTED"` with the safe
  provider code and keeps the attempt protected; enabling QueryDr for the merchant and
  registering the server IP are operational prerequisites.
- The frontend has build/lint/manual runtime checks, but no automated browser or component tests.
- Database-backed tests were rerun against an isolated PostgreSQL instance for this change; a
  separate live VNPay Sandbox round trip still requires real merchant credentials and public
  HTTPS ingress.
