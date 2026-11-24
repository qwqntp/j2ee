# Danh sách chức năng hiện có

> Cập nhật: 24/11/2026  
> Phạm vi: `tour/` (Spring Boot API) và `fe-tour/` (Next.js UI).  
> Nguồn: controller, route, component và migration hiện tại.

**Quy ước**

| Ký hiệu | Ý nghĩa |
|---|---|
| ✅ | Có cả API và giao diện |
| 🟡 | Có API, chưa có giao diện hoặc chỉ dùng nội bộ |
| 🔐 | Chỉ admin |
| 🔧 | Hạ tầng / bảo mật / tích hợp |

---

## 1. Xác thực và tài khoản

| # | Chức năng | Mô tả | Điểm vào |
|---|---|---|---|
| 1 | Đăng ký | Tạo tài khoản email + mật khẩu, băm BCrypt, cấp access token và refresh cookie, gửi mail xác thực | `/register`, `POST /api/auth/register` |
| 2 | Đăng nhập | Email + mật khẩu; access JWT 15 phút, refresh token trong cookie HttpOnly | `/login`, `POST /api/auth/login` |
| 3 | Đăng nhập Google | Nhận Google ID token, xác minh và cấp phiên; nút Google hiển thị khi cấu hình client id | `/login`, `/register`, `POST /api/auth/google` |
| 4 | Duy trì phiên | Refresh token xoay vòng; phát hiện token bị dùng lại và thu hồi toàn bộ phiên của tài khoản | `POST /api/auth/refresh`, `lib/auth.ts` |
| 5 | Đăng xuất | Thu hồi refresh token đang dùng và xóa cookie | `POST /api/auth/logout` |
| 6 | Quên mật khẩu | Gửi mail chứa token dùng một lần, hạn 30 phút; phản hồi chung để không lộ email tồn tại | `/forgot-password`, `POST /api/auth/forgot-password` |
| 7 | Đặt lại mật khẩu | Dùng token một lần, đổi mật khẩu và thu hồi mọi refresh token cũ | `/reset-password`, `POST /api/auth/reset-password` |
| 8 | Đổi mật khẩu | Kiểm tra mật khẩu hiện tại, đổi mật khẩu, thu hồi phiên cũ và cấp phiên mới | `/account/profile`, `POST /api/auth/change-password` |
| 9 | Xác thực email | Token hạn 2 ngày; trang xác thực công khai; đổi email sẽ đặt lại trạng thái xác thực | `/verify-email`, `POST /api/auth/verify-email` |
| 10 | Hồ sơ cá nhân | Xem/sửa tên, email, điện thoại, địa chỉ, ngày sinh; đổi email sẽ gửi lại mail xác thực | `/account/profile`, `GET/PUT /api/profile` |
| 11 | Tài khoản hiện tại | Lấy thông tin tài khoản đang đăng nhập | `GET /api/auth/me` |
| 12 | Khóa / mở tài khoản | Admin đổi `status`; tài khoản bị khóa không đăng nhập hoặc refresh được | `/admin/accounts` |
| 13 | Giới hạn tần suất | Chặn theo IP cho đăng nhập/đăng ký, Google, refresh, quên/đặt lại/đổi mật khẩu | `RateLimitFilter` |

---

## 2. Khách hàng — khám phá và chọn tour

| # | Chức năng | Mô tả | Điểm vào |
|---|---|---|---|
| 1 | Trang chủ | Hero tìm kiếm, điểm đến, tour nổi bật, câu chuyện du lịch, lý do tin tưởng, CTA | `/` |
| 2 | Danh sách tour | Catalogue có phân trang, trạng thái rỗng, trang vượt phạm vi và lỗi | `/tours` |
| 3 | Tìm theo từ khóa | Tìm trong tên/mô tả tour, tối đa 100 ký tự | `GET /api/tours?keyword=` |
| 4 | Lọc theo điểm đến | Chọn điểm đến từ danh sách có sẵn | `GET /api/tours/destinations`, `destinationId` |
| 5 | Lọc theo giá | Giá nhỏ nhất / lớn nhất; chỉ khớp departure còn mở trong tương lai | `minPrice`, `maxPrice` |
| 6 | Lọc theo ngày khởi hành | Từ ngày / đến ngày | `startFrom`, `startTo` |
| 7 | Lọc theo thời lượng | Số ngày tối thiểu / tối đa | `minDays`, `maxDays` |
| 8 | Sắp xếp | Mới nhất, giá tăng, giá giảm, ngày khởi hành gần nhất | `sort` |
| 9 | Chi tiết tour | Ảnh, mô tả, lịch trình, điểm đến, lưu trú, hướng dẫn viên, phương tiện, giá, chỗ trống | `/tours/[id]` |
| 10 | Danh sách departure | Các ngày khởi hành tương lai, trạng thái mở/full và số chỗ còn lại | `GET /api/tours/{id}/departures` |
| 11 | Trạng thái sold-out | Departure đầy hiển thị là hết chỗ thay vì biến mất | `TourResponse`, `TourDepartureResponse` |
| 12 | Cache dữ liệu công khai | Next.js cache theo tag `tours` / `tour-{id}`; backend cache Redis cho truy vấn catalogue mặc định | `lib/tours.ts`, `CacheConfig` |
| 13 | Trạng thái tải / lỗi | Skeleton, empty state, trang 404 và error boundary | `components/skeleton.tsx`, `app/error.tsx`, `app/not-found.tsx` |

