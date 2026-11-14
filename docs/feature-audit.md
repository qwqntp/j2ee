# Đánh giá chức năng — Website bán tour du lịch

> Ngày rà soát: 2026-11-04  
> Phạm vi: `tour/` (Spring Boot backend), `fe-tour/` (Next.js frontend), `docs/`, migration và seed.  
> Mục tiêu: xác định website đã **đủ chức năng cho khách hàng (end user)** và **admin quản trị** hay chưa.

---

## 1. Tổng quan kiến trúc

| Lớp | Công nghệ | Hiện trạng |
|---|---|---|
| Backend | Spring Boot 4.1, Java 21, PostgreSQL, Flyway, JWT | Rộng: 20 controller, 18 bảng, đủ entity booking/payment/refund/invoice/promotion |
| Frontend | Next.js 16 (App Router), React 19, TypeScript, Tailwind 4 | Hẹp: chỉ 6 route, chưa có giao diện admin |
| Auth | Access JWT 15 phút + refresh token opaque trong cookie | Đã có, khá đầy đủ |
| Tài liệu | `docs/progress.md`, `docs/api-spec.md` | Lỗi thời so với code hiện tại |

**Nhận xét nhanh:** backend đã vượt xa frontend. Nhiều nghiệp vụ có API nhưng không có giao diện, và một số API chưa được bảo vệ đúng quyền admin.

---

## 2. Chức năng phía khách hàng (end user)

### 2.1. Đã có

| # | Chức năng | Vị trí |
|---|---|---|
| 1 | Trang chủ editorial, tìm kiếm hero | `fe-tour/app/page.tsx` |
| 2 | Danh sách tour + tìm theo từ khóa + phân trang | `fe-tour/app/tours/page.tsx` |
| 3 | Chi tiết tour: lịch trình, điểm đến, phương tiện, HDV, giá, chỗ trống, sold-out | `fe-tour/app/tours/[id]/page.tsx` |
| 4 | Đăng ký tài khoản | `fe-tour/app/register/page.tsx`, `components/auth-form.tsx` |
| 5 | Đăng nhập / refresh / logout | `fe-tour/app/login/page.tsx`, `lib/auth.ts` |
| 6 | Đặt tour thật (tạo booking + giữ chỗ có khóa bi quan) | `components/payment-form.tsx`, `BookingService.create` |
| 7 | Trang thanh toán hiển thị tổng tiền, số khách, ghi chú liên hệ | `fe-tour/app/payment/page.tsx` |
| 8 | Trạng thái lỗi / 404 / skeleton | `app/error.tsx`, `app/not-found.tsx`, `components/skeleton.tsx` |

### 2.2. Còn thiếu

| # | Chức năng thiếu | Ghi chú |
|---|---|---|
| 1 | **“Đơn của tôi”** — lịch sử booking, xem chi tiết, hủy đơn | API `GET /api/bookings` đã có, helper `listBookings()` có trong `lib/api.ts` nhưng **không trang nào dùng** |
| 2 | Hồ sơ cá nhân, đổi mật khẩu | Chưa có route |
| 3 | Quên mật khẩu, xác thực email | Backend có `emailVerified` nhưng không có luồng gửi mail |
| 4 | Bộ lọc thực sự: điểm đến, khoảng giá, ngày khởi hành, thời lượng, sắp xếp | Hiện chỉ tìm theo tên |
| 5 | Thông tin hành khách thật (`Traveler`) | Request chỉ có `departureId`, `guestCount`, `note`; tên/email/SĐT bị nhét vào `booking_note` |
| 6 | Thanh toán thật (VNPay/MoMo/card) | Không có cổng thanh toán, không trang kết quả |
| 7 | Áp dụng mã khuyến mãi | Có bảng `promotions` + CRUD nhưng `BookingService` luôn set `discountAmount = 0` |
| 8 | Đánh giá / rating tour | Chưa có entity, API, UI |
| 9 | Yêu thích / wishlist | Chưa có |
| 10 | Form liên hệ, newsletter | Chỉ có footer tĩnh |
| 11 | Email xác nhận / hủy tour | Chưa có |
| 12 | Hóa đơn cho khách | Có entity `Invoice` nhưng không tự tạo, không có UI |
| 13 | Đa ngôn ngữ | Giao diện đang tiếng Anh |
| 14 | Trang kết quả thanh toán, thử lại | Chưa có |

### 2.3. Kết luận khách hàng

> **~60%** — đủ demo luồng “xem tour → đăng nhập → đặt chỗ”, nhưng chưa đủ vòng đời sau đặt tour (quản lý đơn, thanh toán, hủy, đánh giá).

---

## 3. Chức năng phía admin

### 3.1. Đã có

