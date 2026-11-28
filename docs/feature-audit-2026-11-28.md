# Đánh giá chức năng — Website bán tour du lịch (bản cập nhật 28/11/2026)

> **Ngày rà soát:** 2026-11-28 (Asia/Ho_Chi_Minh)  
> **Phạm vi:** `tour/` (Spring Boot backend), `fe-tour/` (Next.js frontend), migration, test, CI và docs.  
> **Mốc so sánh:** bản đánh giá 04/11/2026 và 14/11/2026 trong `docs/feature-audit.md` (file đó giữ lại làm bản cũ).  
> **Commit gốc:** `5879bba` (2026-11-28, merge `admin-screen`). Kiểm chứng trên working tree tại commit này, có rename **đang staged, chưa commit**: `V10__seed_account_roles.sql` → `V11__seed_account_roles.sql`; `fe-tour/components/admin-tours.tsx` có thêm 3 dòng trắng chưa commit. Giữ nguyên các thay đổi đó.  
> **Mục tiêu:** cập nhật phiên bản đánh giá sau các đợt bổ sung chức năng mới; xác định website đã đủ chức năng cho khách hàng và admin chưa, và còn gì trước khi đưa lên production.

> **Thay đổi lớn nhất từ 14/11 → 28/11:** admin từ **0% giao diện** lên **15 page chức năng + 1 page catch-all** kèm dashboard, phân quyền 3 role (`USER` / `STAFF` / `ADMIN`); thanh toán từ UI "trang trí" sang **VNPay sandbox có ký số, IPN và đối soát**; bổ sung quên/đặt lại mật khẩu, xác thực email, hồ sơ, bộ lọc tour, hành khách + mã khuyến mãi trong checkout, hóa đơn in, đánh giá + kiểm duyệt, yêu cầu hoàn tiền cho khách, upload ảnh Cloudinary và CI.

---

## 1. Tổng quan kiến trúc

| Lớp | Công nghệ | Hiện trạng 28/11 |
|---|---|---|
| Backend | Spring Boot 4.1, Java target 21, PostgreSQL, Flyway (`V1`–`V11` trong working tree sau rename staged), Redis cache, JWT | 34 file controller; 3 role; có `GET /api/admin/stats`, revenue CSV, upload, rating moderation; HEAD chưa chứa rename nên còn trùng version V10 |
| Frontend | Next.js 16 (App Router), React 19, TypeScript, Tailwind 4, Vitest | 16 page trong nhóm `(public)` (gồm cả trang khách cần đăng nhập) + 15 page admin chức năng + 1 catch-all; shell dùng chung; **không có `middleware.ts`**, guard là client-side |
| Auth | Access JWT 15 phút + refresh token opaque trong cookie HttpOnly | Rotation + reuse detection, hash SHA-256, job dọn 03:00; có đủ luồng self-service |
| Thanh toán | VNPay sandbox PAY 2.1.0, HMAC-SHA512 (IPN), QueryDr, signed return; callback nội bộ HMAC-SHA256 | Có code tích hợp thanh toán sandbox, chưa chứng minh giao dịch trực tiếp với VNPay hoặc chuyển tiền thật; legacy callback vẫn chỉ admin gọi được |
| Kiểm thử / CI | Maven Surefire, Vitest, ESLint, `.github/workflows/ci.yml` | Chạy lại 28/11: **619 test backend không cần DB**, **241 test FE / 40 file**, lint + TypeScript pass; backend local chạy Java 26.0.1, CI cấu hình Java 21 + Node 22 |
| Tài liệu | `docs/feature-list.md` (24/11), `docs/api-spec.md`, `docs/progress.md`, `docs/admin/*` | `docs/feature-audit.md` đã lỗi thời nhiều mục — bản này thay thế phần đó |

**Nhận xét nhanh:** backend và frontend đã **gần cân bằng** (khác với kết luận "backend vượt xa frontend" của bản 04/11 và 14/11). Rủi ro còn lại nằm ở: (1) một số lỗ hổng bảo mật chưa đóng, (2) luồng admin chưa được kiểm chứng với DB/SMTP/VNPay thật, (3) cache Next.js chưa được revalidate sau khi admin sửa dữ liệu, (4) nhiều tài liệu và một vài đoạn copy UI vẫn nói "demo/không thanh toán thật".

---

## 2. Chức năng phía khách hàng (end user)

### 2.1. Đã triển khai và nối FE–BE theo code

Các luồng dưới đây có component, API client và backend tương ứng. Việc đọc code và chạy test không cần DB **không chứng minh toàn bộ luồng hoạt động end-to-end với PostgreSQL, SMTP và VNPay thật**; phạm vi bằng chứng thực thi xem §7.