---

## 3. Đặt tour

| # | Chức năng | Mô tả | Điểm vào |
|---|---|---|---|
| 1 | Checkout | Bắt buộc đăng nhập; chọn số khách, departure và xem giá tạm tính | `/payment`, `components/payment-form.tsx` |
| 2 | Thông tin hành khách | Mỗi ghế một bản ghi: họ tên, điện thoại, email, ngày sinh, giới tính, giấy tờ | `travelers[]` trong `POST /api/bookings` |
| 3 | Mã khuyến mãi | Nhập mã; server kiểm tra hiệu lực, hạn dùng, lượt dùng, giá trị đơn tối thiểu và tính giảm giá | `promotionCode` |
| 4 | Tính tiền phía server | Server tính subtotal, discount, total; client không quyết định số tiền | `BookingService.create` |
| 5 | Giữ chỗ | Khóa bi quan departure, kiểm tra sức chứa và tăng số chỗ đã đặt trong cùng transaction | `TourDepartureRepository.findByIdForUpdate` |
| 6 | Kiểm tra departure | Chỉ cho đặt departure `OPEN` và có ngày khởi hành trong tương lai | `BookingService.create` |
| 7 | Tạo booking | Sinh mã booking, trạng thái ban đầu `PENDING`, gắn tài khoản sở hữu | `POST /api/bookings` |
| 8 | Ghi chú đơn | Lưu ghi chú liên hệ / yêu cầu đặc biệt | `bookingNote` |
| 9 | Vòng đời booking | `PENDING → PAID → CONFIRMED → COMPLETED`; nhánh `CANCELLED` | `BookingService` |
| 10 | Phân quyền theo chủ sở hữu | Khách chỉ xem/sửa/hủy booking của mình; admin được bỏ qua giới hạn này | `AuthPrincipal`, owner-scoped queries |
| 11 | Hủy đơn chưa thanh toán | Chỉ hủy booking `PENDING` chưa có thanh toán thành công; giải phóng chỗ và hoàn lượt promotion | `POST /api/bookings/{id}/cancel` |
| 12 | Xác nhận booking | Chủ đơn hoặc admin xác nhận booking `PAID` | `POST /api/bookings/{id}/confirm` |
| 13 | Hoàn thành chuyến đi | Chủ đơn hoặc admin đánh dấu `CONFIRMED` thành `COMPLETED` | `POST /api/bookings/{id}/complete` |
| 14 | Mail xác nhận / hủy | Gửi mail xác nhận khi tạo booking và mail hủy khi hủy; lỗi SMTP không làm hỏng nghiệp vụ | `EmailService`, `AfterCommitMailDispatcher` |
| 15 | Giữ chỗ, trả sau | Chọn “Reserve only” để tạo booking `PENDING`, thanh toán sau từ chi tiết đơn | `/payment` |

---

## 4. Thanh toán và hóa đơn

