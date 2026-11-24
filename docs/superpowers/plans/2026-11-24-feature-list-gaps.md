# Feature-list Gaps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the first four unimplemented items of `docs/feature-list.md` §9: refund UI + paid-booking refund flow, tour rating UI + moderation, tour image upload, dashboard date filter + revenue CSV export.

**Architecture:** Four independent vertical slices in §9 order. Backend reuses existing `RefundService`, `BookingService` seat-release helpers, and `AdminStatsService`; new code adds owner-scoped refund requests, rating moderation, a Cloudinary proxy upload, and date-windowed reporting. Frontend adds admin pages/components and small `lib/api.ts` helpers; no state/data/form library.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Maven wrapper, PostgreSQL + Flyway, JUnit 5/Mockito; Next.js 16 App Router, React 19, TypeScript strict, Tailwind 4, Vitest, npm.

**Spec:** `docs/superpowers/specs/2026-11-24-feature-list-gaps-design.md`

## Global Constraints

- Never commit or push. Project rule: `never push or commit without permission`.
- Flyway: `V1`–`V9` applied. Next migration is `V10`. Never edit applied migrations.
- `spring.jpa.hibernate.ddl-auto=validate`: entity and schema change in the same task.
- Status strings must match SQL `CHECK` values (`PENDING`, `PAID`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `REQUESTED`, `APPROVED`, `REJECTED`, `DONE`).
- Seat release goes through `BookingService.releaseSeats`; no second decrement path.
- `RefundService.requestRemaining` locks the payment row; keep that guarantee.
- `GET /api/tours/**` stays public; `/api/admin/**` stays `ROLE_ADMIN`; owner scope stays in the service layer.
- No new Maven or npm dependencies. Use Java 21 `java.net.http.HttpClient` and existing Jackson.
- Frontend commands: `npm run lint`, `npx tsc --noEmit`, `npm test`, `npm run build`. npm only.
- Backend tests need the local PostgreSQL from `application-test.properties`; no H2, no Testcontainers.
- One focused assertion-based check per new rule. Tests that touch sums, refund caps, or seat counts must be `@SpringBootTest` integration tests, not mocked repositories.

## Review Focus

- Customer asks refund on `COMPLETED`/`CANCELLED` booking or one with no successful payment → expect 409, no Refund row, no seat change. Test: `refundRequestRejectsCompletedBooking` in Task 1.
- Two refund requests race / requested amount exceeds payment → expect locked-payment cap holds, at most one `REQUESTED` refund for the full amount. Test: `refundRequestReservesFullAmountOnce` in Task 1.
- Pending rating leaks publicly or inflates average → expect public list returns only `APPROVED`, average over approved rows only. Test: `pendingRatingHiddenUntilApproved` in Task 2.
- Cloudinary unconfigured, or file wrong type/too large → expect 503 when blank, 400 on validation, no outbound call. Test: `uploadFailsClosedWithoutConfig` + `uploadRejectsOversizedFile` in Task 3.
- Date range ignores boundaries or includes outside payments → expect only payments with `paymentPaidAt` inside `[from,to]` counted, invalid `from > to` → 400. Test: `revenueCsvRespectsDateWindow` in Task 4.

---

### Task 1: Customer refund request for paid bookings

**Files:**
- Modify: `j2ee/tour/src/main/java/com/j2ee/tour/service/BookingService.java`
- Modify: `j2ee/tour/src/main/java/com/j2ee/tour/controller/BookingController.java`
- Test: `j2ee/tour/src/test/java/com/j2ee/tour/BookingRefundIntegrationTests.java` (create)

**Interfaces:**
- Consumes: `RefundService.requestRemaining(Long paymentId, String reason)`; `PaymentRepository.findByBookingBookingId(Long)`; existing private `releaseSeats(TourDeparture, int)` and `releasePromotionUsage(Booking)` in `BookingService`; existing `sendBookingCancellation(Booking)`.
- Produces: `BookingService.requestRefund(Long id, AuthPrincipal principal)` returning `BookingResponse`; endpoint `POST /api/bookings/{id}/refund-request` returning `ApiResponse<BookingResponse>`. Session auth only (no new `SecurityConfig` rule needed — falls through to `anyRequest().authenticated()`).

- [ ] **Step 1: Write the failing integration tests**