| # | Chức năng | Vị trí / bằng chứng |
|---|---|---|
| 1 | Trang chủ editorial, tìm kiếm hero | `fe-tour/app/(public)/page.tsx`, `lib/tours.ts` |
| 2 | Danh sách tour + **lọc điểm đến, giá, ngày, thời lượng + sắp xếp** + phân trang | `app/(public)/tours/page.tsx`, `components/tour-filters.tsx`, `lib/tour-filters.ts`; backend `TourSearchCriteria.java`, `TourSearchRepositoryImpl.java` |
| 3 | Chi tiết tour đầy đủ + danh sách departure, sold-out | `app/(public)/tours/[id]/page.tsx` |
| 4 | Đăng ký / đăng nhập / refresh / logout / **Google sign-in** | `components/auth-form.tsx`, `components/google-signin-button.tsx`, `lib/auth.ts`, `lib/session.ts` |
| 5 | **Quên / đặt lại mật khẩu** | `app/(public)/forgot-password`, `app/(public)/reset-password`, `components/*-form.tsx`, `lib/reset-password.ts`; forgot tối đa 5 request/phút/IP, reset 10 request/phút/IP |
| 6 | **Xác thực email** | `app/(public)/verify-email`, `components/verify-email.tsx`, `POST /api/auth/verify-email` |
| 7 | **Hồ sơ + đổi mật khẩu self-service** | `app/(public)/account/profile`, `components/account-profile.tsx`, `GET/PUT /api/profile`, `POST /api/auth/change-password` |
| 8 | Checkout thật: **mỗi ghế một hành khách, mã khuyến mãi, giữ chỗ khóa bi quan, tính tiền server-side** | `components/payment-form.tsx`, `lib/booking-form.ts`, `lib/api.ts`, `BookingService.create` |
| 9 | **Thanh toán VNPay sandbox** + trang kết quả + đối soát | `app/(public)/payment/vnpay-return`, `components/vnpay-return.tsx`, `lib/vnpay.ts`, `lib/vnpay-polling.ts`; backend `VnpayPaymentService.java`, `VnpayPaymentController.java` |
| 10 | "Đơn của tôi": `account/bookings` + alias `my-trips`, chi tiết, hủy `PENDING` | `components/account-bookings.tsx`, `components/my-trips.tsx`, `components/trip-detail.tsx` |
| 11 | Từ chi tiết đơn: trả tiền VNPay, đối soát, xác nhận, hoàn thành, **yêu cầu hoàn tiền**, hủy | `components/trip-detail.tsx`, `POST /api/bookings/{id}/refund-request` |
| 12 | **Xem / in hóa đơn** | `app/(public)/invoices/[bookingId]`, `components/invoice-view.tsx`, `GET /api/invoices/me` |
| 13 | **Đánh giá tour** (chỉ booking `PAID`/`COMPLETED`, 1 lần/booking) | `components/tour-ratings.tsx` ở chi tiết tour + trong chi tiết đơn; backend `TourRatingService.java` |
| 14 | Thông báo thao tác, skeleton, empty state, 404, error boundary | `lib/flash.ts`, `components/flash-toast.tsx`, `components/skeleton.tsx`, `app/(public)/error.tsx`, `not-found.tsx` |

> **Lưu ý kiểm chứng:** VNPay bị **tắt chủ ý ở `next dev`** (`lib/vnpay.ts:12-15`: `NODE_ENV !== "development"`). Ở môi trường dev, checkout chỉ có "Reserve only"; để thử luồng VNPay cần frontend build/production và backend được cấu hình sandbox hợp lệ. Source hiện không có cờ cấu hình để bật lại VNPay trong `next dev`.

### 2.2. Còn thiếu / dở dang

| # | Chức năng | Hiện trạng | Mức |
|---|---|---|---|
| 1 | **Enforce xác thực email** | Có UI + API, nhưng login và đặt tour **không kiểm tra** `emailVerified` (chỉ luồng Google enforce) | 🔴 Cao |
| 2 | **Access token trong `localStorage`** | `lib/session.ts:7-8,89-107`; refresh cookie đã HttpOnly nhưng access token thì không — rủi ro XSS | 🔴 Cao |
| 3 | **Trạng thái yêu cầu hoàn tiền cho khách** | Khách gửi được yêu cầu (`refund-request`), nhưng không có trang theo dõi trạng thái; xử lý tiền vẫn thủ công ở admin | 🟠 TB |
| 4 | **Hóa đơn bị lỗi hiển thị khi không có bản ghi** | Ở mode khách, `components/invoice-view.tsx:64-71,106-131` tìm invoice trong 100 bản ghi đầu; kết quả `null` không bật `missing`, nên vẫn render "#pending / CARD" kèm Print. Có thể xảy ra cả khi invoice chưa tồn tại hoặc nằm ngoài 100 bản ghi đầu | 🟠 TB |
| 5 | **Copy UI không phản ánh đúng chế độ sandbox** | `app/(public)/tours/[id]/page.tsx:325` "No online payment is taken in this demo"; `components/invoice-view.tsx:258` "No real payment was processed"; `components/site-footer.tsx:50` "Checkout preview". Cần nói rõ có booking và payment record, còn VNPay là sandbox, không khẳng định có chuyển tiền thật | 🟠 TB |
| 6 | `/account/bookings` thiếu lọc/tab trạng thái | FE có type `BookingListFilters` tại `lib/booking-list.ts`; backend hỗ trợ status/date/q và sort qua Pageable, nhưng UI luôn gửi `{}`; tab chỉ có ở `/my-trips` (client-side) | 🟠 TB |
| 7 | Đánh giá chỉ lấy 5 đầu | `GET /api/tours/{id}/ratings?page=0&size=5`, không có phân trang trên UI | 🟡 Thấp |
| 8 | Không có cutoff đặt tour / trần số khách | Chỉ kiểm tra `startDate > now`; `CreateBookingRequest` chỉ `@Min(1)` (trần thực tế = chỗ còn lại) | 🟡 Thấp |
| 9 | Liên hệ / newsletter / wishlist / tour liên quan / i18n | Theo `docs/feature-list.md:207-211` nằm **ngoài phạm vi**; giữ tiếng Anh, không PDF (dùng print trình duyệt) | ⚪ Out of scope |

### 2.3. Kết luận khách hàng

> **~90–92% về độ phủ chức năng theo nhận định chủ quan**, không phải tỷ lệ E2E đã pass — từ "đủ demo luồng xem → đặt chỗ" (60% hồi 04/11) lên gần như đầy đủ các màn hình và API của vòng đời: tự chăm sóc tài khoản, tạo booking, tích hợp VNPay sandbox, quản lý đơn, hóa đơn, đánh giá và yêu cầu hoàn tiền. Các điểm trừ tập trung vào bảo mật phiên, thông điệp UI chưa rõ chế độ sandbox và thiếu trang theo dõi hoàn tiền; việc chuyển tiền hoàn vẫn thủ công.