| # | Chức năng | Mô tả | Điểm vào |
|---|---|---|---|
| 1 | Thanh toán VNPay Sandbox | Tạo URL thanh toán cho booking thuộc sở hữu, số tiền còn lại | `POST /api/bookings/{id}/payments/vnpay` |
| 2 | Xác minh VNPay return | Kiểm tra chữ ký và đối chiếu số tiền trước khi ghi nhận kết quả | `POST /api/payments/vnpay/return`, `/payment/vnpay-return` |
| 3 | VNPay IPN | Nhận thông báo server-to-server, chống xử lý trùng, trả mã theo chuẩn VNPay | `GET /api/payments/vnpay/ipn` |
| 4 | Kiểm tra trạng thái | Tra trạng thái một giao dịch VNPay | `GET /api/payments/vnpay/status/{reference}` |
| 5 | Đối soát booking | Hỏi VNPay về giao dịch đang chờ và chỉ ghi nhận khi có kết quả hợp lệ | `POST /api/bookings/{id}/payments/vnpay/reconcile` |
| 6 | Thanh toán một phần | Booking thành `PAID` khi tổng các payment `SUCCESS` đạt tổng tiền; hỗ trợ nhiều lần thanh toán | `PaymentService.recomputeBookingStatus` |
| 7 | Chống trùng giao dịch | Deduplicate theo `payment_provider_txn_id`; kiểm tra số tiền và chữ ký | `PaymentService`, `VnpayPaymentService` |
| 8 | Ghi nhận tiền mặt | Admin tạo payment `SUCCESS` để xác nhận đã thu tiền | `/admin/bookings`, `POST /api/payments` |
| 9 | Callback HMAC nội bộ | Callback thanh toán có chữ ký HMAC-SHA256; hiện chỉ admin gọi được | `POST /api/bookings/{id}/payments/callback` |
| 10 | Thanh toán demo | Endpoint giả lập thẻ, mặc định tắt, dùng cho test/demo | `POST /api/bookings/{id}/payments/mock`, `PAYMENT_MOCK_ENABLED` |
| 11 | Hóa đơn tự động | Tạo hóa đơn khi booking chuyển sang `PAID` | `InvoiceService.createForPaidBooking` |
| 12 | Hóa đơn của khách | Danh sách hóa đơn của tài khoản hiện tại | `GET /api/invoices/me` |
| 13 | Xem / in hóa đơn | Trang hóa đơn theo booking, có bố cục in qua trình duyệt | `/invoices/[bookingId]` |
| 14 | Hóa đơn admin | Tìm kiếm, mở và in hóa đơn từ khu quản trị | `/admin/invoices`, `/admin/invoices/[bookingId]` |

---

## 5. Quản lý đơn của khách

| # | Chức năng | Mô tả | Điểm vào |
|---|---|---|---|
| 1 | Danh sách đơn | Phân trang, mới nhất trước; trạng thái rỗng, lỗi và tải lại | `/account/bookings`, `/my-trips` |
| 2 | Chi tiết đơn | Tour, departure, trạng thái, hành khách, promotion, tổng tiền, ghi chú | `/account/bookings/[id]`, `/my-trips/[id]` |
| 3 | Hủy đơn | Xác nhận hai bước; chỉ áp dụng cho `PENDING` chưa thanh toán | `trip-detail.tsx`, `POST /api/bookings/{id}/cancel` |
| 4 | Xác nhận / hoàn thành | Nút xác nhận booking `PAID` và đánh dấu hoàn thành booking `CONFIRMED` | `trip-detail.tsx` |
| 5 | Thanh toán từ chi tiết đơn | Mở VNPay cho booking còn nợ | `startVnpay` |
| 6 | Kiểm tra thanh toán | Đối soát lại giao dịch VNPay đang chờ | `reconcileVnpayBooking` |
| 7 | Liên kết hóa đơn | Mở hóa đơn khi booking đã thanh toán / hoàn thành | `/invoices/[bookingId]` |
| 8 | Thông báo thao tác | Flash toast sau hủy, xác nhận, hoàn thành | `lib/flash.ts`, `components/flash-toast.tsx` |
| 9 | Đồng bộ phiên | Kiểm tra access token, tự refresh, xóa trạng thái riêng của người dùng khi đăng xuất / đổi tài khoản | `ensureSession`, `lib/user-state.ts` |

---

## 6. Quản trị (admin)

