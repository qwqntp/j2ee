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
