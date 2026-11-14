-- Dev seed data: ~50 rows per table. Idempotent-ish via WHERE NOT EXISTS guards.
-- Run once with psql; not a Flyway migration.

BEGIN;

-- accounts (50)
INSERT INTO accounts (username, password, account_email, email_verified, status, role_id, account_created_at)
SELECT
  'seeduser' || g,
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
  'seeduser' || g || '@example.com',
  1::smallint, 1::smallint,
  (SELECT account_role_id FROM account_roles WHERE role_name = 'ROLE_USER' LIMIT 1),
  NOW() - ((g % 60) || ' days')::interval
FROM generate_series(1, 50) AS g
WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.username = 'seeduser' || g);

-- accommodations (50)
INSERT INTO accommodations (accommodation_name, location, rating, price_per_night, accommodation_type)
SELECT
  'Seed Accommodation ' || g,
  'City ' || g,
  (g % 6)::numeric,
  (80 + g * 3)::numeric,
  (ARRAY['HOTEL', 'RESORT', 'HOMESTAY'])[1 + (g % 3)]
FROM generate_series(1, 50) AS g
WHERE NOT EXISTS (SELECT 1 FROM accommodations a WHERE a.accommodation_name = 'Seed Accommodation ' || g);

-- travel_vehicles (50)
INSERT INTO travel_vehicles (vehicle_type, capacity, rental_price_per_day)
SELECT
  (ARRAY['Bus', 'Van', 'SUV'])[1 + (g % 3)],
  8 + (g % 33),
  (60 + g * 2)::numeric
FROM generate_series(1, 50) AS g
WHERE NOT EXISTS (
  SELECT 1 FROM travel_vehicles v
  WHERE v.vehicle_type = (ARRAY['Bus', 'Van', 'SUV'])[1 + (g % 3)]
    AND v.capacity = 8 + (g % 33)
    AND v.rental_price_per_day = (60 + g * 2)::numeric
);

-- tour_guides (50)
INSERT INTO tour_guides (tour_guide_name, tour_guide_email, tour_guide_phone, tour_guide_experience_years)
SELECT
  'Seed Guide ' || g,
  'guide' || g || '@example.com',
  '09' || lpad(g::text, 8, '0'),
  g % 15
FROM generate_series(1, 50) AS g
WHERE NOT EXISTS (SELECT 1 FROM tour_guides tg WHERE tg.tour_guide_name = 'Seed Guide ' || g);

-- tourist_destinations (50)
INSERT INTO tourist_destinations (destination_name, location, description, entry_fee)
SELECT
  'Seed Destination ' || g,
  'Province ' || g,
  'Description for destination ' || g,
  (g * 2)::numeric
FROM generate_series(1, 50) AS g
WHERE NOT EXISTS (SELECT 1 FROM tourist_destinations td WHERE td.destination_name = 'Seed Destination ' || g);

-- promotions (50)
INSERT INTO promotions (promotion_name, discount_percentage, start_date, end_date, description)
SELECT
  'Seed Promo ' || g,
  (g % 31)::numeric,
  CURRENT_DATE - 10,
  CURRENT_DATE + 30,
  'Promo ' || g
FROM generate_series(1, 50) AS g
WHERE NOT EXISTS (SELECT 1 FROM promotions p WHERE p.promotion_name = 'Seed Promo ' || g);

-- tours (50)
INSERT INTO tours (tour_name, tour_description, tour_status, accommodation_id, tour_guide_id, promotion_id, tour_duration_days)
SELECT
  'Seed Tour ' || g,
  'Itinerary A|Itinerary B|Itinerary C',
  'OPEN',
  (SELECT accommodation_id FROM accommodations ORDER BY accommodation_id OFFSET ((g - 1) % (SELECT count(*) FROM accommodations)) LIMIT 1),
  (SELECT tour_guide_id FROM tour_guides ORDER BY tour_guide_id OFFSET ((g - 1) % (SELECT count(*) FROM tour_guides)) LIMIT 1),
  (SELECT promotion_id FROM promotions ORDER BY promotion_id OFFSET ((g - 1) % (SELECT count(*) FROM promotions)) LIMIT 1),
  3 + (g % 7)
FROM generate_series(1, 50) AS g
WHERE NOT EXISTS (SELECT 1 FROM tours t WHERE t.tour_name = 'Seed Tour ' || g);

-- tour_images (one per seeded tour)
INSERT INTO tour_images (image_url, tour_id)
SELECT
  (ARRAY[
    '/images/tour-hoi-an.jpg',
    '/images/destination-nha-trang.jpg',
    '/images/tour-lan-ha.jpg',
    '/images/tour-sa-pa.jpg',
    '/images/tour-ninh-binh.jpg',
    '/images/tour-hue.jpg'
  ])[1 + (t.tour_id % 6)],
  t.tour_id
FROM tours t
WHERE t.tour_name LIKE 'Seed Tour %'
  AND NOT EXISTS (SELECT 1 FROM tour_images ti WHERE ti.tour_id = t.tour_id);

-- tour_destinations (one per seeded tour)
INSERT INTO tour_destinations (tour_id, destination_id)
SELECT
  t.tour_id,
  (SELECT destination_id FROM tourist_destinations ORDER BY destination_id OFFSET ((t.tour_id - 1) % (SELECT count(*) FROM tourist_destinations)) LIMIT 1)
FROM tours t
WHERE t.tour_name LIKE 'Seed Tour %'
ON CONFLICT DO NOTHING;

-- tour_vehicles (one per seeded tour)
INSERT INTO tour_vehicles (tour_id, vehicle_id)
SELECT
  t.tour_id,
  (SELECT vehicle_id FROM travel_vehicles ORDER BY vehicle_id OFFSET ((t.tour_id - 1) % (SELECT count(*) FROM travel_vehicles)) LIMIT 1)