| # | Chức năng | Mô tả | Điểm vào |
|---|---|---|---|
| 1 | Khu quản trị | Shell riêng, sidebar, topbar, gate theo `ROLE_ADMIN`, link Admin trên header công khai | `/admin`, `components/admin-gate.tsx` |
| 2 | Dashboard | Doanh thu, số payment thành công, trung bình mỗi payment, booking theo trạng thái, số tour, departure sắp tới, sắp đầy, đã đầy, top tour, cảnh báo sức chứa | `/admin`, `GET /api/admin/stats` |
| 3 | Quản lý booking | Lọc trạng thái / khoảng ngày / từ khóa, sắp xếp, phân trang; xem hành khách và lịch sử thanh toán | `/admin/bookings`, `GET /api/admin/bookings` |
| 4 | Thao tác booking | Ghi nhận tiền mặt, xác nhận, hoàn thành, hủy booking `PENDING`, mở hóa đơn | `/admin/bookings` |
| 5 | Quản lý tour | Danh sách, tạo, sửa, xóa tour; ảnh tour nhập bằng URL | `/admin/tours`, `/api/admin/tours` |
| 6 | Quản lý departure | Tạo, sửa, xóa lịch khởi hành; đổi trạng thái, giá, sức chứa | `/admin/tours`, `/api/tour-departures` |
| 7 | Hủy departure | Chuyển departure sang `CANCELLED` sẽ hủy booking liên quan, giải phóng chỗ, gửi mail và tạo yêu cầu hoàn tiền cho payment thành công | `TourDepartureService.cascadeCancel` |
| 8 | Quản lý tài khoản | Tìm/lọc theo username, email, role, trạng thái; tạo, sửa, xóa; đổi role, khóa/mở, sửa xác thực email, đặt mật khẩu tạm | `/admin/accounts` |
| 9 | Bảo vệ admin cuối cùng | Không cho xóa hoặc vô hiệu hóa admin hoạt động cuối cùng | `AdminService`, `PESSIMISTIC_WRITE` |
| 10 | Quản lý promotion | Danh sách, lọc, tạo, sửa, xóa; mã, phần trăm giảm, ngày, lượt dùng, đơn tối thiểu, trạng thái | `/admin/promotions` |
| 11 | Quản lý hóa đơn | Tìm kiếm hóa đơn, mở chi tiết, in | `/admin/invoices` |
| 12 | Quản lý danh mục | CRUD điểm đến, lưu trú, phương tiện, hướng dẫn viên, ảnh tour; xử lý lỗi ràng buộc 409 | `/admin/catalog` |
| 13 | Xác nhận thao tác | Dialog xác nhận cho thao tác phá hủy, skeleton, empty state và thông báo thành công/lỗi | `components/admin/*` |

---

## 7. API nghiệp vụ backend (chưa có giao diện)

| # | Nhóm | Chức năng | Điểm vào | Trạng thái |
|---|---|---|---|---|
| 1 | Đánh giá tour | Khách đã có booking `PAID`/`COMPLETED` tạo một đánh giá 1–5 sao kèm bình luận | `POST /api/tours/{tourId}/ratings` | 🟡 |
| 2 | Đánh giá tour | Danh sách đánh giá công khai, phân trang, điểm trung bình 2 chữ số | `GET /api/tours/{tourId}/ratings` | 🟡 |
| 3 | Hoàn tiền | Tạo yêu cầu hoàn tiền cho payment, chặn hoàn vượt số đã thu | `POST /api/payments/{paymentId}/refunds` | 🟡 |
| 4 | Hoàn tiền | Danh sách, xem, cập nhật trạng thái, xóa yêu cầu hoàn tiền | `/api/refunds` | 🟡 |
| 5 | Hoàn tiền | Khóa payment khi hoàn tiền; cascade departure hủy tự tạo yêu cầu hoàn phần còn lại | `RefundService` | 🟡 |
| 6 | Tài khoản | CRUD tài khoản và vai trò | `/api/accounts`, `/api/account-roles` | 🔐 |
| 7 | Danh mục tour | CRUD khách hàng, hành khách, hướng dẫn viên, ảnh, lưu trú, phương tiện, điểm đến | `/api/customers`, `/api/travelers`, `/api/tour-guides`, `/api/tour-images`, `/api/accommodations`, `/api/travel-vehicles`, `/api/tourist-destinations` | 🔐 |
| 8 | Bán hàng | CRUD payment, hóa đơn, promotion, departure | `/api/payments`, `/api/invoices`, `/api/promotions`, `/api/tour-departures` | 🔐 |
| 9 | Booking | Danh sách booking admin có lọc trạng thái, ngày, từ khóa, sắp xếp, phân trang | `GET /api/admin/bookings` | 🔐 |
| 10 | Booking | Admin xem/sửa/xóa mọi booking, bỏ qua giới hạn chủ sở hữu | `/api/bookings` | 🔐 |
| 11 | Thống kê | Snapshot dashboard: cửa sổ 90 ngày, top 5 tour, top 5 cảnh báo sức chứa | `GET /api/admin/stats` | 🔐 |
| 12 | Kiểm tra trùng HDV | Một hướng dẫn viên không nhận hai departure chồng thời gian; có trigger DB chống race | `GuideOverlapValidator`, `V8` | 🔧 |

---

## 8. Tích hợp, bảo mật và hạ tầng

