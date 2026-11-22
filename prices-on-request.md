# "Price on request" và "Dates to be announced" — khi nào xuất hiện?

## Câu trả lời ngắn gọn

Cả hai trạng thái đều bắt nguồn từ **cùng một điều kiện**: tour **không có chuyến khởi hành nào trong tương lai** (trạng thái `OPEN` hoặc `FULL`, ngày khởi hành > hiện tại). Khi đó backend trả `null` cho cả `priceFrom` và `nextDepartureDate`, và frontend dịch các giá trị `null` đó thành hai chuỗi tương ứng.

| Trạng thái | Trường dữ liệu | Giá trị kích hoạt |
|---|---|---|
| `Price on request` | `tour.priceFrom` | `null` / `undefined` |
| `Dates to be announced` | `tour.nextDepartureDate` | `null` / không khớp `YYYY-MM-DD` |

---

## 1. "Price on request"

Nguồn: `fe-tour/lib/format.ts:79-84`

```ts
export function formatPrice(value: number | null | undefined): string {
  if (value == null) return "Price on request";
  return `₫${new Intl.NumberFormat("en-US", {
    maximumFractionDigits: 0,
  }).format(value)}`;
}
```

→ Chuỗi này hiện khi `tour.priceFrom == null`.

`priceFrom` do backend tính trong `TourDepartureRepository.java:20-33`:

```sql
min(d.departurePrice) as priceFrom
...
where d.tour.tourId in :tourIds
  and d.departureStatus in ('OPEN', 'FULL')
  and d.departureStartDate > :now
```

Hàm tổng hợp `min()` trả `null` nếu **không có dòng nào thỏa điều kiện** — tức tour không có chuyến `OPEN`/`FULL` nào trong tương lai (toàn bộ chuyến đã qua ngày hoặc bị `CANCELLED`). Không có chuyến → không có giá để lấy → "Price on request".

## 2. "Dates to be announced"

Nguồn: `fe-tour/lib/format.ts:30-44`

```ts
export function formatDate(value: string | null | undefined): string {
  const date = parts(value);
  if (!date) return "Dates to be announced"; // null hoặc không khớp YYYY-MM-DD
  return `${date.day} ${MONTHS[date.month - 1]} ${date.year}`;
}

export function formatDateRange(start, end): string {
  const from = parts(start);
  const to = parts(end);
  if (!from) return "Dates to be announced";
  if (!to)   return formatDate(start);
  ...
}
```

`nextDepartureDate` cũng lấy từ cùng một query: `min(d.departureStartDate)` với cùng bộ lọc (`OPEN`/`FULL` + ngày trong tương lai). Cùng lý do — không có chuyến tương lai → `null` → "Dates to be announced".

Lưu ý nhỏ: `formatDateRange` trả "Dates to be announced" khi **`start` null**; nếu chỉ `end` null thì nó fallback sang `formatDate(start)` (vẫn hiện ngày bắt đầu).

## 3. Cách hai trạng thái thể hiện trên card tour

Nguồn: `fe-tour/components/tour-card.tsx:8-11`

```tsx
const noDates = tour.departureCount === 0;
const soldOut = !noDates && tour.seatsAvailable <= 0;
const status = noDates ? "New dates soon" : soldOut ? "Sold out" : seatsLabel(tour.seatsAvailable);
```

Khi `departureCount === 0` (cùng điều kiện làm `priceFrom` và `nextDepartureDate` null):

- Badge góc trái: **"New dates soon"**
- Không hiển thị badge số lượng date ở góc phải (chỉ hiện khi `departureCount > 0`)
- Dòng "Next departure" → `formatDate(null)` → **"Dates to be announced"**
- Dòng "From" → `formatPrice(null)` → **"Price on request"**

---

## Bằng chứng trong repo

- `tour/src/test/java/com/j2ee/tour/service/TourServiceTest.java:144-147`: khi không có departure tương lai → `priceFrom() == null`, `nextDepartureDate() == null`, `seatsAvailable() == 0`, `departureCount() == 0`.
- `fe-tour/lib/format.test.ts:18,60`: test khẳng định `formatDate(null)` = `"Dates to be announced"` và `formatPrice(undefined)` = `"Price on request"`.
- `docs/progress.md:169-171`: seed hiện tại có toàn bộ departure trong quá khứ nên card hiện new-dates/price-on-request, trang chi tiết hiện "No dates open just yet."

---

## Trường hợp biên (edge cases)

- **Có chuyến tương lai nhưng đã hết chỗ (`FULL`)**: `departureCount > 0`, `seatsAvailable = 0` → card hiện **"Sold out"**, vẫn có ngày và giá. Hai trạng thái bạn hỏi **không** xuất hiện.
- **Có chuyến tương lai nhưng `departurePrice` bị để `null` trong DB**: `min()` vẫn trả `null` → hiện "Price on request" dù ngày vẫn hiển thị bình thường. Đây là trường hợp dữ liệu bất thường, không phải luồng thiết kế.
- **Chuỗi ngày sai định dạng**: `formatDate` dùng regex `^(\d{4})-(\d{2})-(\d{2})`; nếu input không khớp (ví dụ giá trị API trả về dạng khác), hàm cũng trả "Dates to be announced".

**Tóm lại:** "Price on request" và "Dates to be announced" là hai cách hiển thị của cùng một thực tế — tour chưa có lịch khởi hành nào trong tương lai — áp dụng lần lượt cho trường giá và trường ngày.

---

## Tham chiếu file liên quan

- `fe-tour/lib/format.ts` — hàm định dạng giá, ngày, số ghế
- `fe-tour/lib/format.test.ts` — test các hàm định dạng
- `fe-tour/components/tour-card.tsx` — card tour, quyết định badge "New dates soon" / "Sold out"
- `fe-tour/lib/types.ts` — kiểu `TourSummary` (`priceFrom`, `nextDepartureDate`, `seatsAvailable`, `departureCount`)
- `tour/src/main/java/com/j2ee/tour/repository/TourDepartureRepository.java` — query thống kê `priceFrom` / `nextDepartureDate`
- `tour/src/main/java/com/j2ee/tour/dto/response/TourSummaryResponse.java` — DTO response