FROM tours t
WHERE t.tour_name LIKE 'Seed Tour %'
ON CONFLICT DO NOTHING;

-- tour_departures (one per seeded tour, future dates)
INSERT INTO tour_departures (tour_id, departure_start_date, departure_end_date, departure_capacity, departure_booked_slots, departure_price, departure_meeting_point, departure_status)
SELECT
  t.tour_id,
  (CURRENT_DATE + (((t.tour_id % 60)::int + 7)))::timestamp + interval '8 hours',
  (CURRENT_DATE + (((t.tour_id % 60)::int + 7)))::timestamp + interval '8 hours' + ((t.tour_duration_days)::text || ' days')::interval,
  20 + (t.tour_id % 31),
  0,
  (100 + (t.tour_id % 40) * 10)::numeric,
  'Meeting point ' || t.tour_id,
  'OPEN'
FROM tours t
WHERE t.tour_name LIKE 'Seed Tour %'
  AND NOT EXISTS (SELECT 1 FROM tour_departures d WHERE d.tour_id = t.tour_id);

-- customers (50)
INSERT INTO customers (customer_name, customer_email, customer_phone, customer_address, account_id)
SELECT
  'Seed Customer ' || g,
  'seedcust' || g || '@example.com',
  '09' || lpad((10000000 + g)::text, 8, '0'),
  'Address ' || g,
  (SELECT account_id FROM accounts WHERE username = 'seeduser' || g LIMIT 1)
FROM generate_series(1, 50) AS g
WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.customer_name = 'Seed Customer ' || g);

-- bookings (50)
INSERT INTO bookings (booking_code, account_id, departure_id, booking_guest_count, booking_unit_price, booking_subtotal, booking_discount_amount, booking_total_amount, booking_status, booking_note, booking_created_at)
SELECT
  'BK-20261101-' || lpad(g::text, 4, '0'),
  (SELECT account_id FROM accounts WHERE username = 'seeduser' || g LIMIT 1),
  d.departure_id,
  1 + (g % 4),
  d.departure_price,
  d.departure_price * (1 + (g % 4)),
  0,
  d.departure_price * (1 + (g % 4)),
  'PENDING',
  'Seed booking ' || g,
  NOW() - ((g % 72) || ' hours')::interval
FROM generate_series(1, 50) AS g
JOIN LATERAL (
  SELECT departure_id, departure_price
  FROM tour_departures
  WHERE departure_status = 'OPEN'
  ORDER BY departure_id
  OFFSET ((g - 1) % (SELECT count(*) FROM tour_departures WHERE departure_status = 'OPEN'))
  LIMIT 1
) d ON true
WHERE NOT EXISTS (SELECT 1 FROM bookings b WHERE b.booking_code = 'BK-20261101-' || lpad(g::text, 4, '0'));

-- travelers (one per seeded booking)
INSERT INTO travelers (booking_id, traveler_name, traveler_gender, traveler_id_document)
SELECT
  b.booking_id,
  'Traveler ' || b.booking_id,
  (ARRAY['MALE', 'FEMALE', 'OTHER'])[1 + (b.booking_id % 3)],
  'ID' || b.booking_id
FROM bookings b
WHERE b.booking_code LIKE 'BK-20261101-%'
  AND NOT EXISTS (SELECT 1 FROM travelers t WHERE t.booking_id = b.booking_id);

-- payments (one per seeded booking)
INSERT INTO payments (booking_id, payment_amount, payment_method, payment_provider_txn_id, payment_status, payment_paid_at, payment_created_at)
SELECT
  b.booking_id,
  b.booking_total_amount,
  (ARRAY['CASH', 'VNPAY', 'MOMO', 'CARD'])[1 + (b.booking_id % 4)],
  CASE WHEN (b.booking_id % 2) = 0 THEN 'TXN-' || b.booking_id ELSE NULL END,
  CASE WHEN (b.booking_id % 2) = 0 THEN 'SUCCESS' ELSE 'PENDING' END,
  CASE WHEN (b.booking_id % 2) = 0 THEN NOW() ELSE NULL END,
  NOW()
FROM bookings b
WHERE b.booking_code LIKE 'BK-20261101-%'
  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.booking_id = b.booking_id);

-- invoices (one per seeded booking)
INSERT INTO invoices (status, discount_amount, tax_amount, service_fee, total_amount, payment_method, customer_name, customer_phone, customer_email, booking_id)
SELECT
  'UNPAID',
  0, 0, 0, b.booking_total_amount,
  'CASH', 'Seed Customer', NULL, NULL, b.booking_id
FROM bookings b
WHERE b.booking_code LIKE 'BK-20261101-%'
  AND NOT EXISTS (SELECT 1 FROM invoices i WHERE i.booking_id = b.booking_id);

-- refunds (~20, from successful payments)
INSERT INTO refunds (payment_id, booking_id, refund_amount, refund_reason, refund_status, refund_requested_at)
SELECT
  p.payment_id,
  p.booking_id,
  (p.payment_amount * 0.5)::numeric,
  'Seed refund',
  'REQUESTED',
  NOW()
FROM payments p
WHERE p.payment_status = 'SUCCESS'
  AND p.booking_id IN (SELECT booking_id FROM bookings WHERE booking_code LIKE 'BK-20261101-%')
  AND NOT EXISTS (SELECT 1 FROM refunds r WHERE r.payment_id = p.payment_id);

-- ============================================================
-- Demo catalogue + sign-in-able demo accounts (idempotent).
-- All demo accounts use password: demo1234 (bcrypt, cost 10).
-- ============================================================