| # | Chức năng | Mô tả |
|---|---|---|
| 1 | Cơ sở dữ liệu | PostgreSQL (Supabase), Flyway quản lý migration `V1`–`V9`, Hibernate `ddl-auto=validate` |
| 2 | Xác thực | Access JWT HS256 15 phút; refresh token opaque, chỉ lưu hash SHA-256; rotation và reuse detection |
| 3 | Phân quyền | Ma trận route admin trong `SecurityConfig`; toàn bộ CRUD quản trị yêu cầu `ROLE_ADMIN` |
| 4 | Bảo vệ dữ liệu | Kiểm tra chủ sở hữu booking/payment; admin bypass có chủ đích; không lộ stack trace |
| 5 | Validation | Jakarta Validation cho request; lỗi trả envelope JSON thống nhất |
| 6 | Phân trang | `PageResponse` thống nhất cho các API danh sách |
| 7 | Rate limit | Fixed window theo IP; giới hạn riêng cho nhóm đăng nhập, refresh, quên mật khẩu |
| 8 | CORS | Cấu hình origin qua `APP_CORS_ALLOWED_ORIGINS`, cho phép credentials |
| 9 | Cache | Redis cho đọc catalogue; TTL 10 phút; có thể tắt bằng `SPRING_CACHE_TYPE=none` |
| 10 | Email | Gửi bất đồng bộ sau commit; mail xác thực, quên mật khẩu, xác nhận và hủy booking; lỗi SMTP không phá nghiệp vụ |
| 11 | VNPay | Sandbox PAY 2.1.0; ký và xác minh chữ ký, đối chiếu số tiền, IPN, đối soát |
| 12 | Google | Xác minh Google ID token bằng Nimbus; migration `V9` bổ sung dữ liệu đăng nhập Google |
| 13 | OpenAPI | Swagger UI và `/v3/api-docs`; mô tả bearer auth, phân trang và lỗi |
| 14 | Hình ảnh | Lưu URL ảnh tour (Cloudinary URL); frontend có ảnh fallback cục bộ |
| 15 | Giao diện | Next.js App Router, React 19, Tailwind 4; bố cục responsive, skip link, focus, reduced motion |
| 16 | Kiểm thử | Test backend (unit + integration khi có DB) và test frontend bằng Vitest |
| 17 | Dữ liệu dev | Seed SQL thủ công cho môi trường phát triển (`db/seed/dev_seed.sql`) |
| 18 | Tài liệu | `docs/api-spec.md`, `docs/progress.md`, `docs/feature-audit.md`, `docs/admin-gap-checklist.md` |

---

## 9. Chưa triển khai (tóm tắt) update 24/11/2026 15h39

Các mục dưới đây **chưa có** trong code hiện tại, chỉ ghi để tránh nhầm với chức năng đã có.

**Đang triển khai**

- Hoàn tiền: giao diện hoàn tiền cho admin và luồng khách yêu cầu hủy/hoàn tiền booking `PAID`, kèm quy tắc hoàn theo thời điểm hủy.
- Đánh giá tour: giao diện gửi đánh giá, hiển thị điểm trung bình trên trang tour, kiểm duyệt đánh giá.
- Upload ảnh tour lên Cloudinary / storage; hiện chỉ nhập URL.
- Thống kê: báo cáo doanh thu, xuất Excel/CSV, bộ lọc ngày cho dashboard.

**Cần bổ sung**

- Tự hủy booking `PENDING` quá hạn giữ chỗ bằng job định kỳ, giải phóng chỗ và hoàn lượt promotion.
- Admin: danh sách hành khách theo từng departure (in / xuất CSV).
- Admin: quản lý thanh toán (xem, lọc, đối soát VNPay).
- Admin: quản lý khách hàng (tìm kiếm, xem lịch sử booking).
- Admin tạo booking hộ khách (đặt qua điện thoại / tại quầy).
- Revalidate cache `tours` / `tour-{id}` ngay sau khi admin sửa tour hoặc hủy departure.

**Làm thêm nếu còn thời gian**

- Audit log thao tác admin.
- Admin gửi lại mail / xem trạng thái gửi mail.
- Mở rộng công nghệ: RabbitMQ cho mail/audit, Redis cho rate limit và giữ chỗ có TTL, Spring AI chatbot gợi ý tour, CI bằng GitHub Actions.

**Ngoài phạm vi (không làm)**

- Đa ngôn ngữ Việt–Anh; giao diện giữ tiếng Anh.
- Hóa đơn PDF; giữ bản in qua trình duyệt.
- Wishlist, newsletter, form liên hệ, tour liên quan, gallery lightbox.