Create `BookingRefundIntegrationTests.java` with `@SpringBootTest`, `@ActiveProfiles("test")`, `@Transactional`, and the fixture setup copied from `BookingFlowIntegrationTests.setUp()` (role, account, tour, departure, PAID booking, one `SUCCESS` `CARD` payment). Autowire `BookingService`, `BookingRepository`, `RefundRepository`, `TourDepartureRepository`. Add two tests:

```java
@Test
void refundRequestReservesFullAmountOnce() {
    var result = bookingService.requestRefund(booking.getBookingId(), principal);
    assertEquals("CANCELLED", result.status());
    var refunds = refundRepository.findAll();
    assertEquals(1, refunds.size());
    assertEquals("REQUESTED", refunds.get(0).getRefundStatus());
    assertEquals(0, new BigDecimal("100.00").compareTo(refunds.get(0).getRefundAmount()));
    assertEquals(originalBookedSlots,
            departureRepository.findById(departureId).orElseThrow().getDepartureBookedSlots());
    assertThrows(ApiException.class,
            () -> bookingService.requestRefund(booking.getBookingId(), principal));
}

@Test
void refundRequestRejectsCompletedBooking() {
    booking.setBookingStatus("COMPLETED");
    bookingRepository.save(booking);
    assertThrows(ApiException.class,
            () -> bookingService.requestRefund(booking.getBookingId(), principal));
    assertTrue(refundRepository.findAll().isEmpty());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd j2ee/tour && ./mvnw -Dtest=BookingRefundIntegrationTests test`
Expected: compile failure — `requestRefund` does not exist.

- [ ] **Step 3: Implement the service method**

In `BookingService`, inject `RefundService` via the existing constructor pattern. Implement `public BookingResponse requestRefund(Long id, AuthPrincipal principal)`:
- use existing `findOwnedForUpdate(id, principal)`;
- reject unless status is `PAID` or `CONFIRMED`, else `ApiException(HttpStatus.CONFLICT, ...)`;
- reject if no `SUCCESS` payment for the booking (`PaymentRepository.findByBookingBookingId`), else 409;
- for each `SUCCESS` payment call `refundService.requestRemaining(payment.getPaymentId(), "Customer requested cancellation")`;
- lock the departure with `findByIdForUpdate`, call `releaseSeats(departure, booking.getBookingGuestCount())`, `releasePromotionUsage(booking)`;
- set status `CANCELLED`, save, call `sendBookingCancellation(saved)`, return `BookingResponse.from(saved)`;
- annotate `@Transactional` and `@CacheEvict(cacheNames = "tours", allEntries = true)` exactly as `cancel` does.

- [ ] **Step 4: Add the controller endpoint**