---

## 3. Chức năng phía admin

### 3.1. Đã có

**Khu quản trị có 16 file `page.tsx`: 15 page chức năng và 1 page catch-all** (`fe-tour/app/admin/**`), navigation định nghĩa tại `lib/admin-navigation.ts`. Các số "15 page chức năng" bên dưới không tính catch-all.

| Nhóm | Route | Chức năng |
|---|---|---|
| Tổng quan | `/admin` | Dashboard: doanh thu, payment thành công/trung bình, booking theo trạng thái, top tour, cảnh báo sức chứa; lọc từ/đến ngày cho payment/doanh thu và tải revenue CSV (`components/admin-dashboard.tsx`, `GET /api/admin/stats`). Bộ lọc này không áp dụng cho toàn bộ panel |
| Nghiệp vụ | `/admin/bookings` | Lọc status/ngày/từ khóa/sort, phân trang 10/20/50, chi tiết + travelers + lịch sử thanh toán; thu tiền mặt, xác nhận, hoàn thành, hủy `PENDING` (`components/admin-bookings.tsx`) |
| Nghiệp vụ | `/admin/payments` | Danh sách attempt, lọc, đối soát VNPay pending, copy txn id (`components/admin-payments.tsx`) |
| Nghiệp vụ | `/admin/refunds` | UI cho `REQUESTED → APPROVED → DONE` hoặc `REQUESTED → REJECTED`; ghi nhận xử lý thủ công, không gọi API chuyển tiền hoàn (`components/admin-refunds.tsx`). Backend chưa enforce đầy đủ thứ tự chuyển trạng thái (`RefundService.update`) |
| Nghiệp vụ | `/admin/customers`, `/admin/customers/[id]` | Tìm khách + lịch sử booking, link tới đơn/hóa đơn |
| Nghiệp vụ | `/admin/invoices`, `/admin/invoices/[bookingId]` | Tìm/lọc/phân trang, xem và in (`components/admin-invoices.tsx`, `components/invoice-view.tsx`) |
| Nghiệp vụ | `/admin/departures`, `/admin/departures/[id]/passengers` | Lịch khởi hành + danh sách hành khách, in / xuất CSV |
| Cấu hình | `/admin/accounts` | Tìm/lọc, tạo/sửa/xóa, khóa/mở, đổi role (có Staff), toggle email verified, đặt mật khẩu tạm; bảo vệ admin cuối (`AccountService.java:65-126`) |
| Cấu hình | `/admin/tours` | CRUD tour + modal quản lý departure |
| Cấu hình | `/admin/promotions` | CRUD, toggle active, lọc vòng đời (lọc chỉ trên trang hiện tại) |
| Cấu hình | `/admin/catalog` | CRUD điểm đến, lưu trú, phương tiện, HDV, ảnh; **upload ảnh Cloudinary** qua `POST /api/admin/uploads` |
| Kiểm duyệt | `/admin/ratings` | Danh sách, duyệt/từ chối/xóa (`AdminRatingController.java`, `V10__rating_moderation.sql`) |
| Hệ thống | `/admin/[...missing]` | Catch-all → 404 |

**Phân quyền:** `components/admin-gate.tsx` + `lib/admin-permissions.ts` + `components/admin-page-access.tsx` — re-validate qua `GET /api/auth/me`, so khớp `account.id`/`roleName` giữa session đã validate và localStorage. `ROLE_STAFF` chỉ vào Bookings / Payments / Customers / Invoices / Departures; `ROLE_ADMIN` vào tất cả. **Backend `SecurityConfig.java:89-128` là lớp quyết định cuối** (staff allow-list theo method/path, phần còn lại `hasRole("ADMIN")`). Không có `middleware.ts`.

**Hiệu năng:** commit `237234b` chuyển danh sách booking/invoice sang phân trang server-side, URL-backed query state, debounce search, abort request, cache chi tiết locally; `AdminBookingQueryService` giới hạn `max-page-size = 50`; truy vấn departure dùng `@EntityGraph` để giảm N+1 (`TourRepository.java:14-15`, `TourDepartureRepository.java:21-22`).

### 3.2. Còn thiếu