-- demo accounts (password: demo1234)
INSERT INTO accounts (username, password, account_email, email_verified, status, role_id, account_created_at)
SELECT v.username,
       '$2a$10$Cs/KYx3OnHMHE5kOGBrcfeZ4RcyMV3MQhLZCv5kud4s0Ux8/iRBnC',
       v.email, 1::smallint, 1::smallint,
       (SELECT account_role_id FROM account_roles WHERE role_name = v.role_name),
       NOW() - ((v.ord * 7) || ' days')::interval
FROM (VALUES
  ('demo_user1', 'demo1@example.com', 'ROLE_USER', 1),
  ('demo_user2', 'demo2@example.com', 'ROLE_USER', 2),
  ('demo_user3', 'demo3@example.com', 'ROLE_USER', 3),
  ('demo_admin', 'admin@example.com', 'ROLE_ADMIN', 4),
  ('demo_staff', 'staff@example.com', 'ROLE_STAFF', 5)
) AS v(username, email, role_name, ord)
WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.username = v.username);

-- demo promotions
INSERT INTO promotions (promotion_name, discount_percentage, start_date, end_date, description, promotion_code, is_active, usage_limit, used_count, min_order_amount)
SELECT v.name, v.pct, CURRENT_DATE - 30, CURRENT_DATE + 180, v.descr, v.code, true, v.lim, 0, v.min_order
FROM (VALUES
  ('Demo Sale Hè 2026', 10.0, 'Giảm 10% cho đơn từ 1 triệu', 'DEMO10', 100, 1000000),
  ('Demo Sale Cuối Năm 2026', 20.0, 'Giảm 20% cho đơn từ 3 triệu', 'DEMO20', 50, 3000000)
) AS v(name, pct, descr, code, lim, min_order)
WHERE NOT EXISTS (SELECT 1 FROM promotions p WHERE p.promotion_code = v.code);

-- demo guides
INSERT INTO tour_guides (tour_guide_name, tour_guide_email, tour_guide_phone, tour_guide_experience_years)
SELECT v.name, v.email, v.phone, v.exp
FROM (VALUES
  ('Nguyễn Minh Tuấn', 'minhtuan@example.com', '0908000001', 8),
  ('Lê Thị Lan Anh',   'lananh@example.com',   '0908000002', 6),
  ('Phạm Quốc Bảo',    'quocbao@example.com',  '0908000003', 12),
  ('Đỗ Hoàng Nam',     'hoangnam@example.com', '0908000004', 4)
) AS v(name, email, phone, exp)
WHERE NOT EXISTS (SELECT 1 FROM tour_guides tg WHERE tg.tour_guide_name = v.name);

-- demo accommodations
INSERT INTO accommodations (accommodation_name, location, rating, price_per_night, accommodation_type)
SELECT v.name, v.location, v.rating, v.price, v.type
FROM (VALUES
  ('Mường Thanh Luxury Nha Trang', 'Nha Trang', 4.5, 1800000, 'RESORT'),
  ('Novotel Đà Lạt',               'Đà Lạt',    4.3, 1650000, 'HOTEL'),
  ('Sunrise Hội An Resort',        'Hội An',    4.7, 2400000, 'RESORT'),
  ('Golden Bay Cần Thơ',           'Cần Thơ',   4.2, 1200000, 'HOTEL')
) AS v(name, location, rating, price, type)
WHERE NOT EXISTS (SELECT 1 FROM accommodations a WHERE a.accommodation_name = v.name);

-- demo destinations (real places, not Seed Destinations)
INSERT INTO tourist_destinations (destination_name, location, description, entry_fee)
SELECT v.name, v.location, v.description, v.fee
FROM (VALUES
  ('Vịnh Hạ Long',        'Quảng Ninh',     'Di sản thiên nhiên thế giới, hàng nghìn đảo đá vôi', 40000),
  ('Đỉnh Fansipan',       'Lào Cai',        'Nóc nhà Đông Dương cao 3143m, cáp treo SunWorld',    800000),
  ('Phố cổ Hội An',       'Quảng Nam',      'Phố cổ đèn lồng, di sản văn hóa thế giới',           120000),
  ('Bà Nà Hills',         'Đà Nẵng',        'Cầu Vàng, làng Pháp và khu vui chơi Bà Nà',          875000),
  ('Quần thể Tràng An',   'Ninh Bình',      'Danh thắng Tràng An, hang động và tháp Bái Đính',    250000),
  ('Đảo Ngọc Phú Quốc',   'Kiên Giang',     'Bãi Sao, Dinh Cậu và rừng nguyên sinh',              300000),
  ('Đồi chè Cầu Đất',     'Đà Lạt',         'Đồi chè Cầu Đất xanh mướt, bình minh trên đồi',      0),
  ('Chợ nổi Cái Răng',    'Cần Thơ',        'Chợ nổi trên sông Tiền, đặc sản miền Tây',           0),
  ('Bãi biển Mỹ Khê',     'Đà Nẵng',        'Bãi biển được Forbes bình chọn là đẹp nhất thế giới',0),
  ('Cố đô Huế',           'Thừa Thiên Huế', 'Đại Nội, lăng tẩm và chùa Thiên Mụ',                 200000)
) AS v(name, location, description, fee)
WHERE NOT EXISTS (SELECT 1 FROM tourist_destinations td WHERE td.destination_name = v.name);

-- demo trips (12 realistic catalogue entries)
INSERT INTO tours (tour_name, tour_description, tour_status, accommodation_id, tour_guide_id, promotion_id, tour_duration_days)
SELECT v.name, v.description, 'OPEN',
  (SELECT accommodation_id FROM accommodations WHERE accommodation_name = v.accommodation LIMIT 1),
  (SELECT tour_guide_id FROM tour_guides WHERE tour_guide_name = v.guide LIMIT 1),
  (SELECT promotion_id FROM promotions WHERE promotion_code = v.promo LIMIT 1),
  v.days
