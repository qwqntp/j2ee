# Kế hoạch sửa Database và đồng bộ Project

> **Trạng thái cập nhật:** Đã xử lý các lỗi trong phạm vi, ngoại trừ nhóm cấu hình
> kết nối/migration ở mục I theo yêu cầu sử dụng Supabase sau. Các mục bên dưới
> mô tả cả thay đổi đã thực hiện và phần nghiệp vụ còn cần triển khai khi có
> repository/service.

## Phạm vi rà soát

Đã đối chiếu các phần sau:

- Schema và seed trong `quanlybantour_booking_floor.sql`.
- Các JPA entity trong `tour/src/main/java/com/j2ee/tour/model/`.
- Cấu hình và dependency trong `tour/pom.xml`.
- `tour/src/main/resources/application.properties`.
- Backend service, repository, controller và test hiện có.
- Frontend hiện tại trong `fe-tour/`.

## Kết luận tổng quan

Database hiện có cấu trúc quan hệ nền tảng tương đối đúng cho mô hình:

```text
Tour -> TourDeparture -> Booking -> Traveler
                                  -> Payment -> Refund
```

Tuy nhiên, trước khi phát triển nghiệp vụ thật cần sửa:

1. Cấu hình kết nối database và migration.
2. Một số dữ liệu seed sai hoặc chưa hoàn chỉnh.
3. Mapping giữa entity và database.
4. Ràng buộc toàn vẹn dữ liệu.
5. Cơ chế chống overselling khi đặt tour.
6. Cơ chế chống thanh toán/refund trùng.
7. Cách quản lý các cột cũ và cột booking floor bị trùng ý nghĩa.

## Các thay đổi đã thực hiện

- Giữ nguyên cấu hình Supabase/datasource và migration theo yêu cầu.
- Đồng bộ toàn bộ ID entity với schema `BIGINT GENERATED ... AS IDENTITY` bằng `Long` và `GenerationType.IDENTITY`.
- Sửa `Customer.account` từ `ManyToOne` thành `OneToOne`, khớp `customers.account_id UNIQUE`.
- Đồng bộ `Invoice` với schema mới: dùng `booking_id`, bỏ các field quan hệ cũ `account_id`/`tour_id` và `number_of_people`.
- Thêm departure mẫu cho tour Nha Trang.
- Thêm constraint cho số chỗ, số khách, tiền, ngày, status, payment và refund.
- Thêm composite foreign key để `refund.payment_id` luôn thuộc đúng `refund.booking_id`.
- Thêm unique index cho mã giao dịch payment gateway.
- Đồng bộ identity sequence sau khi seed bằng ID cụ thể.
- Backend compile thành công sau khi đồng bộ entity.

---

# I. Lỗi chặn ứng dụng khởi động

## 1. Thiếu cấu hình PostgreSQL datasource

**Mức độ:** Critical

**File liên quan:**

- `tour/src/main/resources/application.properties`
- `tour/src/test/java/com/j2ee/tour/TourApplicationTests.java`

File properties hiện chỉ có:

```properties
spring.application.name=tour
```

Khi chạy test, Spring Boot báo:

```text
Failed to determine a suitable driver class
```

### Cần sửa