| # | Chức năng | Vị trí |
|---|---|---|
| 1 | Danh sách tài khoản (chỉ đọc) | `GET /api/admin/accounts` — `AdminController` |
| 2 | CRUD tour | `CRUD /api/admin/tours` — `AdminTourController` |
| 3 | Backend có sẵn CRUD cho departure, promotion, invoice, refund, payment, customer, traveler, guide, image, destination, accommodation, vehicle, account, role | Các controller tương ứng |

### 3.2. Còn thiếu

| # | Chức năng thiếu | Ghi chú |
|---|---|---|
| 1 | **Toàn bộ giao diện admin** | Không có route `/admin`, không component, không link nào trong `fe-tour` |
| 2 | Dashboard thống kê | Không có doanh thu, số đơn, tour bán chạy |
| 3 | Quản lý lịch khởi hành (departure), giá, chỗ, trạng thái | Chỉ có API, không UI |
| 4 | Quản lý booking: danh sách, đổi trạng thái, xác nhận thanh toán, hủy | Chỉ có API |
| 5 | Quản lý người dùng: khóa/mở, đổi role, reset mật khẩu | Chỉ có API đọc danh sách |
| 6 | Quản lý promotion / invoice / refund | Chỉ có API |
| 7 | Upload ảnh tour | Hiện chỉ nhập URL |
| 8 | Báo cáo doanh thu, xuất Excel | Chưa có |
| 9 | Revalidate cache `tours` / `tour-{id}` sau khi admin sửa tour | Chưa có |

### 3.3. Kết luận admin

> **~15–20%** — backend có “nguyên liệu” nhưng **chưa đủ chức năng quản trị** để demo.

---

## 4. Lỗ hổng bảo mật nghiêm trọng

### 4.1. Phân quyền sai — user thường CRUD được dữ liệu hệ thống

`SecurityConfig` chỉ chặn `/api/admin/**` bằng `hasRole("ADMIN")`. Các endpoint CRUD sau chỉ cần **đăng nhập** là dùng được:

| Endpoint | Controller | Rủi ro |
|---|---|---|
| `/api/accounts` | `AccountController` | Xem/xóa/sửa tài khoản bất kỳ |
| `/api/account-roles` | `AccountRoleController` | Sửa role |
| `/api/tour-departures` | `TourDepartureController` | Sửa lịch/giá/chỗ |
| `/api/promotions` | `PromotionController` | Tạo/sửa mã giảm giá |
| `/api/payments` | `PaymentCrudController` | Tạo/sửa payment |
| `/api/refunds` | `RefundCrudController` | Duyệt/xóa refund |
| `/api/invoices` | `InvoiceController` | Sửa hóa đơn |
| `/api/customers` | `CustomerController` | Xem/sửa khách hàng |
| `/api/travelers` | `TravelerController` | Xem/sửa hành khách |
| `/api/tour-guides` | `TourGuideController` | Sửa HDV |
| `/api/tour-images` | `TourImageController` | Sửa ảnh |
| `/api/accommodations` | `AccommodationController` | Sửa lưu trú |
| `/api/travel-vehicles` | `TravelVehicleController` | Sửa phương tiện |
| `/api/tourist-destinations` | `TouristDestinationController` | Sửa điểm đến |

**Cách sửa:** chuyển các controller quản trị sang `/api/admin/**`, hoặc thêm `@PreAuthorize("hasRole('ADMIN')")` / matcher `hasRole("ADMIN")` cho từng nhóm.

### 4.2. Thanh toán bị giả được

- `POST /api/bookings/{bookingId}/payments/callback` (`PaymentService.handleCallback`) nhận `amount`, `method`, `successful` từ client.
- `POST /api/payments` (`PaymentService.create`) nhận `amount`, `status`, `paidAt` từ client.
- Không kiểm tra chữ ký, không kiểm tra chủ sở hữu booking, không so khớp số tiền với `booking_total_amount`.

→ User đã đăng nhập có thể tự tạo payment `SUCCESS` để đơn chuyển `PAID` mà không trả tiền.

**Cách sửa:** tính tiền server-side, xác thực callback (HMAC/signature), kiểm tra ownership, so khớp amount, hoặc tạm thời bỏ callback giả và dùng luồng “admin xác nhận đã thu tiền”.

### 4.3. Các lỗi khác