FROM (VALUES
  ('Đà Lạt Mộng Mơ 3N2Đ',       'Ngày 1: Xe đón TP.HCM - Đà Lạt, nhận phòng, dạo chợ đêm|Ngày 2: Đồi chè Cầu Đất, thung lũng Tình Yêu, đồi mộng mơ|Ngày 3: Trả phòng, về lại TP.HCM', 'Novotel Đà Lạt', 'Nguyễn Minh Tuấn', 'DEMO10', 3),
  ('Phú Quốc Ngọc Đảo 4N3Đ',    'Ngày 1: Đón tại Phú Quốc, nhận phòng|Ngày 2: Bãi Sao, Dinh Cậu, ngắm hoàng hôn Sunset|Ngày 3: Vinpearl Safari, Chợ đêm|Ngày 4: Trả phòng, ra sân bay', 'Novotel Đà Lạt', 'Phạm Quốc Bảo', NULL, 4),
  ('Fansipan Nóc Nhà Đông Dương 3N2Đ', 'Ngày 1: Đón tại Lào Cai, thị trấn Sa Pa|Ngày 2: Cáp treo Fansipan 3143m, chùa, ngắm toàn cảnh|Ngày 3: Thác Bạc, về Hà Nội', 'Mường Thanh Luxury Nha Trang', 'Phạm Quốc Bảo', NULL, 3),
  ('Hạ Long Di Sản 2N1Đ',       'Ngày 1: Xe đón Hà Nội, tàu ngắm vịnh Hạ Long, hang Sửng Sốt|Ngày 2: Chèo kayak Đảo Ti Tốp, về Hà Nội', 'Mường Thanh Luxury Nha Trang', 'Nguyễn Minh Tuấn', 'DEMO10', 2),
  ('Hội An - Đà Nẵng Cổ Kính 3N2Đ', 'Ngày 1: Đón tại Đà Nẵng, Sơn Trà, cầu Rồng|Ngày 2: Phố cổ Hội An, thả đèn hoa đăng|Ngày 3: Bảo tàng Đà Nẵng, về Hà Nội', 'Sunrise Hội An Resort', 'Lê Thị Lan Anh', 'DEMO20', 3),
  ('Huế Cố Đô 2N1Đ',            'Ngày 1: Đại Nội Huế, chùa Thiên Mụ|Ngày 2: Lăng Khải Định, lăng Tự Đức, về Hà Nội', 'Sunrise Hội An Resort', 'Đỗ Hoàng Nam', NULL, 2),
  ('Nha Trang Biển Xanh 4N3Đ',  'Ngày 1: Đón Nha Trang, nhận phòng|Ngày 2: Lặn san hô Hòn Mun, đảo Điệp Sơn|Ngày 3: Tháp Bà Ponagar, chợ đêm|Ngày 4: Trả phòng, về TP.HCM', 'Mường Thanh Luxury Nha Trang', 'Lê Thị Lan Anh', 'DEMO10', 4),
  ('Ninh Bình Tràng An 1N',     'Ngày 1: Tràng An, hang động, chùa Bái Đính, thuyền Tam Cốc', 'Novotel Đà Lạt', 'Đỗ Hoàng Nam', NULL, 1),
  ('Cần Thơ - Chợ Nổi Cái Răng 2N1Đ', 'Ngày 1: Xe đón TP.HCM, bến Ninh Kiều|Ngày 2: Chợ nổi Cái Răng sớm, vườn trái cây, về TP.HCM', 'Golden Bay Cần Thơ', 'Nguyễn Minh Tuấn', NULL, 2),
  ('Vũng Tàu - Long Sơn Cuối Tuần 1N', 'Ngày 1: Đón tại TP.HCM, Long Sơn, bãi Trước, chiếm ngưỡng hải đăng, về TP.HCM', 'Golden Bay Cần Thơ', 'Đỗ Hoàng Nam', NULL, 1),
  ('Đà Nẵng - Bà Nà Hills 2N1Đ','Ngày 1: Đón tại Đà Nẵng, Bảo tàng Điêu khắc|Ngày 2: Bà Nà Hills, Cầu Vàng, làng Pháp, về Hà Nội', 'Sunrise Hội An Resort', 'Lê Thị Lan Anh', 'DEMO20', 2),
  ('Quy Nhơn - Kỳ Co Eo Gió 3N2Đ', 'Ngày 1: Đón Quy Nhơn, Tịnh 1|Ngày 2: Kỳ Co, Eo Gió, lặn ngắm san hô|Ngày 3: Trả phòng, về Hà Nội', 'Mường Thanh Luxury Nha Trang', 'Phạm Quốc Bảo', NULL, 3)
) AS v(name, description, accommodation, guide, promo, days)
WHERE NOT EXISTS (SELECT 1 FROM tours t WHERE t.tour_name = v.name);

