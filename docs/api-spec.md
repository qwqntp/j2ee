# Tour Booking API — v1

Base path: `/api`  
Default local base URL: `http://localhost:8080`

> **Scope:** endpoints marked **Implemented** exist in the backend today. The booking and payment
> section is a **planned contract** — those routes are not implemented and the frontend payment page
> is UI-only.

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

Every controller response uses:

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
| refresh | `/api/auth/refresh` | 30 per minute per IP |

- Exceeding a limit returns `429` with `Retry-After: <seconds>` and
  `{"success":false,"message":"Too many requests","data":null,...}`.
- Public `GET`/`OPTIONS` requests are not rate limited.
- The limiter is per application instance. A multi-instance deployment needs a shared store
  (for example Redis).

## Implemented endpoints

| Method | Path | Auth | Success |
|---|---|---|---|
| `GET` | `/api/tours` | Public | `200` |
| `GET` | `/api/tours/{id}` | Public | `200` |
| `GET` | `/api/tours/{id}/departures` | Public | `200` |
| `POST` | `/api/auth/register` | Public | `201` |
| `POST` | `/api/auth/login` | Public | `200` |
| `POST` | `/api/auth/refresh` | Refresh cookie | `200` |
| `POST` | `/api/auth/logout` | Refresh cookie (optional) | `200` |
| `GET` | `/api/auth/me` | Bearer | `200` |
| `GET` | `/api/admin/accounts` | Bearer `ROLE_ADMIN` | `200` |

### GET /api/tours

Public tour catalogue. Only tours with `status = OPEN` are returned.

Query parameters:

| Name | Type | Default | Rules |
|---|---|---|---|
| `page` | integer | `0` | `>= 0` |
| `size` | integer | `12` | `1..50` |
| `keyword` | string | none | max 100 characters; `%` and `_` are stripped; case-insensitive name search |

Results are sorted by tour id ascending.

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
| `400` | Validation failure, malformed JSON, wrong parameter type, or password over 72 bytes |
| `401` | Missing/invalid access token, bad credentials, or invalid/reused refresh token |
| `403` | Authenticated but not allowed, or disabled account |
| `404` | Tour not found or not public |
| `409` | Username/email conflict or data integrity conflict |
| `429` | Rate limit exceeded; see `Retry-After` |
| `500` | Unexpected server error; no stack trace is returned |

## Enumerations

- Tour status: `DRAFT`, `OPEN`, `CLOSED`, `CANCELLED`. Public APIs expose `OPEN` only.
- Departure status: `OPEN`, `FULL`, `CLOSED`, `CANCELLED`. Public APIs expose future `OPEN` and
  `FULL` only.
- Account status: `1` active, `0` disabled.
- Roles: `ROLE_USER`, `ROLE_ADMIN`.

## OpenAPI / Swagger

- Swagger UI: `GET /swagger-ui.html` (redirects to `/swagger-ui/index.html`).
- OpenAPI JSON: `GET /v3/api-docs`.
- Both are public in `SecurityConfig`.
- `OpenApiConfig` sets the title, version, description and the `bearerAuth` HTTP bearer scheme.
- Use the **Authorize** button in Swagger UI to paste an access token for `/api/auth/me` and
  `/api/admin/accounts`.
- For production, disable the docs or put them behind an admin/network boundary:
  `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`.

## Planned booking and payment contract

> **Not implemented.** There is no booking controller, payment controller, seat-hold table, VNPay
> integration, or booking persistence in the current backend. The frontend `/payment` page is a
> UI preview and never calls a booking endpoint.

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
- The payment page is a UI preview. It does not call `/api/bookings` or any payment endpoint, and
  submitting the form only shows a demo confirmation.
- Dates are formatted from the API's wall-clock parts without timezone conversion; money is
  formatted as VND.

## Known limitations

- In-memory rate limiting is single-instance only.
- Expired refresh-token rows are not purged yet. The `expires_at` index is ready for a scheduled
  cleanup.
- Email uniqueness is a service-level case-insensitive check with a documented concurrent race.
- The seeded departures are in the past, so the non-empty summary/aggregate path is unverified
  against the shared database.
- Booking and payment are not implemented; the planned contract above is a design target.
- The frontend has build/lint/manual runtime checks, but no automated browser or component tests.