| # | Lỗi | Vị trí |
|---|---|---|
| 1 | Booking không kiểm tra ngày khởi hành tương lai, chỉ kiểm tra `status = OPEN` | `BookingService.create` |
| 2 | User có thể đổi `accountId` / `status` của booking qua `PUT /api/bookings/{id}` | `BookingUpdateRequest`, `BookingService.update` |
| 3 | Access token lưu `localStorage` (rủi ro XSS) | `fe-tour/lib/auth.ts` |
| 4 | Chưa rate limit cho booking/payment | `RateLimitFilter` chỉ áp dụng auth |
| 5 | Chưa có job dọn refresh token hết hạn | `RefreshTokenService` |
| 6 | Docs lỗi thời: nói booking/payment “chưa làm” nhưng code đã có | `docs/progress.md`, `docs/api-spec.md` |

---

## 5. Bảng tổng hợp mức độ hoàn thiện

| Khu vực | Mức độ | Đánh giá |
|---|---|---|
| Xem & tìm tour | 80% | Tốt, thiếu bộ lọc nâng cao |
| Chi tiết tour | 90% | Đầy đủ thông tin cơ bản |
| Auth | 80% | Đủ đăng ký/đăng nhập/refresh; thiếu quên mật khẩu, xác thực email |
| Đặt tour | 60% | Tạo booking + giữ chỗ OK; thiếu traveler thật, email, hủy |
| Thanh toán | 20% | Chỉ UI, callback không an toàn |
| Quản lý đơn của khách | 0% | Có API, không có UI |
| Admin | 15–20% | Thiếu toàn bộ giao diện và phần lớn nghiệp vụ |
| Bảo mật phân quyền | 30% | Nhiều endpoint CRUD hở cho user thường |
| **Tổng thể** | **~45%** | **Chưa đủ chức năng cho cả khách và admin** |

---

## 6. Roadmap đề xuất

### P0 — bắt buộc để “đủ chức năng”

1. **Sửa phân quyền**: chuyển toàn bộ CRUD quản trị sang `/api/admin/**` hoặc `hasRole('ADMIN')`.
2. **Sửa payment**: tính tiền server-side, callback phải xác thực; hoặc bỏ callback giả, dùng “admin xác nhận đã thu tiền”.
3. **Trang admin tối thiểu**:
   - Đăng nhập admin
   - Dashboard (số tour, số đơn, doanh thu)
   - CRUD tour + departure
   - Danh sách booking + đổi trạng thái
   - Danh sách account + khóa/mở
4. **Trang “Đơn của tôi”**: danh sách, chi tiết, hủy đơn.
5. **Cập nhật docs** `progress.md` và `api-spec.md` cho khớp code.

### P1 — tăng điểm

6. Bộ lọc tìm kiếm: điểm đến, khoảng giá, ngày khởi hành, thời lượng, sắp xếp.
7. Form hành khách + lưu `Traveler`, gửi email xác nhận.
8. Áp dụng promotion vào booking (`discountAmount`).
9. Upload ảnh tour.

### P2 — nâng cao

10. Đánh giá/rating tour.
11. Báo cáo doanh thu, xuất Excel.
12. Tích hợp VNPay sandbox thật.
13. Đa ngôn ngữ Việt/Anh.

---

## 7. Cách kiểm chứng nhanh

```powershell
# Backend
cd tour
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run

# Frontend
cd fe-tour
npm run lint
npx tsc --noEmit
npm test
npm run build
```

Kiểm tra phân quyền thủ công:

1. Đăng nhập bằng tài khoản `ROLE_USER`.
2. Gọi `GET /api/accounts` → nếu trả `200` là **đang hở quyền**.
3. Gọi `POST /api/tour-departures` → nếu tạo được là **đang hở quyền**.
4. Gọi `POST /api/bookings/{id}/payments/callback` với `successful = true` → nếu đơn chuyển `PAID` là **thanh toán bị giả**.

---

## 8. Kết luận

> Website hiện tại **chưa đủ chức năng** cho một hệ thống bán tour phục vụ cả khách hàng và admin.
>
> - Luồng khách cơ bản (xem, tìm, đăng nhập, đặt chỗ): **tạm ổn để demo**.
> - Quản lý đơn hàng, thanh toán và toàn bộ admin: **còn thiếu nhiều**.
> - Có **lỗ hổng phân quyền và thanh toán** cần xử lý trước khi bảo vệ đồ án.
>
> Ưu tiên xử lý P0 trước, sau đó mới mở rộng P1/P2.

---
---

# Đánh giá lại — 14/11/2026 07:54 (Asia/Ho_Chi_Minh)

> **Phạm vi:** rà soát lại toàn bộ `tour/`, `fe-tour/`, migration, test và docs tại commit `dee8fa5`
> (`feat: add my-trips bookings list, detail and cancellation`), so với bản đánh giá ngày 04/11/2026.
> **Phương pháp:** đọc code + controller/service/security, đối chiếu `docs/progress.md` (07/11),
> `docs/api-spec.md` (07/11), chạy lại test backend/frontend (xem §13).
> **Kết luận ngắn:** phần **backend đã tiến rất xa** (phân quyền, payment server-side, email, invoice,
> rating, traveler, promotion, hủy đơn). Phần **giao diện vẫn là khoảng trống lớn nhất**, đặc biệt là
> **toàn bộ admin** và các luồng self-service của khách.

