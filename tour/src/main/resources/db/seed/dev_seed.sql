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

COMMIT;