Thêm cấu hình theo profile, không commit secret thật:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
```

Có thể tạo profile local riêng, ví dụ `application-local.properties`, với thông tin database local. Thông tin Supabase/production phải lấy từ biến môi trường hoặc secret manager.

### Kiểm tra sau khi sửa

- `mvnw.cmd test`
- Test kết nối PostgreSQL.
- Test tạo `EntityManagerFactory`.

## 2. Flyway chưa có migration

**Mức độ:** High

**File liên quan:**

- `tour/pom.xml`
- `tour/src/main/resources/`
- `quanlybantour_booking_floor.sql`

Project đã khai báo Flyway nhưng chưa có:

```text
tour/src/main/resources/db/migration/
```

File SQL đang nằm ngoài classpath migration nên Flyway không tự chạy.

### Cần sửa

1. Tách schema thành migration đầu tiên:

```text
tour/src/main/resources/db/migration/V1__create_schema.sql
```

2. Tách seed thành migration riêng nếu muốn tự động seed:

```text
tour/src/main/resources/db/migration/V2__seed_demo_data.sql
```

3. Các thay đổi sau này phải tạo migration mới, không sửa migration đã chạy ở môi trường dùng chung.
4. Không đặt password/hash demo production trong migration production.

### Kiểm tra sau khi sửa

- Chạy trên PostgreSQL database trống.
- Kiểm tra Flyway tạo đủ bảng.
- Chạy lại ứng dụng trên database đã có migration.
- Đảm bảo migration không chạy seed trùng.

## 3. Test context hiện tại không có database test

**Mức độ:** High

Test `contextLoads()` yêu cầu Spring khởi tạo JPA nhưng chưa có:

- PostgreSQL test database.
- Testcontainers hoặc database test riêng.
- Profile test.

### Cần sửa

Chọn một phương án:

1. Dùng Testcontainers PostgreSQL cho integration test.
2. Dùng PostgreSQL local riêng cho test.
3. Chỉ dùng H2 nếu đã xác nhận H2 tương thích với PostgreSQL schema, nhưng đây không phải lựa chọn ưu tiên.

Phương án khuyến nghị là Testcontainers PostgreSQL để test đúng constraint, kiểu dữ liệu, identity và migration của PostgreSQL.

---

# II. Lỗi dữ liệu seed chắc chắn

## 4. Invoice `INVO000002` sai account

**Mức độ:** High

Customer `CUST000003` - Lê Thị Mua thuộc:

```sql
account_id = 'ACCT000001'
```

Nhưng invoice `INVO000002` lại dùng:

```sql
account_id = 'ACCT000002'
```

### Cần sửa

Nếu invoice là của Lê Thị Mua, đổi thành:

```sql
'ACCT000001'
```

Sau khi sửa cần kiểm tra các bản ghi snapshot:

- `customer_name`
- `customer_phone`
- `customer_email`
- `account_id`

phải cùng đại diện cho một khách hàng.

## 5. `TOUR000002` không có departure

**Mức độ:** High

Tour Nha Trang có trạng thái `OPEN` và còn 40 chỗ trong bảng `tours`, nhưng không có bản ghi trong `tour_departures`.

Mô hình booking mới yêu cầu booking trỏ vào `departure_id`, không trỏ trực tiếp vào `tour_id`.

### Cần sửa

Chọn một trong hai:

1. Thêm departure cho `TOUR000002`, gồm:
   - Ngày bắt đầu/kết thúc.
   - Sức chứa.
   - Giá.
   - Điểm tập trung.
   - Trạng thái.
2. Nếu tour chưa được mở bán theo mô hình mới, đổi trạng thái phù hợp và không hiển thị cho booking.

Không nên để tour `OPEN` nhưng không có departure hợp lệ.

## 6. Promotion demo đã hết hạn

**Mức độ:** Medium

`PROM000003` có thời gian:

```text
2025-11-01 đến 2025-12-31
```

Trong khi thời điểm rà soát là tháng 10/2026. Promotion này đã hết hạn nhưng vẫn liên kết với `TOUR000001`.

### Cần sửa

Nếu là dữ liệu test:

- Ghi rõ đây là promotion test hết hạn.
- Không cho logic khuyến mãi áp dụng promotion ngoài thời gian.

Nếu là dữ liệu demo đang hiển thị:

- Đổi ngày cho phù hợp.
- Hoặc bỏ liên kết khỏi tour.

## 7. Seed booking và departure chưa có quy tắc kiểm tra chéo

Departure có:

```text
departure_booked_slots = 2
```

Booking mẫu có:

```text
booking_guest_count = 2
```

Hai giá trị hiện khớp, nhưng database không tự đảm bảo điều này về sau.

### Cần sửa

Viết validation/integration test để kiểm tra:

```text
departure_booked_slots
= tổng guest_count của các booking còn giữ chỗ
```

Phải định nghĩa rõ booking status nào được tính vào số chỗ:

- `PENDING` có giữ chỗ không?
- `PAID` có giữ chỗ không?
- `CONFIRMED` có giữ chỗ không?
- `CANCELLED` có giải phóng chỗ không?

---

# III. Lỗi thiết kế dữ liệu cần quyết định

## 8. Trùng nguồn dữ liệu giá, ngày và số chỗ giữa `tours` và `tour_departures`

**Mức độ:** High

`tours` đang lưu:

```text
tour_price
tour_start_date
tour_end_date
tour_meeting_point
tour_remaining_slots
```

`tour_departures` cũng lưu:

```text
departure_price
departure_start_date
departure_end_date
departure_meeting_point
departure_capacity
departure_booked_slots
```

Điều này tạo ra hai nguồn dữ liệu cho cùng một nghiệp vụ.

### Cần quyết định

Khuyến nghị:

- `tours` chỉ lưu thông tin sản phẩm dùng chung:
  - Tên.
  - Mô tả.
  - Trạng thái tổng quát.
  - Accommodation.
  - Guide.
- `tour_departures` lưu toàn bộ thông tin theo từng lịch:
  - Giá.
  - Ngày đi.
  - Điểm tập trung.
  - Sức chứa.
  - Số chỗ đã giữ.
  - Trạng thái lịch.

Sau khi code đã đọc dữ liệu từ `tour_departures`, tạo migration để bỏ các cột dư thừa khỏi `tours`:

```sql
ALTER TABLE tours
    DROP COLUMN tour_remaining_slots,
    DROP COLUMN tour_start_date,
    DROP COLUMN tour_end_date,
    DROP COLUMN tour_price,
    DROP COLUMN tour_meeting_point;