---

## 9. Đối chiếu roadmap P0/P1/P2 của bản 04/11

| Hạng mục cũ | Trạng thái 14/11 | Bằng chứng / ghi chú |
|---|---|---|
| P0.1 Sửa phân quyền CRUD admin | ✅ **Xong** | `SecurityConfig.java:81-98` chặn `hasRole("ADMIN")` cho toàn bộ path cũ; `SecurityConfigTest` có 25 case kiểm tra 403 cho `ROLE_USER` |
| P0.2 Sửa payment | 🟡 **Một phần** | Tính tiền server-side, callback có HMAC-SHA256, kiểm tra ownership (`PaymentService.java:63-92`). **Nhưng** callback bị chặn bởi `ADMIN` (PSP thật không gọi được) và admin có thể `PUT /api/payments/{id}` sang `SUCCESS` không cần chữ ký |
| P0.3 Trang admin tối thiểu | ❌ **Chưa làm** | Không có `app/admin`, không component, không link, không middleware; `grep -i admin` trong `fe-tour/**/*.{ts,tsx}` = 0 |
| P0.4 Trang “Đơn của tôi” | ✅ **Xong** | `app/my-trips/page.tsx`, `app/my-trips/[id]/page.tsx`, `components/my-trips.tsx`, `components/trip-detail.tsx`; hủy đơn `PENDING` qua `POST /api/bookings/{id}/cancel` |
| P0.5 Cập nhật docs | 🟡 **Một phần** | `api-spec.md` + `progress.md` đã cập nhật 07/11 (có email/invoice/rating). `fe-tour/README.md` và `fe-tour/AGENTS.md` vẫn mô tả checkout “chưa tạo booking”, “không có traveler” — đã lỗi thời |
| P1.6 Bộ lọc tìm kiếm | ❌ **Chưa làm** | Backend `GET /api/tours` chỉ có `page`, `size`, `keyword` (`TourController.java:36-42`); FE chỉ truyền `keyword` + `page` |
| P1.7 Form hành khách + email | 🟡 **Một nửa** | Backend nhận `travelers[]` và lưu `Traveler`; email xác nhận/hủy đã có. **FE chưa gửi `travelers[]`**, vẫn ghép tên/email/SĐT vào `booking_note` (`payment-form.tsx:52-58`) |
| P1.8 Áp dụng promotion | 🟡 **Một nửa** | Backend xong: validate mã, hạn dùng, min-order, tính `discountAmount`, tăng/giảm `usedCount`. **FE không có ô nhập mã**, `createBooking` không nhận `promotionCode` (`lib/api.ts:133-142`) |
| P1.9 Upload ảnh tour | ❌ **Chưa làm** | Không có `MultipartFile`/`@RequestPart`; `TourImageRequest` chỉ nhận `imageUrl` |
| P2.10 Đánh giá/rating | 🟡 **Backend xong, UI chưa** | `TourRating` + `V6`; `GET/POST /api/tours/{id}/ratings`; rule: chỉ owner booking `PAID/COMPLETED`, 1 rating/booking. FE không hiển thị/không gửi rating |
| P2.11 Báo cáo doanh thu, Excel | ❌ **Chưa làm** | Không có endpoint stats/revenue/report; `AdminController` chỉ có `GET /api/admin/accounts` |
| P2.12 VNPay sandbox | ❌ **Chưa làm** | Không có gateway, không redirect, không trang kết quả; UI ghi rõ “No online payment is taken” |
| P2.13 Đa ngôn ngữ | ❌ **Chưa làm** | `<html lang="en">` cố định, không i18n |

**Tính năng mới phát sinh từ 04/11 (ngoài roadmap cũ):** xác thực email, quên/đặt lại mật khẩu,
refresh-token rotation + reuse detection, job dọn refresh token, invoice tự động khi `PAID`,
`GET /api/invoices/me`, rating tour, hủy booking + giải phóng chỗ + giảm lượt promotion, header
hiển thị trạng thái đăng nhập.

---

## 10. Chức năng khách hàng — hiện trạng

### 10.1. Đã có và dùng được end-to-end