| # | Chức năng | Ghi chú |
|---|---|---|
| 1 | **Revalidate cache Next.js** | Không có `revalidateTag`/`revalidatePath` nào trong source — `lib/tours.ts` cache `tours` / `tour-{id}` theo `cacheLife("minutes")`. Admin sửa tour xong, trang công khai có thể còn dữ liệu cũ cho tới khi cache tự revalidate/hết hạn; chưa đo độ trễ thực tế. Backend đã evict Redis cache nhưng không invalidate được Next.js |
| 2 | **Admin tạo booking hộ khách** | Chưa có (`docs/feature-list.md:198`, `docs/admin/task-requirements-audit-result.md`) |
| 3 | **Audit log thao tác admin** | Không có entity/controller audit chuyên biệt; `PUT /api/payments/{id}` và các thao tác nhạy cảm chưa lưu lịch sử người thực hiện + nội dung thay đổi |
| 4 | **State machine cho `PUT /api/bookings/{id}`** | Admin vẫn nhảy tự do `PENDING → COMPLETED`, `CANCELLED → PENDING`; đổi `guestCount` không reconcile `Traveler` (`BookingService.java:205-292`) |
| 5 | Dashboard chi tiết | Thiếu panel booking gần đây, payment thất bại, tỷ lệ công suất; top tour và booking theo trạng thái chưa lọc theo ngày. Cửa sổ **90 ngày tới chỉ áp dụng cho departure/capacity**, top tour và capacity risk giới hạn 5; doanh thu/payment đã có bộ lọc `from/to` (`AdminStatsService.java:52-103`) |
| 6 | Báo cáo mở rộng | `GET /api/admin/reports/revenue.csv` **đã hỗ trợ khoảng ngày tự chọn**, bắt buộc truyền cả `from` và `to` với `from <= to`, tổng hợp doanh thu theo ngày (`AdminReportController.java:26-35`, `RevenueReportService.java:23-43`); còn thiếu xuất Excel và báo cáo theo từng tour |
| 7 | Hóa đơn | Chưa void khi hủy đơn, chưa xuất PDF (theo quyết định giữ bản in trình duyệt), chưa tìm theo mã booking |
| 8 | Rating UI mỏng | Lấy 100 bản ghi một lần, không lọc/phân trang; `docs/admin-gap-checklist.md:155-159` chưa xong |
| 9 | Tour relation selectors | Form tour giữ nguyên ID quan hệ, không có dropdown chọn (`components/admin-tours.tsx:25-37`) |
| 10 | Warning "instant navigation" của Next | `docs/admin/task-next-instant-fix-result.md` ghi rõ **chưa sửa**, cần kiểm tra trình duyệt |
| 11 | Kiểm chứng môi trường thật | Đã có lần chạy full suite với PostgreSQL nhưng chưa có full green được ghi nhận sau lỗi timing (§7.2); chưa xác nhận SMTP/VNPay trực tiếp. Repo ghi nhận smoke test desktop/mobile bằng API fixture (`docs/admin/task-staff-role-result.md:470`), chưa thay thế E2E với backend thật hoặc audit a11y |

### 3.3. Kết luận admin

> **~85% về độ phủ chức năng theo nhận định chủ quan** — từ 15–20% hồi 14/11. Frontend đã có các màn hình quản trị chính và phân quyền 3 role. Còn thiếu audit, báo cáo mở rộng, state machine, revalidate cache và **kiểm chứng thực tế**; các gap payment, booking và hiệu lực JWT là rủi ro tính đúng đắn/bảo mật, không chỉ là tính năng nâng cao.

---

## 4. Bảo mật — đã sửa gì và còn gì

### 4.1. Đã khắc phục từ bản 14/11

| Vấn đề cũ | Trạng thái hiện tại |
|---|---|
| User thường CRUD accounts/departures/promotions/payments/invoices… | ✅ User thường bị chặn; CRUD quản trị mặc định `hasRole("ADMIN")`, có allow-list giới hạn cho staff (`SecurityConfig.java:89-128`). Chạy lại: `SecurityConfigTest` **46 case**, `StaffRouteSecurityTest` **264 case** |
| Client tự gửi `amount` khi thanh toán | ✅ Server tính tiền từ booking; VNPay dùng **số dư còn phải trả** (`total - tổng SUCCESS`), đối chiếu `vnp_Amount` với attempt, ký HMAC-SHA512 và có QueryDr |
| Callback giả tạo payment `SUCCESS` | ✅ Có luồng tích hợp sandbox thay thế: IPN public có route-token + chữ ký (`VnpayPaymentController.java:80-107`), FE return là POST có xác thực; mock payment mặc định tắt |
| Idempotency check trước ownership check | ✅ `PaymentService.java:78-89` — kiểm tra chữ ký và ownership trước khi dedup; cross-booking → 409 |
| Owner-scope booking/payment | ✅ Booking của user được owner-scoped; admin bypass, staff được đọc và confirm/complete/cancel theo quyền nghiệp vụ, không được generic booking PUT/DELETE. Endpoint VNPay phía khách **chỉ đúng owner, không bypass cho admin/staff** (`VnpayPaymentService.java:262-265`); có endpoint đối soát quản trị riêng |
| Refresh token reuse | ✅ Rotation + reuse detection, hash SHA-256, job dọn 03:00; đổi/reset mật khẩu thu hồi toàn bộ **refresh token cũ**, nhưng access JWT cũ vẫn hợp lệ đến khi hết hạn; đổi mật khẩu cấp refresh token mới cho phiên hiện tại (`AuthService.java:125-170`) |
| Email pipeline | ✅ Gửi bất đồng bộ sau commit, có timeout; xác thực/quên mật khẩu/xác nhận/hủy booking; lỗi SMTP không phá nghiệp vụ |
| Không có CI | ✅ `.github/workflows/ci.yml` — PR + push `main`, backend Java 21, FE Node 22 |
| Upload ảnh | ✅ `POST /api/admin/uploads` + Cloudinary (`AdminUploadController.java`) |
| Đánh giá chưa kiểm duyệt | ✅ `V10__rating_moderation.sql` + `/api/admin/ratings`; chỉ đếm rating `APPROVED` |

### 4.2. Còn tồn tại