In `BookingController`, add `@PostMapping("/{id}/refund-request")` method `requestRefund(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal)` delegating to `bookingService.requestRefund`, returning `ResponseEntity<ApiResponse<BookingResponse>>`. Mirror the `cancel` method.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd j2ee/tour && ./mvnw -Dtest=BookingRefundIntegrationTests test`
Expected: PASS.

- [ ] **Step 6: Verify no regression**

Run: `cd j2ee/tour && ./mvnw test`
Expected: PASS.

---

### Task 2: Rating moderation and UI

**Files:**
- Create: `j2ee/tour/src/main/resources/db/migration/V10__rating_moderation.sql`
- Modify: `j2ee/tour/src/main/java/com/j2ee/tour/model/TourRating.java`
- Modify: `j2ee/tour/src/main/java/com/j2ee/tour/dto/response/TourRatingResponse.java`
- Modify: `j2ee/tour/src/main/java/com/j2ee/tour/repository/TourRatingRepository.java`
- Modify: `j2ee/tour/src/main/java/com/j2ee/tour/service/TourRatingService.java`
- Modify: `j2ee/tour/src/main/java/com/j2ee/tour/controller/TourRatingController.java` (only if list signature changes)
- Create: `j2ee/tour/src/main/java/com/j2ee/tour/dto/request/RatingModerationRequest.java`
- Create: `j2ee/tour/src/main/java/com/j2ee/tour/controller/AdminRatingController.java`
- Test: `j2ee/tour/src/test/java/com/j2ee/tour/RatingModerationIntegrationTests.java` (create)
- Test: `j2ee/tour/src/test/java/com/j2ee/tour/controller/TourRatingControllerTest.java` (update constructor calls)
- Modify: `j2ee/fe-tour/lib/api.ts`, `j2ee/fe-tour/lib/types.ts`
- Create: `j2ee/fe-tour/components/tour-ratings.tsx`, `j2ee/fe-tour/components/admin-ratings.tsx`
- Modify: `j2ee/fe-tour/app/(public)/tours/[id]/page.tsx`, `j2ee/fe-tour/components/trip-detail.tsx`
- Create: `j2ee/fe-tour/app/admin/ratings/page.tsx`
- Modify: `j2ee/fe-tour/lib/admin-navigation.ts`

**Interfaces:**
- Consumes: `TourRatingService.list(Long tourId, Pageable)`; `TourRatingResponse.from(TourRating)`; `TourRatingPageResponse` record.
- Produces: DB column `tour_ratings.rating_status`; `TourRating.ratingStatus`; `TourRatingResponse.status`; `TourRatingService.listAll(Pageable)`, `moderate(Long id, String status)`, `delete(Long id)`; `GET/PUT/DELETE /api/admin/ratings`; TS types `TourRating`, `TourRatingPage`, `RatingModerationInput`; API helpers `getTourRatings`, `createTourRating`, `listAdminRatings`, `moderateRating`, `deleteRating`.

- [ ] **Step 1: Write the failing integration test**

Create `RatingModerationIntegrationTests.java` (`@SpringBootTest`, `@ActiveProfiles("test")`, `@Transactional`), reusing the fixture from `BookingFlowIntegrationTests` (tour + PAID booking + principal). Add:

```java
@Test
void pendingRatingHiddenUntilApproved() {
    var created = ratingService.create(tour.getTourId(),
            new TourRatingRequest(booking.getBookingId(), 5, "Great"), principal);
    assertEquals("PENDING", created.status());
    assertEquals(0, ratingService.list(tour.getTourId(), PageRequest.of(0, 20)).ratingCount());

    ratingService.moderate(created.id(), "APPROVED");

    var publicPage = ratingService.list(tour.getTourId(), PageRequest.of(0, 20));
    assertEquals(1, publicPage.ratingCount());
    assertEquals(new BigDecimal("5.00"), publicPage.averageRating());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd j2ee/tour && ./mvnw -Dtest=RatingModerationIntegrationTests test`
Expected: compile failure — `status()`/`moderate` do not exist.

- [ ] **Step 3: Add migration and entity field**

Create `V10__rating_moderation.sql`:
```sql
ALTER TABLE tour_ratings
    ADD COLUMN IF NOT EXISTS rating_status VARCHAR(16) NOT NULL DEFAULT 'PENDING';
UPDATE tour_ratings SET rating_status = 'APPROVED' WHERE rating_status = 'PENDING';
ALTER TABLE tour_ratings
    ADD CONSTRAINT ck_rating_status CHECK (rating_status IN ('PENDING','APPROVED','REJECTED'));
CREATE INDEX IF NOT EXISTS idx_rating_status_tour
    ON tour_ratings (rating_status, tour_id, rating_created_at DESC);
```
Add `@Column(name = "rating_status", nullable = false, length = 16) private String ratingStatus;` to `TourRating`.

- [ ] **Step 4: Update DTOs and repository**

Add `String status` to `TourRatingResponse` and map from `rating.getRatingStatus()` (update constructor calls in `TourRatingControllerTest`). Add to `TourRatingRepository`:
- `Page<TourRating> findByTourTourIdAndRatingStatus(Long tourId, String status, Pageable pageable)`
- `@Query("select avg(rating.ratingValue) from TourRating rating where rating.tour.tourId = :tourId and rating.ratingStatus = 'APPROVED'") Double averageRatingApproved(@Param("tourId") Long tourId)`

- [ ] **Step 5: Update service and admin controller**

In `TourRatingService.create`, set `rating.setRatingStatus("PENDING")`. Change `list` to use the approved-only repository methods. Add `listAll(Pageable)` (`PageResponse<TourRatingResponse>`), `moderate(Long id, String status)` (accept only `APPROVED`/`REJECTED`, else `ApiException(HttpStatus.BAD_REQUEST, ...)`), and `delete(Long id)`. Create `RatingModerationRequest(String status)` with `@NotBlank`. Create `AdminRatingController` at `/api/admin/ratings` with `GET` (Pageable), `PUT /{id}`, `DELETE /{id}`, following `RefundCrudController` conventions.

- [ ] **Step 6: Run backend tests**

Run: `cd j2ee/tour && ./mvnw -Dtest=RatingModerationIntegrationTests,TourRatingControllerTest test`
Expected: PASS. Then `cd j2ee/tour && ./mvnw test`.

- [ ] **Step 7: Add frontend types and API helpers**

In `lib/types.ts` add `TourRating` (id, tourId, accountId, accountUsername, bookingId, rating, comment, status, createdAt) and `TourRatingPage = PageData<TourRating> & { averageRating: number; ratingCount: number }`. In `lib/api.ts` add `getTourRatings(tourId, page=0, size=5)`, `createTourRating(token, tourId, input)`, `listAdminRatings(token, page=0, size=20)`, `moderateRating(token, id, status)`, `deleteRating(token, id)`.

- [ ] **Step 8: Build rating components and pages**

Create `components/tour-ratings.tsx`: server-friendly list rendering average + approved comments, and a client form that calls `createTourRating` for a booking. Mount the list in `app/(public)/tours/[id]/page.tsx`; mount the form in `components/trip-detail.tsx` shown only for `PAID`/`COMPLETED` bookings without an existing rating. Create `components/admin-ratings.tsx` (table + approve/reject/delete) and `app/admin/ratings/page.tsx` (mirror `app/admin/promotions/page.tsx`). Add `{ href: "/admin/ratings", label: "Ratings", icon: "search" }` to `ADMIN_SECTIONS` in `lib/admin-navigation.ts`.

- [ ] **Step 9: Verify frontend**

Run: `cd j2ee/fe-tour && npm run lint && npx tsc --noEmit && npm test && npm run build`
Expected: all PASS.

---

### Task 3: Cloudinary image upload

**Files:**
- Modify: `j2ee/tour/src/main/resources/application.properties`
- Create: `j2ee/tour/src/main/java/com/j2ee/tour/service/CloudinaryUploadService.java`
- Create: `j2ee/tour/src/main/java/com/j2ee/tour/dto/response/ImageUploadResponse.java`
- Create: `j2ee/tour/src/main/java/com/j2ee/tour/controller/AdminUploadController.java`
- Test: `j2ee/tour/src/test/java/com/j2ee/tour/service/CloudinaryUploadServiceTest.java` (create)
- Modify: `j2ee/fe-tour/lib/api.ts`, `j2ee/fe-tour/components/admin-tours.tsx`, `j2ee/fe-tour/components/admin-catalog.tsx`

**Interfaces:**
- Consumes: `ApiException`, `ApiResponse`; existing `ObjectMapper` bean; Spring `MultipartFile`.
- Produces: `ImageUploadResponse(String url, String publicId)`; `CloudinaryUploadService.upload(MultipartFile file)`; `POST /api/admin/uploads` (multipart) returning `ApiResponse<ImageUploadResponse>`; TS `uploadImage(token, file): Promise<{ url: string; publicId: string }>`.

- [ ] **Step 1: Write the failing unit tests**

Create `CloudinaryUploadServiceTest.java` (Mockito, no network). Construct the service with blank config and with a fake 5 MB+ `MockMultipartFile`, plus a wrong-content-type file:

```java
@Test
void uploadFailsClosedWithoutConfig() {
    var service = new CloudinaryUploadService("", "", "", objectMapper);
    assertThrows(ApiException.class, () -> service.upload(pngFile()));
}

@Test
void uploadRejectsOversizedFile() {
    var service = new CloudinaryUploadService("cloud", "key", "secret", objectMapper);
    var ex = assertThrows(ApiException.class,
            () -> service.upload(new MockMultipartFile("file", "big.png", "image/png", new byte[6 * 1024 * 1024])));
    assertEquals(400, ex.getStatus().value());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd j2ee/tour && ./mvnw -Dtest=CloudinaryUploadServiceTest test`
Expected: compile failure — class does not exist.

- [ ] **Step 3: Add configuration**

Append to `application.properties`:
```properties
# Cloudinary image upload. Blank settings fail closed (503).
app.cloudinary.cloud-name=${CLOUDINARY_CLOUD_NAME:}
app.cloudinary.api-key=${CLOUDINARY_API_KEY:}
app.cloudinary.api-secret=${CLOUDINARY_API_SECRET:}
app.cloudinary.folder=tour-images
app.cloudinary.max-bytes=5242880
```

- [ ] **Step 4: Implement the service**

`CloudinaryUploadService` constructor `(@Value config strings..., ObjectMapper)`. `upload(MultipartFile file)`:
- blank cloud/name/key/secret → `ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Image upload is not configured")` before any network call;
- allow only `image/jpeg`, `image/png`, `image/webp`, `image/gif`; size `> maxBytes` → `ApiException(HttpStatus.BAD_REQUEST, ...)`;
- sign `folder=<folder>&timestamp=<epochSeconds>` with SHA-1 of `toSign + apiSecret` (use `MessageDigest`, hex-encode);
- build multipart body manually with `ByteArrayOutputStream` and a random boundary (Java 21 has no multipart publisher), fields `file`, `api_key`, `timestamp`, `folder`, `signature`;
- POST with `java.net.http.HttpClient` to `https://api.cloudinary.com/v1_1/{cloudName}/image/upload`, timeout 15s;
- parse `secure_url` and `public_id` with the injected `ObjectMapper`; return `ImageUploadResponse`.

- [ ] **Step 5: Add the controller**

Create `AdminUploadController` with `@RestController`, `@RequestMapping("/api/admin/uploads")`, `@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)` method `upload(@RequestParam("file") MultipartFile file)` returning `ResponseEntity<ApiResponse<ImageUploadResponse>>`. `/api/admin/**` already requires `ROLE_ADMIN`; no `SecurityConfig` change.

- [ ] **Step 6: Run tests**

Run: `cd j2ee/tour && ./mvnw -Dtest=CloudinaryUploadServiceTest test`, then `cd j2ee/tour && ./mvnw test`
Expected: PASS.

- [ ] **Step 7: Add frontend upload helper**

In `lib/api.ts`, add a small `apiRequestFormData<T>(path, token, file)` helper (no `Content-Type` header; browser sets boundary) and `export function uploadImage(token: string, file: File): Promise<{ url: string; publicId: string }>` calling `/api/admin/uploads`.

- [ ] **Step 8: Wire upload into admin forms**

In `components/admin-tours.tsx`, add a file input next to the `imageUrls` textarea (around line 371) that calls `uploadImage`, appends the returned URL to the textarea value, and shows errors inline. In `components/admin-catalog.tsx`, add the same control to the `images` resource editor. Accept `image/*`, reuse existing button/badge classes.

- [ ] **Step 9: Verify frontend**

Run: `cd j2ee/fe-tour && npm run lint && npx tsc --noEmit && npm test && npm run build`
Expected: all PASS.

---

### Task 4: Date-filtered dashboard and revenue CSV

**Files:**
- Modify: `j2ee/tour/src/main/java/com/j2ee/tour/repository/PaymentRepository.java`
- Modify: `j2ee/tour/src/main/java/com/j2ee/tour/service/AdminStatsService.java`
- Modify: `j2ee/tour/src/main/java/com/j2ee/tour/controller/AdminController.java`
- Create: `j2ee/tour/src/main/java/com/j2ee/tour/service/RevenueReportService.java`
- Create: `j2ee/tour/src/main/java/com/j2ee/tour/controller/AdminReportController.java`
- Test: `j2ee/tour/src/test/java/com/j2ee/tour/RevenueReportIntegrationTests.java` (create)
- Modify: `j2ee/fe-tour/lib/api.ts`, `j2ee/fe-tour/components/admin-dashboard.tsx`

**Interfaces:**
- Consumes: `PaymentRepository.sumByPaymentStatus`, `Payment.paymentPaidAt`, `AdminStatsResponse`, `AdminStatsService.stats()`.
- Produces: `AdminStatsService.stats(LocalDate from, LocalDate to)`; `GET /api/admin/stats?from=&to=`; `RevenueReportService.daily(LocalDate from, LocalDate to)` returning `List<RevenueRow>` with `date`/`amount`; `RevenueReportService.csv(...)` returning `String`; `GET /api/admin/reports/revenue.csv?from=&to=` returning `text/csv`; TS `getAdminStats(token, from?, to?)`, `getRevenueCsv(token, from, to)`.

- [ ] **Step 1: Write the failing integration test**

Create `RevenueReportIntegrationTests.java` (`@SpringBootTest`, `@ActiveProfiles("test")`, `@Transactional`). Insert one `SUCCESS` payment inside the window and one outside (use `Payment` fields `paymentAmount`, `paymentMethod = "CASH"`, `paymentStatus = "SUCCESS"`, `paymentPaidAt`, `paymentCreatedAt`, `booking`). Add:

```java
@Test
void revenueCsvRespectsDateWindow() {
    String csv = revenueReportService.csv(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
    assertTrue(csv.contains("2026-11-15,50.00"));
    assertFalse(csv.contains("2026-10-02"));
    var stats = adminStatsService.stats(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
    assertEquals(0, new BigDecimal("50.00").compareTo(stats.successfulRevenue()));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd j2ee/tour && ./mvnw -Dtest=RevenueReportIntegrationTests test`
Expected: compile failure — methods do not exist.

- [ ] **Step 3: Extend repository and stats service**

In `PaymentRepository` add:
```java
@Query("select coalesce(sum(p.paymentAmount), 0) from Payment p "
     + "where p.paymentStatus = 'SUCCESS' and p.paymentPaidAt between :from and :to")
BigDecimal sumSuccessfulPaymentsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

@Query("select count(p) from Payment p "
     + "where p.paymentStatus = 'SUCCESS' and p.paymentPaidAt between :from and :to")
long countSuccessfulPaymentsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

@Query("select cast(p.paymentPaidAt as date), coalesce(sum(p.paymentAmount), 0) from Payment p "
     + "where p.paymentStatus = 'SUCCESS' and p.paymentPaidAt between :from and :to "
     + "group by cast(p.paymentPaidAt as date) order by 1")
List<Object[]> dailySuccessfulPayments(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
```
In `AdminStatsService`, keep `stats()` unchanged (existing `AdminStatsServiceTest` mocks repositories) and add `stats(LocalDate from, LocalDate to)` that repeats the existing body but uses `sumSuccessfulPaymentsBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay().minusNanos(1))` for `successfulRevenue`, `countSuccessfulPaymentsBetween`, and `averageSuccessfulPayment` recomputed from the windowed revenue/count. Reject `from.isAfter(to)` with `ApiException(HttpStatus.BAD_REQUEST, ...)`. Keep `generatedAt`, tours, bookings, and capacity fields as today.

- [ ] **Step 4: Implement report service and controller**

`RevenueReportService.daily(from, to)` maps repository rows to `record RevenueRow(LocalDate date, BigDecimal amount)`. `csv(from, to)` returns header `date,amount`, `\uFEFF` BOM prefix, ISO dates, plain amounts. `AdminReportController` at `/api/admin/reports` exposes `GET /revenue.csv` with `@DateTimeFormat(iso = DATE)` params, `produces = "text/csv"`, returning `ResponseEntity<String>` with `Content-Disposition: attachment; filename="revenue.csv"`. Return 400 when `from.isAfter(to)` via `ApiException`.

- [ ] **Step 5: Expose date params on stats endpoint**

In `AdminController.stats`, add `@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from` and `to`; call the windowed overload when both are present, else `stats()`. No change to the no-arg behaviour.

- [ ] **Step 6: Run backend tests**

Run: `cd j2ee/tour && ./mvnw -Dtest=RevenueReportIntegrationTests,AdminStatsServiceTest test`, then `cd j2ee/tour && ./mvnw test`
Expected: PASS.

- [ ] **Step 7: Add frontend API helpers**

In `lib/api.ts`, add `apiRequestText` (same fetch envelope handling as `apiRequest` but returns `response.text()` on 2xx), `getAdminStats(token, from?: string, to?: string)` with query appended only when both provided, and `getRevenueCsv(token, from: string, to: string)` using `apiRequestText`.

- [ ] **Step 8: Add dashboard filter and export**

In `components/admin-dashboard.tsx`, add two `<input type="date">` controls plus an "Export CSV" button in `AdminPageHeading.action`. Store `from`/`to` in state, pass to `getAdminStats`, and on export call `getRevenueCsv` then trigger a download via a temporary `Blob` URL. Show `ApiError.message` inline on failure. Keep the existing "Data as of" display.

- [ ] **Step 9: Verify frontend**

Run: `cd j2ee/fe-tour && npm run lint && npx tsc --noEmit && npm test && npm run build`
Expected: all PASS.