| # | Chức năng | Ghi chú |
|---|---|---|
| 1 | Trang chủ editorial, tìm kiếm hero | `app/page.tsx` |
| 2 | Danh sách tour + keyword + phân trang | `app/tours/page.tsx`; server-side, cache `tours` |
| 3 | Chi tiết tour đầy đủ | lịch trình, điểm đến, lưu trú, HDV, phương tiện, giá, chỗ trống, sold-out |
| 4 | Đăng ký / đăng nhập / refresh / logout | `lib/auth.ts`, cookie refresh HttpOnly |
| 5 | Đặt tour thật | `POST /api/bookings`, giữ chỗ bằng khóa bi quan, owner-scoped |
| 6 | **“Đơn của tôi”** | tabs Upcoming/Past/Cancelled, load more, chi tiết, trạng thái, travelers, promotion, tổng tiền |
| 7 | **Hủy đơn `PENDING`** | confirm 2 bước, giải phóng chỗ, flash thành công |
| 8 | Trạng thái lỗi/404/skeleton | `error.tsx`, `not-found.tsx`, `skeleton.tsx` |

### 10.2. Còn thiếu hoặc dở dang (xếp theo mức ưu tiên)

| # | Chức năng | Hiện trạng | Phần còn thiếu | Mức |
|---|---|---|---|---|
| 1 | **Quên / đặt lại mật khẩu** | Backend có `forgot-password`, `reset-password` | Không có route/form/UI; không có link ở trang login | 🔴 Cao |
| 2 | **Xác thực email** | Backend phát token + `verify-email` | Không có UI nhập token, không banner nhắc xác thực; **chưa enforce** khi đăng nhập/đặt tour | 🔴 Cao |
| 3 | **Hồ sơ cá nhân / đổi mật khẩu** | Không có | Không có trang `/profile`; backend không có endpoint self-service (chỉ admin sửa account), user không tự đổi mật khẩu khi đã đăng nhập | 🔴 Cao |
| 4 | **Form hành khách thật** | Backend nhận `travelers[]` (tên/SĐT/email) | FE chỉ có stepper số khách + gộp contact vào `note`; detail có hiển thị travelers nếu có nhưng checkout không tạo ra | 🔴 Cao |
| 5 | **Ô nhập mã khuyến mãi** | Backend tính discount khi có `promotionCode` | Checkout không có input, `createBooking` không truyền field; user không thể dùng mã | 🔴 Cao |
| 6 | **Thanh toán thật / trang kết quả** | Payment server-side + invoice | Không có VNPay/MoMo/card, không redirect, không `/payment/result`, không retry; 2 radio “Pay at office / Bank transfer” là **trang trí**, không gửi lên server | 🔴 Cao |
| 7 | **Hóa đơn cho khách** | `Invoice` tự tạo khi `PAID`, `GET /api/invoices/me` | Không hiển thị trong my-trips, không tải/in PDF, không số hóa đơn; hủy đơn không void invoice | 🟠 TB |
| 8 | **Hủy đơn đã thanh toán / hoàn tiền** | User chỉ hủy được `PENDING` chưa thanh toán | Không có luồng “yêu cầu hủy/hoàn tiền” cho `PAID`; refund chỉ admin tạo | 🟠 TB |
| 9 | **Đánh giá tour** | API public GET + POST đã xong | Không có form rating, không hiển thị điểm/đánh giá ở trang chi tiết, không có mục “đánh giá chuyến đi” trong my-trips | 🟠 TB |
| 10 | **Bộ lọc / sắp xếp** | Chỉ keyword | Thiếu điểm đến, khoảng giá, ngày khởi hành, thời lượng, sắp xếp; backend cũng chưa có query param tương ứng | 🟠 TB |
| 11 | **Ảnh tour** | Hiển thị URL từ API | Không upload; ảnh remote dùng `<img>` thô (bỏ qua `next/image`), chưa có gallery/lightbox | 🟡 Thấp |
| 12 | **Liên hệ / newsletter / wishlist** | Footer tĩnh mailto | Không form liên hệ, không newsletter, không yêu thích | 🟡 Thấp |
| 13 | **Đa ngôn ngữ** | Tiếng Anh | Không có switcher Việt/Anh | 🟡 Thấp |
| 14 | **Tour liên quan** | Không có | Trang chi tiết không gợi ý tour khác | 🟡 Thấp |

### 10.3. API đã có nhưng frontend chưa dùng (nợ tích hợp)

| Endpoint / field | Trạng thái |
|---|---|
| `GET /api/tours/{tourId}/ratings` | Public, có phân trang + điểm trung bình — FE không gọi |
| `POST /api/tours/{tourId}/ratings` | Cần Bearer — FE không có form |
| `GET /api/invoices/me` | Cần Bearer — FE không gọi |
| `POST /api/auth/forgot-password` | Public — không có UI |
| `POST /api/auth/reset-password` | Public — không có UI |
| `POST /api/auth/verify-email` | Public — không có UI |
| `travelers[]` trong `POST /api/bookings` | Backend đã lưu — FE không gửi |
| `promotionCode` trong `POST /api/bookings` | Backend đã áp dụng — FE không gửi |
| `booking.travelers`, `booking.promotion` | Đã hiển thị ở chi tiết đơn, nhưng dữ liệu thường rỗng vì checkout không tạo |
| `account.roleName` | Có trong type + localStorage — không component nào đọc để phân quyền |