| # | Vấn đề | Mức | Chi tiết |
|---|---|---|---|
| 1 | Callback legacy `/api/bookings/*/payments/callback` vẫn **admin-only** | 🟠 TB | PSP thật không gọi được; luồng VNPay đã thay thế nhưng endpoint cũ vẫn tồn tại. Nên gỡ bỏ hoặc ghi rõ là nội bộ |
| 2 | Payment thủ công ghi `SUCCESS` **không có trần tổng** | 🔴 Cao | Admin được `POST /api/payments` và `PUT /api/payments/{id}`; staff chỉ được **POST `CASH/SUCCESS`**, không PUT/DELETE (`PaymentCrudController.java:44-59`, `SecurityConfig.java:101-127`). `PaymentService.create:174-182` tạo payment bằng toàn bộ tổng booking; update có thể đổi payment sang SUCCESS mà không cap tổng; `recomputeBookingStatus` mark `PAID` khi `paid >= total`. Attempt VNPay pending/success có guard riêng và reference quản lý bị khóa sửa |
| 3 | `PUT /api/bookings/{id}` không có state machine | 🟠 TB | `BookingUpdateRequest.java:6-11` cho phép mọi status; `confirm`/`complete`/`cancel`/`refund-request` có guard đúng nhưng generic PUT thì không; đổi `guestCount` không reconcile travelers |
| 4 | Email verification **không bắt buộc** khi login/đặt tour | 🟠 TB | `AuthService.login:85-102`, `BookingService.create:136-203` chỉ check mật khẩu/status/departure |
| 5 | Access token trong `localStorage` | 🟠 TB | `fe-tour/lib/session.ts:7-8,89-107` — rủi ro XSS; logout chỉ thu hồi refresh, không denylist access token |
| 6 | Rate limit chưa phủ booking/payment/VNPay | 🟠 TB | `RateLimitFilter.java:40,56-64`, `FixedWindowRateLimiter.java:25-38`: giới hạn **mỗi phút/IP**, login + register dùng chung bucket 10, google 10, refresh 30, forgot 5, reset 10, change-password 10; in-memory per instance |
| 7 | `auth_tokens` không dọn định kỳ | 🟡 Thấp | Chỉ có 1 `@Scheduled`: `RefreshTokenService.java:92`; token hết hạn chỉ bị ghi đè khi phát hành lại |
| 8 | Swagger public cả production | 🟡 Thấp | `SecurityConfig.java:76-78` (`/swagger-ui/**`, `/v3/api-docs/**` permitAll) |
| 9 | Cookie `secure=false` mặc định | 🟡 Thấp | `application.properties:44` (`APP_COOKIE_SECURE:false`) — phải bật qua env khi deploy |
| 10 | Invoice không void khi hủy đơn `PAID` | 🟡 Thấp | `InvoiceService.java:84-135` không có đường void; hủy/hoàn tiền không đụng invoice |
| 11 | Rating lộ dữ liệu | 🟡 Thấp | `TourRatingResponse.java:10-12` trả `accountId`, `accountUsername`, `bookingId` công khai |
| 12 | Không có cutoff đặt tour / trần guest | 🟡 Thấp | Chỉ `startDate > now`; `@Min(1)` — trần thực tế phụ thuộc chỗ còn |
| 13 | Migration seed đang **staged, chưa commit** | 🟠 TB | Working tree đã có `V10__rating_moderation.sql` + `V11__seed_account_roles.sql`, không trùng version trong source hiện tại; nhưng **HEAD `5879bba` vẫn có cả hai file V10**. Checkout/deploy chỉ từ HEAD chưa nhận rename sẽ gặp trùng version |
| 14 | Docs lỗi thời | 🟠 TB | Nhiều chỗ vẫn nói "V10 seed role", "không có CI", "password reset không rate-limit", "FE không có test", "callback public IPN" — xem Phụ lục B |
| 15 | Chưa kiểm chứng DB/SMTP/VNPay thật | 🟠 TB | Các suite integration cần PostgreSQL bị loại khỏi CI; chưa có lần chạy full green được ghi nhận sau khi sửa timing test |
| 16 | JWT cũ giữ quyền sau khi đổi mật khẩu/khóa tài khoản/hạ role | 🔴 Cao | `JwtAuthenticationFilter.java:43-49` xác thực bằng claim của JWT, không đọc lại status/role từ DB; `AccountService.java:75-97` đổi tài khoản nhưng không làm mất hiệu lực access JWT cũ. Token có thể giữ quyền cũ tới hết TTL 15 phút; FE re-validate `/auth/me` không chặn được việc gọi API trực tiếp bằng JWT cũ |

**Đánh giá bảo mật phân quyền: ~80% theo nhận định chủ quan** (30% → 70% → 80%), không phải chứng nhận an toàn. Đã có các lớp kiểm tra quyền, ownership và chữ ký thanh toán; rủi ro còn lại gồm phiên/JWT giữ quyền cũ, thao tác đặc quyền (payment/booking PUT) và độ bao phủ kiểm thử tích hợp.

---

## 5. Bảng điểm cập nhật

| Khu vực | 04/11 | 14/11 | 28/11 | Ghi chú thay đổi |
|---|---|---|---|---|
| Xem & tìm tour | 80% | 80% | **95%** | Lọc điểm đến/giá/ngày/thời lượng + sắp xếp đã nối FE–BE theo code |
| Chi tiết tour | 90% | 90% | **95%** | Có rating + departure; rating chỉ lấy 5 đầu, còn copy cũ |
| Auth (tổng) | 80% | 85% | **90%** | Google, forgot/reset/verify/profile/change-password; chưa enforce verify, token vẫn localStorage |
| Đặt tour | 60% | 85% | **95%** | Traveler + promotion + lifecycle + refund request; generic PUT chưa có state machine |
| Thanh toán | 20% | 35% | **80%** | VNPay sandbox + IPN ký số + QueryDr + trang kết quả; admin path vẫn chưa cap, dev tắt VNPay |
| Quản lý đơn của khách | 0% | 80% | **95%** | List/detail/cancel/confirm/complete/refund/invoice/rating; thiếu tracking hoàn tiền |
| Đánh giá tour | 0% | 35% | **80%** | UI gửi + hiển thị + kiểm duyệt; thiếu phân trang/lọc |
| Admin | 15–20% | 20% | **85%** | 15 page chức năng + catch-all, dashboard, 3 role, upload, báo cáo CSV; thiếu audit/revalidate/state machine |
| Bảo mật phân quyền | 30% | 70% | **80%** | Đóng CRUD hở + có tích hợp VNPay sandbox; còn JWT giữ quyền cũ/token/đặc quyền/integration test |
| **Tổng thể** | **~45%** | **~62%** | **~85–88%** | **Độ phủ chức năng phù hợp để chuẩn bị demo khách/admin; chưa chứng minh E2E và chưa sẵn production** |