-- demo tour images (2 per tour, local assets)
INSERT INTO tour_images (image_url, tour_id)
SELECT v.url, t.tour_id
FROM (VALUES
  ('Đà Lạt Mộng Mơ 3N2Đ',       '/images/destination-sa-pa.jpg'),
  ('Đà Lạt Mộng Mơ 3N2Đ',       '/images/tour-sa-pa.jpg'),
  ('Phú Quốc Ngọc Đảo 4N3Đ',    '/images/tour-phu-quoc.jpg'),
  ('Phú Quốc Ngọc Đảo 4N3Đ',    '/images/cta-phu-quoc.jpg'),
  ('Fansipan Nóc Nhà Đông Dương 3N2Đ', '/images/tour-sa-pa.jpg'),
  ('Fansipan Nóc Nhà Đông Dương 3N2Đ', '/images/destination-sa-pa.jpg'),
  ('Hạ Long Di Sản 2N1Đ',       '/images/tour-lan-ha.jpg'),
  ('Hạ Long Di Sản 2N1Đ',       '/images/hero-lan-ha.jpg'),
  ('Hội An - Đà Nẵng Cổ Kính 3N2Đ', '/images/tour-hoi-an.jpg'),
  ('Hội An - Đà Nẵng Cổ Kính 3N2Đ', '/images/destination-hoi-an.jpg'),
  ('Huế Cố Đô 2N1Đ',            '/images/tour-hue.jpg'),
  ('Nha Trang Biển Xanh 4N3Đ',  '/images/destination-nha-trang.jpg'),
  ('Nha Trang Biển Xanh 4N3Đ',  '/images/hero-lan-ha.jpg'),
  ('Ninh Bình Tràng An 1N',     '/images/tour-ninh-binh.jpg'),
  ('Cần Thơ - Chợ Nổi Cái Răng 2N1Đ', '/images/story-coast.jpg'),
  ('Vũng Tàu - Long Sơn Cuối Tuần 1N', '/images/story-coast.jpg'),
  ('Đà Nẵng - Bà Nà Hills 2N1Đ','/images/tour-hoi-an.jpg'),
  ('Đà Nẵng - Bà Nà Hills 2N1Đ','/images/destination-hoi-an.jpg'),
  ('Quy Nhơn - Kỳ Co Eo Gió 3N2Đ', '/images/hero-lan-ha.jpg')
) AS v(name, url)
JOIN tours t ON t.tour_name = v.name
WHERE NOT EXISTS (SELECT 1 FROM tour_images ti WHERE ti.tour_id = t.tour_id AND ti.image_url = v.url);

-- demo tour destinations
INSERT INTO tour_destinations (tour_id, destination_id)
SELECT t.tour_id, d.destination_id
FROM (VALUES
  ('Đà Lạt Mộng Mơ 3N2Đ',       'Đồi chè Cầu Đất'),
  ('Phú Quốc Ngọc Đảo 4N3Đ',    'Đảo Ngọc Phú Quốc'),
  ('Fansipan Nóc Nhà Đông Dương 3N2Đ', 'Đỉnh Fansipan'),
  ('Hạ Long Di Sản 2N1Đ',       'Vịnh Hạ Long'),
  ('Hội An - Đà Nẵng Cổ Kính 3N2Đ', 'Phố cổ Hội An'),
  ('Hội An - Đà Nẵng Cổ Kính 3N2Đ', 'Bãi biển Mỹ Khê'),
  ('Huế Cố Đô 2N1Đ',            'Cố đô Huế'),
  ('Ninh Bình Tràng An 1N',     'Quần thể Tràng An'),
  ('Cần Thơ - Chợ Nổi Cái Răng 2N1Đ', 'Chợ nổi Cái Răng'),
  ('Đà Nẵng - Bà Nà Hills 2N1Đ','Bà Nà Hills'),
  ('Đà Nẵng - Bà Nà Hills 2N1Đ','Bãi biển Mỹ Khê')
) AS v(name, destination)
JOIN tours t ON t.tour_name = v.name
JOIN tourist_destinations d ON d.destination_name = v.destination
ON CONFLICT DO NOTHING;

-- demo tour vehicles
INSERT INTO tour_vehicles (tour_id, vehicle_id)
SELECT t.tour_id, v.vehicle_id
FROM (VALUES ('Đà Lạt Mộng Mơ 3N2Đ'), ('Phú Quốc Ngọc Đảo 4N3Đ'), ('Fansipan Nóc Nhà Đông Dương 3N2Đ'),
             ('Hạ Long Di Sản 2N1Đ'), ('Hội An - Đà Nẵng Cổ Kính 3N2Đ'), ('Huế Cố Đô 2N1Đ'),
             ('Nha Trang Biển Xanh 4N3Đ'), ('Ninh Bình Tràng An 1N'), ('Cần Thơ - Chợ Nổi Cái Răng 2N1Đ'),
             ('Vũng Tàu - Long Sơn Cuối Tuần 1N'), ('Đà Nẵng - Bà Nà Hills 2N1Đ'), ('Quy Nhơn - Kỳ Co Eo Gió 3N2Đ')) AS n(name)
JOIN tours t ON t.tour_name = n.name
JOIN travel_vehicles v ON v.vehicle_type = 'Bus' AND v.capacity >= 16
ON CONFLICT DO NOTHING;

-- demo departures: 2 future per trip + 1 past completed trip for Nha Trang
INSERT INTO tour_departures (tour_id, departure_start_date, departure_end_date, departure_capacity, departure_booked_slots, departure_price, departure_meeting_point, departure_status)
SELECT t.tour_id,
       NOW() + (v.offset_days || ' days')::interval,
       NOW() + (v.offset_days || ' days')::interval + ((t.tour_duration_days)::text || ' days')::interval,
       v.capacity, 0, v.price, 'DEMO ' || v.meeting, 'OPEN'