---

## 11. Chức năng admin — hiện trạng

**Vẫn không có bất kỳ giao diện admin nào.** Không route `/admin`, không layout guard, không
middleware, không link trong header. Toàn bộ thao tác quản trị hiện phải gọi API bằng Swagger/Postman.

### 11.1. Backend đã sẵn sàng

| Nhóm | API | Ghi chú |
|---|---|---|
| Tài khoản | `GET/POST/PUT/DELETE /api/accounts` | Update được `status` (khóa/mở), `roleId`, `password`, `emailVerified` — đủ cho màn quản lý user |
| Vai trò | `/api/account-roles` CRUD | |
| Tour | `/api/admin/tours` CRUD | |
| Lịch khởi hành | `/api/tour-departures` CRUD | |
| Booking | `GET/PUT/DELETE /api/bookings`, `POST /{id}/cancel` | Admin được bypass owner-scope |
| Thanh toán | `/api/payments`, `/api/bookings/{id}/payments`, callback | Admin xác nhận thu tiền được (xem §12) |
| Hoàn tiền | `/api/refunds`, `/api/payments/{id}/refunds` | Có kiểm tra trần hoàn tiền |
| Hóa đơn | `/api/invoices` CRUD + `/by-booking` | |
| Promotion | `/api/promotions` CRUD | |
| Danh mục | customers, travelers, guides, images, destinations, accommodations, vehicles | CRUD |

### 11.2. Còn thiếu

| # | Chức năng | Ghi chú |
|---|---|---|
| 1 | **Toàn bộ UI admin** | Ưu tiên cao nhất; hiện 0% |
| 2 | **Dashboard thống kê** | Không có endpoint: số tour, số booking theo trạng thái, doanh thu, tour bán chạy, công suất |
| 3 | Quản lý booking trên UI | Danh sách, lọc theo trạng thái/ngày, đổi trạng thái, xác nhận thanh toán, hủy + hoàn tiền |
| 4 | Quản lý tài khoản trên UI | Khóa/mở, đổi role, reset mật khẩu (API đã đủ) |
| 5 | Quản lý lịch khởi hành/giá/chỗ | API đủ, chưa có UI |
| 6 | Báo cáo doanh thu + xuất Excel/CSV | Chưa có endpoint lẫn UI |
| 7 | Upload ảnh tour | Chưa có storage/multipart |
| 8 | Audit log thao tác admin | Không có |
| 9 | Revalidate cache `tours` / `tour-{id}` sau khi admin sửa | Không có `revalidateTag`/`revalidatePath`; cache `minutes` nên dữ liệu có thể cũ |
| 10 | Bảo vệ Swagger ở production | `/swagger-ui/**` đang `permitAll` |

> **Mức hoàn thiện admin: ~20%** — backend “nguyên liệu” gần đủ nhưng thiếu 100% giao diện và
> toàn bộ phần thống kê/báo cáo.

---

## 12. Bảo mật — đã sửa gì và còn gì

### 12.1. Đã khắc phục

| Vấn đề cũ | Trạng thái |
|---|---|
| User thường CRUD được accounts, departures, promotions, payments, invoices… | ✅ Tất cả đã `hasRole("ADMIN")` tại `SecurityConfig.java:81-98`; có test 403 |
| User tự đổi `accountId`/`status` của booking | ✅ Chặn 403 cho non-admin (`BookingService.java:173-179`) |
| Client tự gửi `amount`/`status` khi thanh toán | ✅ Server luôn dùng `booking_total_amount`; callback verify HMAC-SHA256; secret rỗng → fail closed 503 |
| Booking không kiểm tra ngày khởi hành | ✅ Chặn departure không `OPEN` hoặc `startDate` đã qua (`BookingService.java:119-124`) |
| Không kiểm tra chủ sở hữu booking | ✅ List/get/update/delete/cancel đều owner-scoped (admin bypass) |
| Refresh token không thu hồi khi reuse | ✅ Rotation + reuse detection thu hồi toàn bộ token; job dọn hàng ngày 03:00 |
| Chưa có email | ✅ Xác thực email, quên mật khẩu, xác nhận booking, hủy booking (async, lỗi SMTP không phá nghiệp vụ) |

### 12.2. Còn tồn tại