```

Không thực hiện lệnh này trước khi toàn bộ code và API đã chuyển sang departure.

## 9. Xác định rõ ý nghĩa `departure_capacity`

**Mức độ:** High

Seed cũ của `TOUR000001` có:

```text
tours.tour_remaining_slots = 28
tour_departures.departure_capacity = 30
tour_departures.departure_booked_slots = 2
```

Trong schema migration comment, `tour_remaining_slots` được đưa vào `departure_capacity`, nhưng tên `remaining_slots` có thể khiến người đọc hiểu đó là 28 thay vì sức chứa tổng 30.

### Cần sửa

- Thống nhất `departure_capacity` là tổng sức chứa.
- `departure_booked_slots` là số chỗ đã giữ.
- Số chỗ còn lại luôn tính:

```text
departure_capacity - departure_booked_slots
```

- Không dùng `tour_remaining_slots` làm nguồn sự thật sau migration.

---

# IV. Ràng buộc database còn thiếu

## 10. Ràng buộc sức chứa chưa đầy đủ

**Mức độ:** High

Hiện chỉ có:

```sql
CHECK (departure_booked_slots <= departure_capacity)
```

Nhưng vẫn cho phép số âm.

### Cần thêm

```sql
CHECK (departure_capacity > 0),
CHECK (departure_booked_slots >= 0),
CHECK (departure_booked_slots <= departure_capacity)
```

## 11. Thiếu ràng buộc số khách booking

**Mức độ:** High

`booking_guest_count` có default 1 nhưng không ngăn giá trị 0 hoặc âm.

### Cần thêm

```sql
CHECK (booking_guest_count > 0)
```

## 12. Thiếu ràng buộc số tiền không âm

**Mức độ:** High

Các cột tiền quan trọng cần kiểm tra:

- `accommodations.price_per_night`
- `travel_vehicles.rental_price_per_day`
- `tourist_destinations.entry_fee`
- `promotions.discount_percentage`
- `tours.tour_price`
- `tour_departures.departure_price`
- `bookings.booking_unit_price`
- `bookings.booking_subtotal`
- `bookings.booking_discount_amount`
- `bookings.booking_total_amount`
- `payments.payment_amount`
- `refunds.refund_amount`
- `invoices.discount_amount`
- `invoices.tax_amount`
- `invoices.service_fee`
- `invoices.total_amount`

### Cần thêm

Tối thiểu:

```sql
CHECK (departure_price >= 0)
CHECK (booking_unit_price >= 0)
CHECK (booking_subtotal >= 0)
CHECK (booking_discount_amount >= 0)
CHECK (booking_total_amount >= 0)
CHECK (payment_amount > 0)
CHECK (refund_amount > 0)
CHECK (discount_amount >= 0)
CHECK (tax_amount >= 0)
CHECK (service_fee >= 0)
CHECK (total_amount >= 0)
```

Với `discount_percentage`, nên giới hạn:

```sql
CHECK (discount_percentage >= 0 AND discount_percentage <= 100)
```

## 13. Thiếu ràng buộc ngày tháng

**Mức độ:** Medium

Các bảng có khoảng thời gian nhưng chưa kiểm tra thứ tự ngày:

- `tours`
- `tour_departures`
- `promotions`

### Cần thêm

```sql
CHECK (departure_end_date > departure_start_date)
CHECK (end_date >= start_date)
```

Với `tours`, chỉ thêm check nếu các cột ngày vẫn còn được giữ.

## 14. Status và enum đang là VARCHAR tự do

**Mức độ:** Medium

Các nhóm giá trị đang chỉ được ghi trong comment:

- `tour_status`: `DRAFT`, `OPEN`, `CLOSED`, `CANCELLED`
- `departure_status`: `OPEN`, `FULL`, `CLOSED`, `CANCELLED`
- `booking_status`: `PENDING`, `PAID`, `CONFIRMED`, `COMPLETED`, `CANCELLED`
- `payment_status`: `PENDING`, `SUCCESS`, `FAILED`
- `refund_status`: `REQUESTED`, `APPROVED`, `REJECTED`, `DONE`
- `invoice.status`: `PAID`, `UNPAID`
- `payment_method`: `CASH`, `VNPAY`, `MOMO`, `CARD`
- `traveler_gender`: `MALE`, `FEMALE`, `OTHER`
- `accommodation_type`: `HOTEL`, `RESORT`, `HOMESTAY`

### Cần sửa

Chọn một phương án:

1. PostgreSQL enum.
2. `CHECK` constraint.
3. Bảng danh mục riêng.

Không nên để backend là nơi duy nhất kiểm soát các giá trị này.

## 15. Thiếu kiểm tra công thức tiền booking

**Mức độ:** High

Database chưa đảm bảo quan hệ giữa:

```text
booking_unit_price
booking_guest_count
booking_subtotal
booking_discount_amount
booking_total_amount
```

### Cần sửa

- Backend phải tự lấy giá từ departure.
- Không tin giá và total do client gửi.
- Tính lại subtotal, discount và total ở server.
- Thực hiện trong transaction.
- Thêm integration test cho giá, giảm giá và tổng tiền.

Có thể thêm check cơ bản ở database, nhưng không nên phụ thuộc hoàn toàn vào `CHECK` cho logic khuyến mãi phức tạp.

---

# V. Lỗi integrity của booking, payment và refund

## 16. Có thể oversell khi nhiều người đặt cùng lúc

**Mức độ:** Critical

`departure_booked_slots` là dữ liệu được lưu dư thừa và database không có cơ chế đồng bộ tự động với booking.

Hai request đồng thời có thể cùng đọc số chỗ còn lại rồi cùng ghi booking, dẫn đến vượt sức chứa.

### Cần sửa

Quy trình đặt chỗ phải:

1. Mở transaction.
2. Lock departure bằng `SELECT ... FOR UPDATE`.
3. Kiểm tra status departure.
4. Kiểm tra số chỗ còn lại.
5. Tính lại tiền từ departure.
6. Tạo booking.
7. Tăng `departure_booked_slots`.
8. Commit.

Khi hủy booking:

1. Lock departure.
2. Kiểm tra booking chưa được giải phóng chỗ.
3. Giảm `departure_booked_slots`.
4. Đổi status booking.
5. Commit.

Phải tránh việc một booking bị trừ/hoàn chỗ nhiều lần.

## 17. `refunds.booking_id` có thể không khớp với payment

**Mức độ:** Critical

`refunds` có cả:

```text
payment_id
booking_id
```

Nhưng chỉ có hai foreign key độc lập. Database không đảm bảo payment và booking thuộc cùng một booking.

### Cần sửa

Khuyến nghị bỏ `refunds.booking_id`, vì có thể truy ngược qua `payments.booking_id`.

Nếu cần giữ để tối ưu truy vấn:

1. Thêm unique key trên `payments(payment_id, booking_id)`.
2. Dùng composite foreign key từ `refunds(payment_id, booking_id)`.
3. Thêm validation ở service.

## 18. Không giới hạn tổng tiền refund

**Mức độ:** High

Schema cho phép tổng refund của một payment vượt quá số tiền payment thành công.

### Cần sửa

Khi tạo hoặc duyệt refund:

```text
tổng refund đã DONE/APPROVED
<=
tổng payment SUCCESS có thể hoàn
```

Thao tác phải chạy trong transaction và lock các payment/refund liên quan để tránh race condition.

## 19. Thiếu unique transaction id của payment gateway

**Mức độ:** High

`payment_provider_txn_id` hiện chưa unique. Callback gateway lặp lại có thể tạo payment thành công trùng.

### Cần thêm

Nếu mã giao dịch unique toàn hệ thống:

```sql
CREATE UNIQUE INDEX uq_payment_provider_txn
ON payments (payment_provider_txn_id)
WHERE payment_provider_txn_id IS NOT NULL;
```

Nếu mã chỉ unique trong từng provider, cần lưu thêm provider riêng và tạo unique key phù hợp.

## 20. Chưa ràng buộc `payment_paid_at` theo status

**Mức độ:** Medium

Các trạng thái sau đang có thể tồn tại:

```text
SUCCESS nhưng payment_paid_at NULL
PENDING nhưng payment_paid_at khác NULL
FAILED nhưng payment_paid_at khác NULL
```

### Cần sửa

Định nghĩa rõ quy tắc:

- `SUCCESS` phải có `payment_paid_at`.
- `PENDING` và `FAILED` phải có `payment_paid_at = NULL`.

Có thể enforce bằng `CHECK` hoặc service tùy quy trình gateway.

## 21. Thiếu kiểm tra payment method và provider transaction

**Mức độ:** Medium

Ví dụ:

- `CASH` có thể không cần provider transaction.
- `VNPAY`, `MOMO`, `CARD` cần provider transaction.

### Cần sửa

Định nghĩa constraint hoặc validation tương ứng, tránh payment online không có mã giao dịch.

---

# VI. Lỗi đồng bộ entity và database

## 22. `Customer.account` đang dùng `ManyToOne` nhưng database là một-một

**Mức độ:** High

Database có:

```sql
customers.account_id VARCHAR(10) NULL UNIQUE
```

Điều này thể hiện một account chỉ có tối đa một customer.

Nhưng entity [Customer.java](tour/src/main/java/com/j2ee/tour/model/Customer.java) đang dùng:

```java
@ManyToOne
private Account account;
```

### Cần sửa

Nếu nghiệp vụ là một account tương ứng một customer:

```java
@OneToOne
@JoinColumn(name = "account_id")
private Account account;
```

Nếu nghiệp vụ cho phép một account có nhiều customer thì bỏ `UNIQUE` trong database. Cần quyết định một mô hình duy nhất.

## 23. Entity chưa mô tả đầy đủ nullable và fetch/cascade

**Mức độ:** Medium

Các quan hệ bắt buộc trong database chưa được mô tả rõ trong JPA, ví dụ:

- `Booking.departure`
- `Payment.booking`
- `Traveler.booking`
- `TourDeparture.tour`
- `Account.role`

### Cần sửa

Xác định rõ:

- `optional = false`.
- `nullable = false`.
- `fetch`.
- `cascade`.
- `orphanRemoval` nếu cần.

Không thêm cascade bừa bãi vì có thể xóa lan dữ liệu booking/payment.

## 24. Entity đang giữ các cột legacy của `tours`

Entity [Tour.java](tour/src/main/java/com/j2ee/tour/model/Tour.java) vẫn có:

- `tourPrice`
- `tourStartDate`
- `tourEndDate`
- `tourMeetingPoint`
- `tourRemainingSlots`

### Cần sửa

Sau khi hoàn tất migration sang departure:

- Xóa các field legacy khỏi entity.
- Xóa cột legacy khỏi database.
- Cập nhật DTO, repository, service và API.
- Cập nhật test.

Không xóa khi code vẫn còn đọc các field này.

---

# VII. Vấn đề triển khai và vận hành database

## 25. File SQL không idempotent

**Mức độ:** Medium

Chạy lại file SQL sẽ lỗi vì:

- Bảng đã tồn tại.
- Function đã tồn tại.
- Trigger đã tồn tại.
- Primary key đã tồn tại.
- Unique key đã tồn tại.

### Cần sửa

Không dùng file seed tổng hợp làm cơ chế migration chính. Chuyển sang Flyway.

Nếu vẫn cần script local:

- Tách `schema.sql`.
- Tách `seed.sql`.
- Ghi rõ script chỉ chạy trên database trống, hoặc viết script reset riêng có phạm vi rõ ràng.

Không dùng lệnh xóa dữ liệu rộng trong môi trường production.

## 26. Trigger cập nhật account chưa có migration quản lý

Function và trigger:

```text
set_account_updated_at()
trg_accounts_updated_at
```

đang nằm trong file SQL thủ công.

### Cần sửa

Đưa function và trigger vào migration có version. Thêm test để xác nhận `account_updated_at` thay đổi khi update account.

## 27. Thiếu index cho một số truy vấn quan trọng

**Mức độ:** Low/Medium

Đã có nhiều index cơ bản, nhưng cần rà soát thêm theo API thực tế:

- Booking theo `booking_code`.
- Booking theo account và status.
- Departure theo tour, status và ngày.
- Payment theo booking, status và transaction id.
- Refund theo payment và booking.
- Customer theo account.

`booking_code` đã có unique nên PostgreSQL tự tạo index. `payment_provider_txn_id` cần unique/partial index như phần trên.

Chỉ thêm index sau khi biết truy vấn thực tế hoặc có execution plan, tránh index dư thừa.

---

# VIII. Phần backend/frontend chưa hoàn thiện nhưng ảnh hưởng database

## 28. Backend chưa có repository, service và controller nghiệp vụ

**Mức độ:** High

Hiện các thư mục repository/controller/service phần lớn là placeholder. [tourService.java](tour/src/main/java/com/j2ee/tour/service/tourService.java) chưa có logic.

### Cần làm

- Repository cho tour/departure/booking/payment/refund.
- Service transaction cho booking.
- API lấy tour và departure.
- API tạo/hủy booking.
- API thanh toán và callback idempotent.
- API refund có kiểm tra giới hạn.
- DTO không expose entity trực tiếp.

## 29. Frontend chưa sử dụng database/backend

**Mức độ:** Medium

Frontend [page.tsx](fe-tour/app/page.tsx) hiện vẫn là trang mặc định Next.js. Chưa có API client, tour listing hoặc booking flow.

### Cần làm

- API base URL qua environment variable.
- Màn hình danh sách tour.
- Hiển thị departure, giá và số chỗ còn lại.
- Không hiển thị giá/số chỗ legacy từ `tours` khi đã chuyển sang departure.
- Xử lý trạng thái booking/payment rõ ràng.

---

# IX. Thứ tự triển khai khuyến nghị

## Phase 1 - Làm project khởi động được

- [ ] Thêm datasource config bằng environment variables.
- [ ] Chọn PostgreSQL local hoặc Testcontainers cho test.
- [ ] Chuyển schema vào Flyway migration.
- [ ] Chạy migration trên database trống.
- [ ] Làm cho `contextLoads()` pass.

## Phase 2 - Sửa dữ liệu seed

- [ ] Sửa account của `INVO000002`.
- [ ] Thêm departure cho `TOUR000002` hoặc đóng tour.
- [ ] Quyết định xử lý `PROM000003` hết hạn.
- [ ] Viết test kiểm tra foreign key và consistency của seed.

## Phase 3 - Chốt mô hình tour/departure

- [ ] Xác nhận departure là nguồn giá/ngày/sức chứa duy nhất.
- [ ] Chuyển toàn bộ code sang departure.
- [ ] Xóa field legacy khỏi entity.
- [ ] Tạo migration xóa cột legacy khỏi `tours`.

## Phase 4 - Bổ sung database constraints

- [ ] Constraint sức chứa.
- [ ] Constraint số khách.
- [ ] Constraint tiền không âm.
- [ ] Constraint phần trăm giảm giá.
- [ ] Constraint ngày.
- [ ] Constraint status/method.
- [ ] Unique payment gateway transaction.
- [ ] Constraint hoặc thiết kế lại refund booking/payment.

## Phase 5 - Xây booking transaction

- [ ] Lock departure khi đặt chỗ.
- [ ] Tính tiền server-side.
- [ ] Tăng/giảm booked slots trong cùng transaction.
- [ ] Chống hủy booking hai lần.
- [ ] Xác định status nào giữ chỗ.
- [ ] Test concurrent booking.

## Phase 6 - Đồng bộ JPA

- [ ] Sửa `Customer.account` thành quan hệ đúng.
- [ ] Khai báo nullable/optional chính xác.
- [ ] Rà soát cascade và orphan removal.
- [ ] Kiểm tra toàn bộ tên cột sau khi bật schema validation.

## Phase 7 - Payment/refund

- [ ] Callback payment idempotent.
- [ ] Chặn duplicate provider transaction.
- [ ] Kiểm tra status và paid timestamp.
- [ ] Giới hạn tổng refund.
- [ ] Test payment retry, callback lặp và refund partial.

---

# X. Tiêu chí hoàn thành

Database/project chỉ được xem là ổn khi:

- [ ] App khởi động được với PostgreSQL.
- [ ] Flyway chạy được trên database trống.
- [ ] Test context pass.
- [ ] Seed không có account/customer/invoice mâu thuẫn.
- [ ] Mọi tour mở bán đều có ít nhất một departure hợp lệ.
- [ ] Giá và lịch booking chỉ lấy từ departure.
- [ ] Không thể insert số chỗ, số khách hoặc số tiền âm.
- [ ] Không thể tạo booking vượt sức chứa trong concurrent requests.
- [ ] Không thể refund sai booking hoặc vượt số tiền được hoàn.
- [ ] Payment callback lặp không tạo giao dịch thành công trùng.
- [ ] Entity mapping khớp với database, đặc biệt là `Customer.account`.
- [ ] Migration được version hóa và có thể triển khai lặp lại an toàn.
- [ ] Test integration chạy trên PostgreSQL thật hoặc Testcontainers.