FROM tours t
JOIN (VALUES
  ('Đà Lạt Mộng Mơ 3N2Đ', 20, 2490000, 25, 'TP.HCM - 116 Bùi Thị Xuân, Q.1 (Đợt 1)'),
  ('Đà Lạt Mộng Mơ 3N2Đ', 52, 2590000, 25, 'TP.HCM - 116 Bùi Thị Xuân, Q.1 (Đợt 2)'),
  ('Phú Quốc Ngọc Đảo 4N3Đ', 18, 6990000, 30, 'TP.HCM - 116 Bùi Thị Xuân, Q.1 (Đợt 1)'),
  ('Phú Quốc Ngọc Đảo 4N3Đ', 49, 7290000, 30, 'Hà Nội - Duy Tân 2A, Q.Cầu Giấy (Đợt 2)'),
  ('Fansipan Nóc Nhà Đông Dương 3N2Đ', 22, 5990000, 20, 'Hà Nội - Duy Tân 2A, Q.Cầu Giấy (Đợt 1)'),
  ('Fansipan Nóc Nhà Đông Dương 3N2Đ', 55, 6290000, 20, 'Hà Nội - Duy Tân 2A, Q.Cầu Giấy (Đợt 2)'),
  ('Hạ Long Di Sản 2N1Đ', 15, 3290000, 35, 'Hà Nội - Duy Tân 2A, Q.Cầu Giấy (Đợt 1)'),
  ('Hạ Long Di Sản 2N1Đ', 45, 3490000, 35, 'Hà Nội - Duy Tân 2A, Q.Cầu Giấy (Đợt 2)'),
  ('Hội An - Đà Nẵng Cổ Kính 3N2Đ', 30, 5490000, 25, 'TP.HCM - 116 Bùi Thị Xuân, Q.1 (Đợt 1)'),
  ('Hội An - Đà Nẵng Cổ Kính 3N2Đ', 62, 5690000, 25, 'Hà Nội - Duy Tân 2A, Q.Cầu Giấy (Đợt 2)'),
  ('Huế Cố Đô 2N1Đ', 19, 2290000, 30, 'Hà Nội - Duy Tân 2A, Q.Cầu Giấy (Đợt 1)'),
  ('Huế Cố Đô 2N1Đ', 50, 2390000, 30, 'Hà Nội - Duy Tân 2A, Q.Cầu Giấy (Đợt 2)'),
  ('Nha Trang Biển Xanh 4N3Đ', 21, 6490000, 30, 'TP.HCM - 116 Bùi Thị Xuân, Q.1 (Đợt 1)'),
  ('Nha Trang Biển Xanh 4N3Đ', 54, 6790000, 30, 'TP.HCM - 116 Bùi Thị Xuân, Q.1 (Đợt 2)'),
  ('Ninh Bình Tràng An 1N', 12, 1290000, 45, 'Hà Nội - Duy Tân 2A, Q.Cầu Giấy (Đợt 1)'),
  ('Ninh Bình Tràng An 1N', 40, 1390000, 45, 'Hà Nội - Duy Tân 2A, Q.Cầu Giấy (Đợt 2)'),
  ('Cần Thơ - Chợ Nổi Cái Răng 2N1Đ', 23, 2490000, 30, 'TP.HCM - 116 Bùi Thị Xuân, Q.1 (Đợt 1)'),
  ('Cần Thơ - Chợ Nổi Cái Răng 2N1Đ', 59, 2490000, 30, 'TP.HCM - 116 Bùi Thị Xuân, Q.1 (Đợt 2)'),
  ('Vũng Tàu - Long Sơn Cuối Tuần 1N', 14, 890000, 40, 'TP.HCM - 116 Bùi Thị Xuân, Q.1 (Đợt 1)'),
  ('Vũng Tàu - Long Sơn Cuối Tuần 1N', 42, 990000, 40, 'TP.HCM - 116 Bùi Thị Xuân, Q.1 (Đợt 2)'),
  ('Đà Nẵng - Bà Nà Hills 2N1Đ', 26, 3790000, 25, 'Hà Nội - Duy Tân 2A, Q.Cầu Giấy (Đợt 1)'),
  ('Đà Nẵng - Bà Nà Hills 2N1Đ', 58, 3990000, 25, 'Đà Nẵng - Sân bay Đà Nẵng, Ga đi (Đợt 2)'),
  ('Quy Nhơn - Kỳ Co Eo Gió 3N2Đ', 27, 4290000, 25, 'Hà Nội - Duy Tân 2A, Q.Cầu Giấy (Đợt 1)'),
  ('Quy Nhơn - Kỳ Co Eo Gió 3N2Đ', 59, 4590000, 25, 'Hà Nội - Duy Tân 2A, Q.Cầu Giấy (Đợt 2)')
) AS v(name, offset_days, price, capacity, meeting) ON t.tour_name = v.name
WHERE NOT EXISTS (SELECT 1 FROM tour_departures d WHERE d.departure_meeting_point = 'DEMO ' || v.meeting);

-- two past, already-completed departures (host the COMPLETED demo bookings)
INSERT INTO tour_departures (tour_id, departure_start_date, departure_end_date, departure_capacity, departure_booked_slots, departure_price, departure_meeting_point, departure_status)
SELECT t.tour_id, v.start_at, v.end_at, 30, 0, v.price, 'DEMO ' || v.meeting, 'CLOSED'
FROM (VALUES
  ('Nha Trang Biển Xanh 4N3Đ', NOW() - interval '20 days', NOW() - interval '16 days', 6290000, 'TP.HCM - 116 Bùi Thị Xuân, Q.1 (Đợt Quá Khứ)'),
  ('Cần Thơ - Chợ Nổi Cái Răng 2N1Đ', NOW() - interval '50 days', NOW() - interval '49 days', 2290000, 'TP.HCM - 116 Bùi Thị Xuân, Q.1 (Đợt Quá Khứ)')
) AS v(name, start_at, end_at, price, meeting)
JOIN tours t ON t.tour_name = v.name
WHERE NOT EXISTS (SELECT 1 FROM tour_departures d WHERE d.departure_meeting_point = 'DEMO ' || v.meeting);

-- demo bookings (mixed statuses) for the demo accounts
INSERT INTO bookings (booking_code, account_id, departure_id, booking_guest_count, booking_unit_price, booking_subtotal, booking_discount_amount, booking_total_amount, booking_status, booking_note, booking_created_at, promotion_id)
SELECT v.code,
       (SELECT account_id FROM accounts WHERE username = v.username),
       d.departure_id,
       v.guests,
       d.departure_price,
       d.departure_price * v.guests,
       CASE WHEN v.promo IS NULL THEN 0
            ELSE ROUND(d.departure_price * v.guests * (SELECT discount_percentage FROM promotions WHERE promotion_code = v.promo) / 100, 0) END,
       d.departure_price * v.guests
         - CASE WHEN v.promo IS NULL THEN 0
                ELSE ROUND(d.departure_price * v.guests * (SELECT discount_percentage FROM promotions WHERE promotion_code = v.promo) / 100, 0) END,
       v.status,
       v.note,
       NOW() - (v.days_ago || ' days')::interval,
       (SELECT promotion_id FROM promotions WHERE promotion_code = v.promo)
