# Audit fetch/invoice — Controller → Service → Repository → DTO Mapper

> Ngày rà soát: 2026-11-22  
> Phạm vi: `tour/src/main/java/com/j2ee/tour/` (controller, service, repository, model, DTO), `application.properties`, `V2__create_schema.sql`, `V7__add_read_indexes.sql`.  
> Phương pháp: **chỉ đọc code, không sửa code**. Không chạy `EXPLAIN ANALYZE`, không bật SQL logging — mọi kết luận về thời gian là suy luận cấu trúc, chưa phải số đo.

---

## 1. Tóm tắt nhanh

| Câu hỏi | Kết luận |
|---|---|
| N+1 query | **Có — trên `promotions`** ở cả `list()` lẫn `listMine()` (~N query phụ cho mỗi trang) |
| Lazy loading | **Có, và kích hoạt đúng lúc mapping DTO**; chạy được chỉ vì nằm trong `@Transactional(readOnly=true)` của service |
| Phân trang ở DB hay in-memory? | **Ở DB** (`LIMIT/OFFSET` qua Spring Data) — đúng |
| Gọi repository trong vòng lặp mapping? | **Không** — đúng |
| Đo thời gian DB vs mapping? | **Không có instrumentation nào** → chưa có số liệu để kết luận |
| Hướng tối ưu phù hợp | **Batch fetching** khớp pagination nhất. **Fetch join collection thì KHÔNG dùng** cho 2 endpoint phân trang. DTO projection tốt nhất nhưng cần tách query riêng cho `promotionIds` |

---

## 2. Sơ đồ luồng

```
InvoiceController.list(Pageable)            → InvoiceService.list()           → invoiceRepository.findAll(pageable)          → PageResponse.from(..., InvoiceResponse::from)
InvoiceController.mine(Pageable, principal) → InvoiceService.listMine()       → findByBookingAccountAccountId(id, pageable) → PageResponse.from(..., InvoiceResponse::from)
InvoiceController.get(id)                   → InvoiceService.get()           → findById(id)                              → InvoiceResponse.from(...)
InvoiceController.getByBooking(bookingId)   → InvoiceService.getByBooking()  → findByBookingBookingId(...)               → InvoiceResponse.from(...)
```

---

## 3. N+1 queries và Lazy loading — có vấn đề

**Nguyên nhân:** `model/Invoice.java:78-82`

```java
@ManyToMany
@JoinTable(name = "invoice_promotions", ...)
private List<Promotion> promotions = new ArrayList<>();
```

`@ManyToMany` mặc định là **LAZY**. `dto/response/InvoiceResponse.java:25-44` lại luôn chạm vào collection này:

```java
invoice.getPromotions().stream().map(Promotion::getPromotionId).toList();   // dòng 26-28
```

Hệ quả:

- Với `list()` / `listMine()`: mỗi invoice trong trang → **1 SELECT riêng** để load `invoice_promotions` + `promotions`. Trang mặc định 20 item → **~20 query phụ** + 2 query chính (count + data) ≈ **22 query/request**.
- Với `get()` / `getByBooking()`: 1 query chính + 1 query lazy → **2 query**, không phải N+1 nhưng vẫn là query thừa.
- `spring.jpa.open-in-view=false` (`application.properties:18`) nghĩa là lazy load **chỉ không lỗi vì** mapping nằm trong `@Transactional(readOnly=true)` của service (`InvoiceService.java:43, 48, 113, 118`). Nếu sau này map ở controller → `LazyInitializationException` ngay.

**Về `booking`:** `model/Invoice.java:73-76` là `@OneToOne` **EAGER** (mặc định). Hibernate thường join-fetch to-one EAGER nên **không tạo N+1**, nhưng load toàn bộ entity `Booking` trong khi DTO chỉ cần `bookingId` (`InvoiceResponse.java:31`) → tốn cột không cần thiết. Cần verify bằng SQL log để chắc chắn không có secondary select.

**Các điểm sạch sẽ (không có N+1):**

- `InvoiceService.resolvePromotions()` (`InvoiceService.java:160-169`) dùng `findAllById(ids)` → đã batch, đúng.
- `InvoiceService.createForPaidBooking()` (`InvoiceService.java:90-108`) chạm `getTravelers()`, `getAccount()`, `getPromotion()` nhưng chỉ trên **1 booking**, không phải vòng lặp → không phải N+1 (dù có thể là vài lazy select riêng lẻ).

---

## 4. Phân trang — đúng ở database

- `InvoiceService.java:45` → `findAll(pageable)`; `InvoiceService.java:55-56` → `findByBookingAccountAccountId(accountId, effective)`.
- Cả hai là derived query của Spring Data → Hibernate sinh `LIMIT ? OFFSET ?` **trong SQL**. Không có `list().subList()` hay `Stream.skip().limit()` nào.

Hai điểm cần lưu ý:

1. **`list()` không có sort mặc định** (`InvoiceService.java:43-46`; controller `InvoiceController.java:32-35` chỉ truyền `Pageable`). Không có `ORDER BY` → thứ tự giữa các trang không ổn định (có thể trùng/sót bản ghi khi chuyển trang). `listMine()` thì đã có default sort tốt ở `InvoiceService.java:50-54` (`invoiceCreatedAt DESC, invoiceId DESC`).
2. **Sort của `listMine()` không khớp index có sẵn.** Index hiện có nằm ở bảng `bookings`: `V7__add_read_indexes.sql:21-23` → `idx_booking_account_created ON bookings (account_id, booking_created_at DESC, booking_id DESC)`. Trong khi `listMine` sort theo cột của `invoices`. Các index khác đã có: `invoices.booking_id` UNIQUE (`V2__create_schema.sql:132`), `invoice_promotions` PK `(invoice_id, promotion_id)` (`V2__create_schema.sql:145`). → **Không có index trên `invoices(invoice_created_at, invoice_id)`**. Với bảng lớn, Postgres phải sort + offset. Cần `EXPLAIN ANALYZE` trước khi thêm index — đừng thêm index chỉ vì đoán.

---

## 5. Repository trong vòng lặp mapping — không có

- `InvoiceResponse.from()` (`InvoiceResponse.java:25-44`) **chỉ gọi getter** trên entity đã load. Không gọi repository, không gọi service.
- `common/PageResponse.from(page, mapper)` (`PageResponse.java:33-44`) chỉ `stream().map(mapper).toList()` — mapper thuần túy.
- → Không có anti-pattern "query trong loop" ở tầng mapping.

---

## 6. Thời gian DB vs thời gian mapping — chưa đo được

Không có instrumentation nào trong path này:

- `spring.jpa.show-sql=false` (`application.properties:17`)
- Không có Hibernate statistics, không có p6spy/datasource-proxy, không có APM, không có timing tương đương `console.time`.

**Ước lượng theo cấu trúc (chưa phải số đo):** mapping chỉ là stream qua ~20-50 record + vài getter → micro-giây. DB phải chạy count + data + ~N lazy select → **latency gần như hoàn toàn do DB**, đặc biệt là phần N+1. Theo nguyên tắc "measure before optimizing", đây vẫn là giả thuyết cho đến khi bật SQL timing/APM và đo thực tế.

---

## 7. Đánh giá 3 hướng tối ưu (cho pagination)

| Hướng | Đánh giá |
|---|---|
| **DTO projection** | **Tốt nhất.** Repository trả thẳng interface/class projection gồm `invoiceId, booking.bookingId, invoiceCreatedAt, status, amounts, customer..., paymentMethod`. Loại được EAGER `Booking` và không cần load entity. Riêng `promotionIds` không aggregate gọn trong projection → tách 1 query batch `select i.invoiceId, p.promotionId from Invoice i join i.promotions p where i.invoiceId in :ids` rồi group lại trong service (1 query cho cả trang). |
| **Batch fetching** | **Khớp pagination nhất, ít xáo trộn nhất.** Thêm `hibernate.default_batch_fetch_size` (hoặc `@BatchSize` trên `promotions`) → N lazy select gom thành ~N/batch (ví dụ 20 item → 1-2 query). Page size có giới hạn nên batch hoạt động hiệu quả. Không đụng đến SQL phân trang. |
| **Fetch join** | **Chỉ dùng cho `get()` / `getByBooking()`; KHÔNG dùng cho `list()` / `listMine()`.** `join fetch i.promotions` trên Pageable khiến Hibernate cảnh báo `HHH000104` và **tự động phân trang in-memory** (load toàn bộ bảng rồi cắt) — phản lại mục tiêu. Với to-one `booking` thì join fetch ổn. |
| **Thêm index** | Cần `EXPLAIN ANALYZE` trước. `invoices(created_at DESC, invoice_id DESC)` có thể giúp sort của `listMine`, nhưng do filter nằm ở `bookings.account_id` nên hiệu quả phụ thuộc join plan — đoán là không đủ. |

---

## 8. Kiến nghị (theo thứ tự ưu tiên, không tự sửa)

1. **Đo trước:** bật Hibernate statistics / datasource-proxy và log SQL trong 1 request `GET /api/invoices?size=20` để đếm query và xác nhận secondary select của `booking`.
2. **Ưu tiên sửa N+1 promotions** — chọn một trong hai:
   - Nhanh / ít rủi ro: batch fetching (`default_batch_fetch_size` hoặc `@BatchSize`), **hoặc**
   - Triệt để: DTO projection + 1 query batch lấy `promotionIds` theo danh sách invoiceId.
3. **Thêm default sort cho `list()`** để phân trang ổn định (đồng bộ với `listMine`).
4. **Chạy `EXPLAIN ANALYZE`** cho query của `listMine()` trước khi thêm index.
5. **Giữ nguyên** việc không gọi repository trong mapper — đây là điểm tốt cần duy trì.
6. Nếu dùng fetch join, **chỉ** áp dụng cho `findById` / `findByBookingBookingId`, không áp dụng cho method nhận `Pageable`.

---

## 9. Trạng thái tài liệu

- Đây là audit tĩnh, không phải ADR: ghi nhận hiện trạng và các lựa chọn đã bị loại, chưa có quyết định được phê duyệt.
- Nếu sau này chọn một hướng tối ưu cụ thể, nên viết ADR riêng (ví dụ `docs/decisions/ADR-xxx-invoice-fetch-strategy.md`) ghi rõ lý do và số liệu before/after.
- Không file code nào bị thay đổi trong lần rà soát này.