> Tỷ lệ được giữ như nhận định tương đối về **độ phủ chức năng** của người rà soát, dựa trên code + docs tại working tree của commit `5879bba`. Không có rubric/trọng số để tái lập phép chấm; các số này **không phải tỷ lệ test pass, mức an toàn bảo mật hay mức sẵn sàng production**, và chưa được xác nhận bằng E2E.

---

## 6. Roadmap đề xuất (cập nhật)

### P0 — chặn trước khi bảo vệ / deploy

1. **Đóng nhóm rủi ro bảo mật còn mở (§4.2 #2, #3, #4, #5, #13, #16):** thống nhất chính sách bắt buộc xác thực email và enforce ở backend nếu áp dụng; đưa access token ra khỏi `localStorage` (cookie HttpOnly hoặc memory + refresh, kèm bảo vệ CSRF phù hợp nếu dùng cookie); xử lý hiệu lực JWT cũ sau đổi/reset mật khẩu, khóa tài khoản hoặc hạ role; cap tổng payment `SUCCESS` và chặn chuyển trạng thái tuỳ tiện trên generic PUT; **commit rename migration V10→V11 khi được cho phép** để tránh Flyway trùng version trong bản deploy.
2. **Kiểm chứng môi trường thật:** chạy full integration suite với PostgreSQL, thử SMTP và VNPay sandbox có cấu hình, ghi kết quả green. Chạy CI trên Java 21 (hiện machine local dùng JDK 26 biên dịch release 21).
3. **Sửa thông điệp UI** (`No online payment…`, `No real payment…`, `Checkout preview`) để phân biệt booking/payment record với giao dịch VNPay sandbox, không ngụ ý đã chuyển tiền thật; sửa lỗi invoice "pending/CARD" khi không tìm thấy bản ghi.
4. **Swagger/Cookie production:** tắt `/swagger-ui/**` ngoài dev, bật `APP_COOKIE_SECURE=true` qua env.

### P1 — hoàn thiện chức năng

5. **Revalidate cache** `tours` / `tour-{id}` sau khi admin sửa tour/departure (`revalidateTag`/`revalidatePath`).
6. **Hoàn thiện lifecycle booking/refund:** state machine cho `PUT /api/bookings/{id}`, reconcile `Traveler` và các điều kiện promotion khi đổi `guestCount` (backend đã tính lại discount/minimum amount), enforce thứ tự trạng thái refund ở backend. Phần chặn chuyển trạng thái booking tuỳ tiện thuộc P0 ở trên.
7. **Admin tạo booking hộ khách**; thêm lọc/tab trạng thái trên `/account/bookings`.
8. **Tracking yêu cầu hoàn tiền** cho khách (hiện mới chỉ gửi yêu cầu); phân trang/lọc rating ở cả client và admin.
9. **Audit log thao tác admin** (đặc biệt payment/booking/account); bổ sung panel dashboard: booking gần đây, payment thất bại, công suất.
10. Xử lý warning **"instant navigation"** của Next trong khu admin (chưa tìm ra nguyên nhân; nghi ngờ `AdminGate` render trạng thái "Checking admin access…").

### P2 — nâng cao

11. Tự hủy booking `PENDING` quá hạn giữ chỗ bằng job định kỳ (giải phóng chỗ + hoàn lượt promotion).
12. Báo cáo/export mở rộng ngoài revenue CSV theo khoảng ngày đã có: thêm báo cáo theo tour/Excel; mở rộng lọc ngày sang booking/top tour và làm rõ cửa sổ departure/capacity trên dashboard.
13. Bổ sung E2E trình duyệt với backend thật, mở rộng kiểm tra mobile ngoài fixture smoke đã ghi nhận và audit a11y; dọn `auth_tokens`, rate limit dùng Redis thay vì in-memory.

> **Ngoài phạm vi theo `docs/feature-list.md:207-211`:** i18n Việt/Anh, hóa đơn PDF (dùng in trình duyệt), wishlist/newsletter/form liên hệ, tour liên quan, gallery lightbox. Không đưa vào roadmap.

---

## 7. Kiểm chứng

### 7.1. Lệnh kiểm chứng nhanh

```powershell
# Backend — cùng bộ lọc mà CI dùng (không cần DB)
cd tour
.\mvnw.cmd "-Dtest=!*IntegrationTests,!TourApplicationTests" test
# Chỉ chạy với PostgreSQL test cô lập, không dùng DB chung/production:
.\mvnw.cmd test

# Frontend — tiếp từ thư mục tour/ ở trên
cd ..\fe-tour
npm.cmd run lint
.\node_modules\.bin\tsc.cmd --noEmit --incremental false
npm.cmd test
npm.cmd run build             # CI dùng `next build` để type-check
```

### 7.2. Kết quả kiểm chứng và nguồn lịch sử

**Chạy lại trong phiên đối chiếu 28/11/2026**, working tree tại `5879bba` với các thay đổi có sẵn nêu ở đầu tài liệu:

| Hạng mục | Lệnh đã chạy | Kết quả |
|---|---|---|
| Backend không cần DB | `.\mvnw.cmd "-Dtest=!*IntegrationTests,!TourApplicationTests" test` trong `tour/` | **619 tests, 0 failures/errors/skipped — BUILD SUCCESS**; chạy bằng Java **26.0.1**, không phải kiểm chứng runtime Java 21 |
| Frontend tests | `npm.cmd test` trong `fe-tour/` | **241 test / 40 file pass**, exit 0 |
| Frontend lint | `npm.cmd run lint` | Exit 0 |
| Frontend TypeScript | `.\node_modules\.bin\tsc.cmd --noEmit --incremental false` | Exit 0 |

Lệnh `npm test` ban đầu bị PowerShell chặn launcher `npm.ps1`, chưa chạy Vitest; dùng `npm.cmd test` sau đó chạy thành công, không thay đổi execution policy. Các kết quả này **không bao gồm full integration suite có DB hoặc production build**.

**Bằng chứng lịch sử trong repo** — giữ nguyên số liệu theo nguồn, không thay bằng kết quả mới:

| Nguồn | Lệnh / hạng mục | Kết quả |
|---|---|---|
| `docs/admin/task-staff-role-result.md:461-472` (25/11) | Backend `.\mvnw.cmd "-Dtest=!*IntegrationTests,!TourApplicationTests" package` | **616 passed, 0 failures/errors/skipped — BUILD SUCCESS** |
| Cùng nguồn `:468` | `npm test` | **237 test / 40 file pass** |
| Cùng nguồn `:469` | `npm run build` | Nguồn ghi **33 pages generated**; không đồng nhất với số page chức năng đếm trong source |
| Cùng nguồn `:470` | Browser fixture smoke | Ghi nhận frontend production 3348 + API fixture 3349, kiểm tra 3 role và desktop/mobile; **không phải E2E với backend thật hoặc audit a11y** |
| `docs/superpowers/plans/2026-11-22-admin-data-fetching.md:110` | Full suite có DB (22/11) | 417 test, 1 failure, 1 skipped — failure là assert timing 2s; source `tour/src/test/java/com/j2ee/tour/VnpayPaymentIntegrationTests.java:432` đã đổi sang 10s nhưng **chưa tìm thấy lần chạy full green sau đó trong các tài liệu đã đối chiếu** |
| `.github/workflows/ci.yml` | PR + push `main` | Backend Java 21, FE Node 22, `npm ci` → lint → test → build; **không có deploy/secrets** |

**Chưa chạy lại trong phiên đối chiếu này:** DB integration full suite, runtime Java 21, production build, SMTP, VNPay sandbox trực tiếp, browser/mobile/a11y và warning "instant navigation". Đã đọc source + docs và chạy bốn nhóm lệnh không cần DB ở trên; không sửa product source hoặc cấu hình, không commit. Smoke desktop/mobile lịch sử dùng fixture không thay thế các kiểm chứng môi trường thật còn thiếu.

### 7.3. Kiểm tra thủ công nhanh

1. Đăng nhập `ROLE_STAFF`: đích mặc định là `/admin/bookings`; sidebar chỉ Bookings/Payments/Customers/Invoices/Departures. Truy cập trực tiếp `/admin/ratings` phải hiện "No permission"; dashboard `/admin` không thuộc quyền staff.
2. Dùng staff gọi `GET /api/admin/stats` → 403.
3. Dùng user thường gọi `POST /api/accounts` hoặc `POST /api/bookings/{id}/payments/callback` → 403.
4. Sửa chữ ký query VNPay return rồi gửi `POST /api/payments/vnpay/return` → phải bị từ chối; booking không chuyển `PAID`.
5. Gửi lại cùng IPN hợp lệ đã xử lý → service trả `ALREADY_PROCESSED`; HTTP body trả `RspCode: "02"`, `Message: "Order already confirmed"`, không tạo payment thứ hai (`VnpayPaymentController.java:90-95`).
6. Admin sửa tour → mở `/tours` không có filter và trang chi tiết tour ngay sau đó; so sánh dữ liệu với API, theo dõi thời điểm cache tự cập nhật để đo **gap revalidate cache** (§3.2 #1). Không giả định độ trễ bằng một TTL cố định khi chưa đo.
7. Mở `/invoices/{bookingId}` của booking chưa thanh toán → hiện hóa đơn "#pending/CARD" là **bug** (§2.2 #4).

---

## 8. Kết luận

> So với bản 14/11, dự án đã **triển khai phần lớn hạng mục chức năng P0/P1 cũ theo code**: admin không còn là điểm trống lớn nhất, có tích hợp VNPay sandbox, và các API mới (rating, invoice, forgot/reset/verify, traveler, promotion, filter) đã được nối vào giao diện. Chưa có bằng chứng toàn bộ luồng pass E2E với các dịch vụ thật.
>
> - **Khách hàng (~90% độ phủ chức năng, ước lượng chủ quan):** có màn hình/API từ khám phá, đặt tour, VNPay sandbox đến quản lý đơn, hóa đơn, đánh giá và yêu cầu hoàn tiền.
> - **Admin (~85% độ phủ chức năng, ước lượng chủ quan):** có các màn hình quản trị chính, dashboard, phân quyền `STAFF` riêng, upload ảnh và kiểm duyệt rating; đã có smoke desktop/mobile bằng API fixture.
> - **Chưa sẵn production:** access token nằm `localStorage`, JWT cũ còn giữ quyền sau khóa tài khoản/hạ role/đổi mật khẩu, email verification chưa enforce, payment/booking đặc quyền thiếu cap/state machine, rename migration mới staged chưa có trong HEAD, cache Next.js chưa revalidate và chưa có bằng chứng full integration green sau lỗi timing gần nhất.
>
> Nếu cần ưu tiên: **xử lý P0 mục 1–2 trong §6** (JWT/token, chính sách verify, payment cap/booking state, migration và kiểm chứng DB/SMTP/VNPay). Số lượng màn hình đã khá đầy đủ, nhưng tỷ lệ ước lượng không thay thế bằng chứng tính đúng đắn và độ tin cậy khi chạy thật.

---

## Phụ lục A — Đối chiếu nhanh 14/11 → 28/11

| Hạng mục bản 14/11 | Trạng thái 28/11 | Bằng chứng |
|---|---|---|
| P0.1 Sửa phân quyền CRUD admin | ✅ Xong + mở rộng | `SecurityConfig.java:89-128`, 3 role, staff allow-list, `SecurityConfigTest` |
| P0.2 Sửa payment | ✅ Xong (luồng khách), 🟡 legacy | VNPay IPN public ký HMAC-SHA512 + QueryDr; callback cũ vẫn admin-only |
| P0.3 Trang admin tối thiểu | ✅ Xong theo code | 15 page chức năng + 1 catch-all `/admin/**`, `admin-gate.tsx`, dashboard |
| P0.4 "Đơn của tôi" | ✅ Xong, mở rộng | `account/bookings`, `my-trips`, refund-request, invoice, rating |
| P0.5 Cập nhật docs | 🟡 Một phần | `feature-list.md` (24/11) khá đầy đủ; `api-spec.md`/`progress.md`/README/AGENTS vẫn lỗi thời |
| P1.6 Bộ lọc tìm kiếm | ✅ Xong | `tour-filters.tsx` + `TourSearchRepositoryImpl.java` |
| P1.7 Form hành khách + email | ✅ Xong | `booking-form.ts` gửi `travelers[]`; mail sau commit |
| P1.8 Áp dụng promotion | ✅ Xong | `promotionCode` từ checkout tới `BookingService` |
| P1.9 Upload ảnh tour | ✅ Xong | `AdminUploadController.java` + Cloudinary |
| P2.10 Đánh giá/rating | ✅ Xong + kiểm duyệt | `tour-ratings.tsx`, `admin/ratings`, `V10__rating_moderation.sql` |
| P2.11 Báo cáo doanh thu | 🟡 Một phần | `GET /api/admin/reports/revenue.csv` + nút tải trên dashboard **đã hỗ trợ khoảng ngày tự chọn, bắt buộc cả `from/to` hợp lệ**; chưa Excel/báo cáo theo tour |
| P2.12 VNPay sandbox | ✅ Xong | `VnpayGateway`, `VnpayQueryClient`, return + IPN + reconcile |
| P2.13 Đa ngôn ngữ | ⚪ Không làm | Quyết định giữ tiếng Anh (`feature-list.md:209`) |

**Tính năng mới phát sinh ngoài roadmap 14/11:** `ROLE_STAFF` + allow-list và guard từng trang; Google sign-in; đổi mật khẩu/hồ sơ self-service; xác thực email UI; Cloudinary upload; kiểm duyệt rating (`V10`); endpoint stats + revenue CSV; passenger manifest in/CSV; admin payments/refunds/accounts/catalog/ratings screens; admin booking filters + phân trang; CI GitHub Actions; session state cleared trên logout/đổi tài khoản; import VNPay sandbox PAY 2.1.0.

## Phụ lục B — Docs/copy cần làm sạch

| Vị trí | Nội dung lỗi thời | Hiện trạng |
|---|---|---|
| `docs/feature-audit.md` (toàn bộ phần 14/11) | Không có admin UI, không VNPay, không CI, 132/39 test | Bản này thay thế |
| `docs/api-spec.md:832,1014,1025,1058` | Chỉ 2 role; reset không rate-limit; FE không có test; V10 seed role | 3 role; forgot **5 request/phút/IP**, reset **10 request/phút/IP**; chạy lại **241 test/40 file**; `V11__seed_account_roles.sql` trong working tree sau rename staged |
| `docs/progress.md:19,32,73,111-113,249-252,327` | Cập nhật 07/11; booking/payment chưa làm; rate limit hẹp; refresh không dọn | Đều đã triển khai; `RefreshTokenService.java:92-96` |
| `tour/AGENTS.md`, `tour/README.md:123-131` | Migration V1–V8; callback admin-only không đổi | Có V9/V10/V11 và IPN public ký số |
| `fe-tour/AGENTS.md:25,27,37` | Token ở `lib/auth.ts`; order detail chỉ cancel/confirm/complete; Admin link chỉ ROLE_ADMIN | `lib/session.ts`; có refund/rating/invoice; ADMIN **và** STAFF |
| `fe-tour/README.md:57-58,62-73,92-93` | Thiếu refund/rating/invoice; thiếu một số route admin; "Showing sample journeys" | Các route đó có thật; chuỗi "sample journeys" không tồn tại trong code |
| `app/(public)/tours/[id]/page.tsx:325` | "No online payment is taken in this demo" | Đã có code tích hợp VNPay sandbox; cần ghi rõ sandbox, chưa xác nhận giao dịch trực tiếp với cổng trong phiên này |
| `components/invoice-view.tsx:258` | "No real payment was processed" | Có payment record và tích hợp sandbox; không đồng nghĩa chuyển tiền thật. Copy nên nêu đúng môi trường/phương thức thay vì khẳng định chung |
| `components/site-footer.tsx:50` | "Checkout preview" | Checkout tạo booking thật |
| `docs/admin-gap-checklist.md:184`, `docs/admin/task-requirements-audit-result.md:173,179,245,277` | Không có CI | `.github/workflows/ci.yml` tồn tại |

## Lịch sử đính chính

- **2026-11-28 — sau đối chiếu source và chạy lại kiểm tra không cần DB:** sửa số controller/page và case phân quyền; phân biệt kết quả mới **619 backend / 241 frontend** với số lịch sử **616 / 237**; thay khẳng định E2E/thanh toán thật bằng phạm vi bằng chứng sandbox; đính chính quyền payment của staff, ownership VNPay, phạm vi lọc ngày và smoke mobile bằng fixture. Làm rõ refresh-token revocation khác với hiệu lực access JWT, bổ sung rủi ro JWT giữ quyền cũ; sửa đơn vị rate limit theo phút và cách đọc kết quả IPN trùng. Không thay đổi code, số liệu lịch sử hay các phần chưa có bằng chứng kiểm chứng trực tiếp.