FROM (VALUES
  ('BK-DEMO-0001', 'demo_user1', 'Đà Lạt Mộng Mơ 3N2Đ', 0, 2, 'PENDING',   'DEMO10', 'Liên hệ sớm để giữ chỗ - 0909111001',        3),
  ('BK-DEMO-0002', 'demo_user2', 'Hạ Long Di Sản 2N1Đ', 0, 3, 'PAID',      NULL,     'Liên hệ: 0909111002',                       5),
  ('BK-DEMO-0003', 'demo_user1', 'Phú Quốc Ngọc Đảo 4N3Đ', 0, 2, 'PAID',   'DEMO20', 'Liên hệ: 0909111001',                       8),
  ('BK-DEMO-0004', 'demo_user3', 'Hội An - Đà Nẵng Cổ Kính 3N2Đ', 0, 1, 'CONFIRMED', NULL, 'Liên hệ: 0909111003',                   12),
  ('BK-DEMO-0005', 'demo_user2', 'Ninh Bình Tràng An 1N', 0, 2, 'CONFIRMED', 'DEMO10', 'Liên hệ: 0909111002',                       15),
  ('BK-DEMO-0006', 'demo_user3', 'Cần Thơ - Chợ Nổi Cái Răng 2N1Đ', 0, 2, 'COMPLETED', NULL, 'Liên hệ: 0909111003',                   55),
  ('BK-DEMO-0007', 'demo_user1', 'Nha Trang Biển Xanh 4N3Đ', 0, 2, 'COMPLETED', NULL, 'Liên hệ: 0909111001',                        40),
  ('BK-DEMO-0008', 'demo_user2', 'Fansipan Nóc Nhà Đông Dương 3N2Đ', 0, 1, 'CANCELLED', NULL, 'Hủy do đổi lịch công tác',             10),
  ('BK-DEMO-0009', 'demo_user3', 'Vũng Tàu - Long Sơn Cuối Tuần 1N', 0, 2, 'CANCELLED', NULL, 'Hủy do thời tiết xấu',                20),
  ('BK-DEMO-0010', 'demo_user1', 'Đà Nẵng - Bà Nà Hills 2N1Đ', 0, 2, 'PAID', 'DEMO20', 'Liên hệ: 0909111001',                         6),
  ('BK-DEMO-0011', 'demo_user2', 'Huế Cố Đô 2N1Đ', 0, 1, 'PENDING',        NULL,     'Liên hệ: 0909111002',                         1),
  ('BK-DEMO-0012', 'demo_user3', 'Quy Nhơn - Kỳ Co Eo Gió 3N2Đ', 0, 2, 'CONFIRMED', 'DEMO10', 'Liên hệ: 0909111003',                  18),
  ('BK-DEMO-0013', 'demo_user1', 'Ninh Bình Tràng An 1N', 0, 1, 'PAID',     NULL,     'Liên hệ: 0909111001',                         7),
  ('BK-DEMO-0014', 'demo_user2', 'Cần Thơ - Chợ Nổi Cái Răng 2N1Đ', 0, 3, 'COMPLETED', NULL, 'Liên hệ: 0909111002',                60),
  ('BK-DEMO-0015', 'demo_user3', 'Hạ Long Di Sản 2N1Đ', 1, 2, 'PENDING',   'DEMO10', 'Liên hệ: 0909111003',                         2)
) AS v(code, username, tour_name, dep_ord, guests, status, promo, note, days_ago)
JOIN tours t ON t.tour_name = v.tour_name
JOIN LATERAL (
  SELECT departure_id, departure_price
  FROM tour_departures
  WHERE tour_id = t.tour_id
  ORDER BY departure_start_date
  OFFSET v.dep_ord LIMIT 1
) d ON true
WHERE NOT EXISTS (SELECT 1 FROM bookings b WHERE b.booking_code = v.code);

-- demo payments (SUCCESS for paid bookings, PENDING for pending ones)
INSERT INTO payments (booking_id, payment_amount, payment_method, payment_provider_txn_id, payment_status, payment_paid_at, payment_created_at)
SELECT b.booking_id,
       b.booking_total_amount,
       v.method,
       'TXN-DEMO-' || split_part(v.code, '-', 3),
       'SUCCESS',
       b.booking_created_at + interval '2 hours',
       b.booking_created_at + interval '2 hours'
FROM bookings b
JOIN (VALUES
  ('BK-DEMO-0002', 'CARD'),
  ('BK-DEMO-0003', 'VNPAY'),
  ('BK-DEMO-0004', 'MOMO'),
  ('BK-DEMO-0005', 'CASH'),
  ('BK-DEMO-0006', 'VNPAY'),
  ('BK-DEMO-0007', 'MOMO'),
  ('BK-DEMO-0008', 'CARD'),
  ('BK-DEMO-0009', 'MOMO'),
  ('BK-DEMO-0010', 'CARD'),
  ('BK-DEMO-0012', 'MOMO'),
  ('BK-DEMO-0013', 'CASH'),
  ('BK-DEMO-0014', 'VNPAY')
) AS v(code, method) ON v.code = b.booking_code
WHERE NOT EXISTS (SELECT 1 FROM payments p WHERE p.booking_id = b.booking_id);