| # | Vấn đề | Mức | Chi tiết |
|---|---|---|---|
| 1 | **Callback thanh toán chỉ ADMIN gọi được** | 🔴 Cao | `SecurityConfig.java:97-98` chặn `/api/bookings/*/payments/**` bằng `hasRole("ADMIN")`. PSP thật không thể callback; trong khi đó admin có thể `PUT /api/payments/{id}` → `SUCCESS` **không cần chữ ký**, không audit. Cần tách: callback public + verify HMAC, hoặc chính thức hóa luồng “admin xác nhận đã thu tiền” và bỏ callback giả |
| 2 | **Thanh toán vượt / replay** | 🔴 Cao | Không cap tổng payment `SUCCESS` theo `booking_total_amount`; callback với `txnId` mới cho booking đã `PAID` tạo thêm payment đủ tiền (`PaymentService.java:81-92`) |
| 3 | **Idempotency check trước ownership check** | 🟠 TB | `handleCallback` trả payment đã tồn tại (idempotency, dòng 65-72) **trước khi** gọi `findOwnedBooking` (dòng 74) — admin có thể đọc payment của booking bất kỳ nếu biết cặp (bookingId, txnId) |
| 4 | **Access token trong `localStorage`** | 🟠 TB | `lib/session.ts:6-7` — rủi ro XSS; refresh cookie đã HttpOnly nhưng access token thì không |
| 5 | **Email verification không bắt buộc** | 🟠 TB | Đăng ký xong nhận token ngay; login/booking không kiểm tra `emailVerified` |
| 6 | **Rate limit còn hẹp** | 🟠 TB | Chỉ login/register (10/phút/IP) và refresh (30/phút); chưa limit `forgot-password`, tạo booking, callback |
| 7 | **Không có state machine cho booking** | 🟠 TB | Admin có thể nhảy `PENDING → COMPLETED`, `CANCELLED → PENDING`; đổi `guestCount` không reconcile travelers, không re-check promotion `active`/hạn/usage, không tính lại trạng thái `PAID` |
| 8 | **Không có cutoff đặt tour** | 🟡 Thấp | Chỉ cần `startDate > now`, có thể đặt sát giờ khởi hành |
| 9 | **Rating lộ dữ liệu** | 🟡 Thấp | `TourRatingResponse` trả `accountId`, `accountUsername`, `bookingId` công khai |
| 10 | **Invoice không void khi hủy** | 🟡 Thấp | Booking `PAID` bị hủy vẫn giữ invoice `PAID`; không có số hóa đơn/PDF |
| 11 | **`auth_tokens` không được dọn định kỳ** | 🟡 Thấp | Chỉ ghi đè theo account+type; token hết hạn vẫn nằm lại |
| 12 | **Swagger public** | 🟡 Thấp | `/swagger-ui/**`, `/v3/api-docs/**` `permitAll` cả production |
| 13 | **Cookie `secure=false` mặc định** | 🟡 Thấp | Dev-friendly nhưng phải bật ở production qua env |
| 14 | **Logout không thu hồi access token** | 🟡 Thấp | Access token sống tới hết 15 phút; không denylist |
| 15 | **Guest count không có trần** | 🟡 Thấp | `CreateBookingRequest` chỉ `@Min(1)`; trần thực tế phụ thuộc số chỗ còn lại (đã khóa bi quan) |

**Đánh giá bảo mật phân quyền: ~70%** (tăng từ 30%) — lỗ hổng CRUD nghiêm trọng đã đóng, nhưng
luồng thanh toán vẫn là điểm yếu nhất và chưa thể coi là “thanh toán thật”.

---

## 13. Kiểm chứng đã chạy (14/11/2026)

| Lệnh | Kết quả |
|---|---|
| `tour> .\mvnw.cmd -Dtest=!*IntegrationTests,!TourApplicationTests test` | **132 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS** (07:53) |
| `fe-tour> npx tsc --noEmit` | Pass, không lỗi |
| `fe-tour> npm run lint` | Pass, không cảnh báo |
| `fe-tour> npm test` | **6 file / 39 test pass** (Vitest) |

**Chưa chạy trong lần này:**

- `BookingFlowIntegrationTests` (24) và `CrudIntegrationTests` (13) — cần DB; `progress.md` ghi lần
  chạy đầy đủ gần nhất pass nhưng nên chạy lại khi Supabase ổn định.
- `npm run build` (Next production build).
- Không có E2E (Playwright/Cypress) và không có CI (`.github/workflows` trống).

---

## 14. Bảng điểm cập nhật