INSERT INTO payments (booking_id, payment_amount, payment_method, payment_provider_txn_id, payment_status, payment_paid_at, payment_created_at)
SELECT b.booking_id, b.booking_total_amount, v.method, NULL, 'PENDING', NULL, b.booking_created_at + interval '2 hours'
FROM bookings b
JOIN (VALUES ('BK-DEMO-0001', 'CARD'), ('BK-DEMO-0011', 'VNPAY'), ('BK-DEMO-0015', 'MOMO')) AS v(code, method) ON v.code = b.booking_code
WHERE NOT EXISTS (SELECT 1 FROM payments p WHERE p.booking_id = b.booking_id);

-- demo invoices: PAID for paid bookings, UNPAID for pending
INSERT INTO invoices (status, discount_amount, tax_amount, service_fee, total_amount, payment_method, customer_name, customer_phone, customer_email, booking_id)
SELECT
  CASE WHEN b.booking_status IN ('PAID', 'CONFIRMED', 'COMPLETED') THEN 'PAID' ELSE 'UNPAID' END,
  b.booking_discount_amount, 0, 0, b.booking_total_amount,
  (SELECT p.payment_method FROM payments p WHERE p.booking_id = b.booking_id LIMIT 1),
  (SELECT CASE av.username WHEN 'demo_user1' THEN 'Nguyễn Văn An' WHEN 'demo_user2' THEN 'Trần Thị Bình' WHEN 'demo_user3' THEN 'Lê Văn Cường' ELSE 'Demo Khách' END
   FROM accounts av WHERE av.account_id = b.account_id),
  (SELECT CASE av.username WHEN 'demo_user1' THEN '0909111001' WHEN 'demo_user2' THEN '0909111002' WHEN 'demo_user3' THEN '0909111003' ELSE NULL END
   FROM accounts av WHERE av.account_id = b.account_id),
  (SELECT av.account_email FROM accounts av WHERE av.account_id = b.account_id),
  b.booking_id
FROM bookings b
WHERE b.booking_code LIKE 'BK-DEMO-%'
  AND NOT EXISTS (SELECT 1 FROM invoices i WHERE i.booking_id = b.booking_id);

-- demo refunds: one REQUESTED, one DONE, for the two cancelled paid bookings
INSERT INTO refunds (payment_id, booking_id, refund_amount, refund_reason, refund_status, refund_requested_at)
SELECT p.payment_id, p.booking_id, (p.payment_amount * 0.5)::numeric, 'Khách hủy do đổi lịch', 'REQUESTED', NOW() - interval '9 days'
FROM payments p
WHERE p.booking_id = (SELECT booking_id FROM bookings WHERE booking_code = 'BK-DEMO-0008')
  AND NOT EXISTS (SELECT 1 FROM refunds r WHERE r.payment_id = p.payment_id);

INSERT INTO refunds (payment_id, booking_id, refund_amount, refund_reason, refund_status, refund_requested_at)
SELECT p.payment_id, p.booking_id, p.payment_amount, 'Khách hủy do thời tiết', 'DONE', NOW() - interval '19 days'
FROM payments p
WHERE p.booking_id = (SELECT booking_id FROM bookings WHERE booking_code = 'BK-DEMO-0009')
  AND NOT EXISTS (SELECT 1 FROM refunds r WHERE r.payment_id = p.payment_id);

-- demo ratings for the three COMPLETED bookings
INSERT INTO tour_ratings (tour_id, account_id, booking_id, rating_value, rating_comment, rating_created_at)
SELECT t.tour_id, b.account_id, b.booking_id, v.rating, v.comment, NOW() - (v.days_ago || ' days')::interval
FROM (VALUES
  ('BK-DEMO-0006', 5, 'Tour miền Tây dễ thương, cô hướng dẫn viên nhiệt tình, đồ ăn ngon!', 47),
  ('BK-DEMO-0007', 4, 'Biển đẹp, lịch trình hợp lý. Hơi đông người vào cuối tuần nhưng vẫn rất đáng tiền.', 13),
  ('BK-DEMO-0014', 5, 'Chợ nổi Cái Răng trải nghiệm tuyệt vời, sẽ quay lại lần sau cùng gia đình.', 47)
) AS v(code, rating, comment, days_ago)
JOIN bookings b ON b.booking_code = v.code
JOIN tour_departures d ON d.departure_id = b.departure_id
JOIN tours t ON t.tour_id = d.tour_id
WHERE NOT EXISTS (SELECT 1 FROM tour_ratings r WHERE r.booking_id = b.booking_id);

-- one traveler row per seat on the demo bookings
INSERT INTO travelers (booking_id, traveler_name, traveler_gender, traveler_id_document, traveler_phone, traveler_email)
SELECT b.booking_id,
       'Khách Demo ' || split_part(b.booking_code, '-', 3) || '-' || g,
       (ARRAY['MALE', 'FEMALE'])[1 + ((g + length(b.booking_code)) % 2)],
       'IDDEMO' || split_part(b.booking_code, '-', 3) || g,
       NULL,
       NULL
FROM bookings b
CROSS JOIN LATERAL generate_series(1, b.booking_guest_count) AS g
WHERE b.booking_code LIKE 'BK-DEMO-%'
  AND NOT EXISTS (SELECT 1 FROM travelers t WHERE t.booking_id = b.booking_id);

-- sync seat counters for the demo departures (non-cancelled demo bookings only)
UPDATE tour_departures d
SET departure_booked_slots = LEAST(d.departure_capacity, agg.guests)
FROM (
  SELECT departure_id, SUM(booking_guest_count) AS guests
  FROM bookings
  WHERE booking_code LIKE 'BK-DEMO-%' AND booking_status <> 'CANCELLED'
  GROUP BY departure_id
) agg
WHERE d.departure_id = agg.departure_id;

COMMIT;