| Khu vực | 04/11 | 14/11 | Ghi chú |
|---|---|---|---|
| Xem & tìm tour | 80% | 80% | Vẫn chỉ keyword, chưa có filter/sort |
| Chi tiết tour | 90% | 90% | Đầy đủ; thiếu rating/review, tour liên quan, gallery |
| Auth (tổng) | 80% | 85% | Backend ~95% (verify/reset/rotation); FE thiếu UI quên mật khẩu, xác thực email, profile |
| Đặt tour | 60% | 85% | Traveler + promotion + cancel ở backend; FE chưa dùng traveler/promotion |
| Thanh toán | 20% | 35% | Server-side amount + HMAC + invoice; chưa có gateway, chưa có trang kết quả, callback admin-only |
| Quản lý đơn của khách | 0% | 80% | List/detail/cancel; thiếu invoice, rating, yêu cầu hoàn tiền |
| Đánh giá tour | 0% | 35% | API hoàn chỉnh, UI 0% |
| Admin | 15–20% | 20% | Vẫn 0% UI; thiếu dashboard/report/upload/audit |
| Bảo mật phân quyền | 30% | 70% | Đã đóng lỗ hổng CRUD; còn vấn đề payment/rate-limit/token storage |
| **Tổng thể** | **~45%** | **~62%** | Đủ demo luồng khách tốt hơn nhiều; **chưa đủ để bảo vệ đồ án** vì thiếu admin và thanh toán thật |

---

## 15. Việc cần làm tiếp (cập nhật)

### P0 — chặn bảo vệ đồ án

1. **Admin UI MVP** (lớn nhất, hiện 0%):
   - `app/admin` + layout guard theo `roleName`/role từ `/api/auth/me` (không tin localStorage).
   - Dashboard: cần thêm endpoint `GET /api/admin/stats` (doanh thu, booking theo trạng thái, tour bán chạy, công suất).
   - CRUD tour + departure; danh sách booking + đổi trạng thái; danh sách account + khóa/mở/đổi role.
2. **Chốt luồng thanh toán** — chọn một hướng và làm đúng:
   - (a) VNPay/MoMo sandbox: callback **public** + verify chữ ký, trang kết quả, retry, cap tổng tiền; hoặc
   - (b) “Admin xác nhận đã thu tiền”: bỏ callback giả, bỏ radio trang trí, hiển thị hướng dẫn chuyển khoản + trạng thái, admin xác nhận qua UI.
3. **Self-service khách**: quên/đặt lại mật khẩu, xác thực email, trang hồ sơ + đổi mật khẩu (cần thêm API self-service).
4. **Invoice**: hiển thị trong my-trips, tải/xem, void khi hủy đơn.
5. **Hủy đơn đã thanh toán**: luồng yêu cầu hủy/hoàn tiền cho khách (không chỉ admin).

### P1 — tăng điểm chức năng

6. Checkout: form travelers thật + ô promotion code (backend đã sẵn sàng).
7. Bộ lọc/sắp xếp tour (backend query param + UI).
8. Rating UI: hiển thị điểm/số lượng ở chi tiết tour, form đánh giá trong my-trips.
9. Upload ảnh tour (storage) hoặc tối thiểu preview URL + allowlist domain + `next/image`.
10. Revalidate cache `tours` / `tour-{id}` sau admin sửa và sau booking/cancel.
11. Đồng bộ docs (`README.md`, `fe-tour/AGENTS.md`), thêm CI + ít nhất smoke E2E.

### P2 — nâng cao

12. Báo cáo doanh thu + xuất Excel/CSV.
13. Đa ngôn ngữ Việt/Anh.
14. Wishlist, newsletter, form liên hệ, tour liên quan, gallery/lightbox.
15. Audit log admin + purge `auth_tokens` + rate limit mở rộng.

---

## 16. Kết luận lần 2

> So với 04/11, dự án đã **sửa xong lỗ hổng phân quyền nghiêm trọng nhất** và bổ sung nhiều nghiệp vụ
> backend có giá trị: traveler, promotion, email, invoice, rating, hủy đơn, “Đơn của tôi”.
>
> Tuy nhiên **giao diện vẫn tụt hậu so với backend**:
>
> - **Admin gần như vẫn là 0% UI** — đây là khoảng trống lớn nhất và là rủi ro khi bảo vệ.
> - **Thanh toán chưa thật**: chưa có cổng, chưa có trang kết quả; callback đang bị khóa bởi ADMIN.
> - **Các API mới (rating, invoice, forgot/reset/verify, travelers, promotion) chưa được nối vào UI**.
>
> Nếu chỉ còn thời gian cho một việc: **làm admin UI MVP + dashboard**, vì backend đã đủ để demo
> quản trị. Việc thứ hai là **chốt và hoàn thiện một luồng thanh toán** thay vì để UI “giả vờ”.
