-- QUANLYBANTOUR - DEV SEED ONLY (PostgreSQL)
-- Run once on an empty database AFTER Flyway migrations:
--   psql -U <user> -d <database> -f quanlybantour_booking_floor.sql
-- Schema lives in db/migration and db/callback. Do not add DDL here.

SET client_encoding = 'UTF8';

-- ============================================================
-- SEED DU LIEU (thu tu bang cha -> bang con, khong can tat FK)
-- ============================================================

-- A. ROLES
INSERT INTO account_roles (account_role_id, role_name) VALUES
(1, 'ROLE_ADMIN'),
(2, 'ROLE_USER'),
(3, 'ROLE_STAFF');

-- B. ACCOMMODATIONS
INSERT INTO accommodations (accommodation_id, accommodation_name, location, rating, price_per_night, accommodation_type) VALUES
(1, 'Khách sạn Mường Thanh Grand', '962 Ngô Quyền, Sơn Trà, Đà Nẵng', 4.5, 1200000, 'HOTEL'),
(2, 'Vinpearl Resort & Spa', 'Đảo Hòn Tre, Nha Trang', 5.0, 3500000, 'RESORT'),
(3, 'Homestay Cánh Đồng Hoa', 'Phường 8, Đà Lạt', 4.0, 500000, 'HOMESTAY');

-- C. VEHICLES
INSERT INTO travel_vehicles (vehicle_id, vehicle_type, capacity, rental_price_per_day) VALUES
(1, 'Xe Giường Nằm Thaco', 40, 3000000),
(2, 'Xe Limousine Dcar 9 chỗ', 9, 2500000),
(3, 'Tàu Cao Tốc Superdong', 100, 5000000);

-- D. GUIDES
INSERT INTO tour_guides (tour_guide_id, tour_guide_name, tour_guide_email, tour_guide_phone, tour_guide_experience_years) VALUES
(1, 'Nguyễn Văn Hướng', 'huong.nv@tour.com', '0901234567', 5),
(2, 'Trần Thị Dẫn', 'dan.tt@tour.com', '0909888777', 3);

-- E. DESTINATIONS
INSERT INTO tourist_destinations (destination_id, destination_name, location, description, entry_fee) VALUES
(1, 'Phố Cổ Hội An', 'Quảng Nam', 'Di sản văn hóa thế giới.', 120000),
(2, 'Bà Nà Hills', 'Đà Nẵng', 'Đường lên tiên cảnh.', 900000),
(3, 'Tháp Bà Ponagar', 'Nha Trang', 'Di tích Chăm Pa.', 30000);

-- F. PROMOTIONS
INSERT INTO promotions (promotion_id, promotion_name, discount_percentage, start_date, end_date, description) VALUES
(1, 'Chào Hè 2026', 10.0, '2026-05-01', '2026-08-31', 'Giảm giá mùa hè tương lai.'),
(2, 'Black Friday', 20.0, '2026-11-20', '2026-11-30', 'Sale cuối năm tương lai.'),
(3, 'Siêu Sale Tháng 11', 15.0, '2025-11-01', '2025-12-31', 'Khuyến mãi test chức năng.');

-- G. ACCOUNTS (Pass: 123456)
INSERT INTO accounts (account_id, username, password, email_verified, status, account_created_at, account_updated_at, role_id) VALUES
(1, 'user01', '$2a$10$EnfdjqhOU9a7wzPUVFYENO3i6K..WMO1M92uKmhZnMReJjZl/rxr6', 1, 1, NOW(), NOW(), 2),
(2, 'user02', '$2a$10$EnfdjqhOU9a7wzPUVFYENO3i6K..WMO1M92uKmhZnMReJjZl/rxr6', 1, 1, NOW(), NOW(), 2);

-- H. CUSTOMERS
INSERT INTO customers (customer_id, customer_name, customer_email, customer_phone, customer_address, customer_date_of_birth, account_id) VALUES
(2, 'Nguyễn Văn Khách', 'khach01@gmail.com', '0911111111', 'TP.HCM', '1995-05-15', 2),
(3, 'Lê Thị Mua', 'mua.le@gmail.com', '0922222222', 'Đà Nẵng', '2000-10-20', 1);

-- I. TOURS (dates, capacity, and price live in tour_departures)
INSERT INTO tours (tour_id, tour_name, tour_description, tour_duration_days, tour_status, accommodation_id, tour_guide_id, promotion_id) VALUES
(1, 'Đà Nẵng - Hội An: Hành Trình Di Sản',
'Ngày 1: Xe đón quý khách tại sân bay Đà Nẵng, đưa về khách sạn Mường Thanh nhận phòng. Chiều tham quan Bán đảo Sơn Trà.|Ngày 2: Khởi hành đi Bà Nà Hills - Đường lên tiên cảnh. Check-in Cầu Vàng. Ăn trưa Buffet. Chiều về Hội An tham quan phố cổ.|Ngày 3: Mua sắm đặc sản Miền Trung tại chợ Hàn. Xe đưa quý khách ra sân bay.',
3, 'OPEN', 1, 1, 1),
(2, 'Nha Trang Biển Gọi',
'Ngày 1: Đón khách tại sân bay Cam Ranh. Về trung tâm thành phố, nhận phòng Vinpearl Resort.|Ngày 2: Du ngoạn 4 đảo: Hòn Mun, Hòn Một, Bãi Tranh. Lặn ngắm san hô.|Ngày 3: Vui chơi không giới hạn tại VinWonders Nha Trang.|Ngày 4: Tắm bùn khoáng nóng I-Resort. Tiễn đoàn.',
4, 'OPEN', 2, 2, NULL);

-- J. TOUR IMAGES
INSERT INTO tour_images (image_url, tour_id) VALUES
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764752418/tours/68a8ed00-a2ca-4c8c-8d94-ef65e878f805_40-trai-nghiem-du-thuyen-tren-song-hoai.png.png', 1),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764752414/tours/86aeb11f-acdd-4bc9-a9b9-9d71e8a73766_hoi-an-1680591517857660432696.webp.webp', 1),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764752414/tours/5ae3201b-903f-4090-828b-f1b02ed491c2_du-lich-da-nang-hoi-an-kinh-nghiem-cho-nguoi-moi-bat-dau-1.png.png', 1),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764752413/tours/96bdc303-a1ef-43c5-8ed7-3a7b56cef264_tour-da-nang.jpg.jpg', 1),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764751830/tours/1d0193d8-32e0-4144-8ff1-2f5f4f3e3611_adgahjd-1755152740753.jpg.jpg', 2),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764751830/tours/ec62a7f6-c557-4c8f-9a9b-9d71e8a73766_Bien-nha-trang-3-jpg.webp.webp', 2),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764751830/tours/db9f9072-4c8b-405f-8b84-2b93b882e3ea_nha-trang_1.jpg.jpg', 2),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764751830/tours/981214ff-327b-4f46-89ec-0263125e4d5b_kinh-nghiem-du-lich-nha-trang-2_1689411065.jpg.jpg', 2);

-- K. LIÊN KẾT BẢNG
INSERT INTO tour_destinations (tour_id, destination_id) VALUES
 (1, 1),
 (1, 2),
 (2, 3);

INSERT INTO tour_vehicles (tour_id, vehicle_id) VALUES
 (1, 2),
 (2, 1);

-- L. BOOKING FLOOR - DEPARTURES
INSERT INTO tour_departures (departure_id, tour_id, departure_start_date, departure_end_date,
    departure_capacity, departure_booked_slots, departure_price, departure_meeting_point, departure_status)
VALUES (1, 1, '2026-06-15 08:00:00', '2026-06-17 17:00:00', 30, 2, 4500000, 'Sân bay Đà Nẵng', 'OPEN');

INSERT INTO tour_departures (departure_id, tour_id, departure_start_date, departure_end_date,
    departure_capacity, departure_booked_slots, departure_price, departure_meeting_point, departure_status)
VALUES (2, 2, '2026-07-10 07:00:00', '2026-07-13 18:00:00', 40, 0, 6500000, 'Sân bay Cam Ranh', 'OPEN');

-- M. BOOKING + MANIFEST + PAYMENT
INSERT INTO bookings (booking_id, booking_code, account_id, departure_id,
    booking_created_at, booking_guest_count, booking_unit_price, booking_subtotal,
    booking_discount_amount, booking_total_amount, booking_status, booking_note)
VALUES (1, 'BK-20260303-0001', 2, 1,
    NOW(), 2, 4500000, 9000000, 0, 9000000, 'PAID', 'Xin đón tại cổng A');

INSERT INTO travelers (traveler_id, booking_id, traveler_name, traveler_date_of_birth, traveler_gender, traveler_id_document) VALUES
(1, 1, 'Nguyễn Văn Khách', '1995-05-15', 'MALE',   '012345678901'),
(2, 1, 'Lê Thị Mua',        '2000-10-20', 'FEMALE', '098765432109');

INSERT INTO payments (payment_id, booking_id, payment_amount, payment_method,
    payment_provider_txn_id, payment_status, payment_paid_at)
VALUES (1, 1, 9000000, 'VNPAY', 'VNP_20260303_000123456', 'SUCCESS', NOW());

-- N. INVOICE FOR THE BOOKING
INSERT INTO invoices (invoice_id, invoice_created_at, status, discount_amount, tax_amount,
    total_amount, payment_method, customer_name, customer_phone, customer_email, booking_id, service_fee)
VALUES (1, NOW(), 'PAID', 0, 0, 9000000, 'VNPAY',
    'Nguyễn Văn Khách', '0911111111', 'khach01@gmail.com', 1, 0);

-- Refund ma vidu (kich hoat khi can test hoan tien):
-- INSERT INTO refunds (refund_id, payment_id, booking_id, refund_amount, refund_reason, refund_status)
-- VALUES (1, 1, 1, 4500000, 'Khách xin hoàn một chỗ', 'REQUESTED');

-- Explicit seed IDs do not advance identity sequences. Align them before application inserts.
SELECT setval(pg_get_serial_sequence('account_roles', 'account_role_id'),
              COALESCE((SELECT MAX(account_role_id) FROM account_roles), 1), true);
SELECT setval(pg_get_serial_sequence('accounts', 'account_id'),
              COALESCE((SELECT MAX(account_id) FROM accounts), 1), true);
SELECT setval(pg_get_serial_sequence('accommodations', 'accommodation_id'),
              COALESCE((SELECT MAX(accommodation_id) FROM accommodations), 1), true);
SELECT setval(pg_get_serial_sequence('travel_vehicles', 'vehicle_id'),
              COALESCE((SELECT MAX(vehicle_id) FROM travel_vehicles), 1), true);
SELECT setval(pg_get_serial_sequence('tour_guides', 'tour_guide_id'),
              COALESCE((SELECT MAX(tour_guide_id) FROM tour_guides), 1), true);
SELECT setval(pg_get_serial_sequence('tourist_destinations', 'destination_id'),
              COALESCE((SELECT MAX(destination_id) FROM tourist_destinations), 1), true);
SELECT setval(pg_get_serial_sequence('promotions', 'promotion_id'),
              COALESCE((SELECT MAX(promotion_id) FROM promotions), 1), true);
SELECT setval(pg_get_serial_sequence('tours', 'tour_id'),
              COALESCE((SELECT MAX(tour_id) FROM tours), 1), true);
SELECT setval(pg_get_serial_sequence('tour_images', 'image_id'),
              COALESCE((SELECT MAX(image_id) FROM tour_images), 1), true);
SELECT setval(pg_get_serial_sequence('invoices', 'invoice_id'),
              COALESCE((SELECT MAX(invoice_id) FROM invoices), 1), true);
SELECT setval(pg_get_serial_sequence('customers', 'customer_id'),
              COALESCE((SELECT MAX(customer_id) FROM customers), 1), true);
SELECT setval(pg_get_serial_sequence('tour_departures', 'departure_id'),
              COALESCE((SELECT MAX(departure_id) FROM tour_departures), 1), true);
SELECT setval(pg_get_serial_sequence('bookings', 'booking_id'),
              COALESCE((SELECT MAX(booking_id) FROM bookings), 1), true);
SELECT setval(pg_get_serial_sequence('travelers', 'traveler_id'),
              COALESCE((SELECT MAX(traveler_id) FROM travelers), 1), true);
SELECT setval(pg_get_serial_sequence('payments', 'payment_id'),
              COALESCE((SELECT MAX(payment_id) FROM payments), 1), true);
SELECT setval(pg_get_serial_sequence('refunds', 'refund_id'),
              COALESCE((SELECT MAX(refund_id) FROM refunds), 1), true);

-- ============================================================
-- MIGRATION (sau khi da thay code doc tu departures):
-- INSERT INTO tour_departures (departure_id, tour_id, departure_start_date, departure_end_date, departure_capacity, departure_booked_slots, departure_price, departure_meeting_point, departure_status)
-- SELECT 'DEPA' || LPAD(ROW_NUMBER() OVER (ORDER BY tour_id)::text, 6, '0'), tour_id, tour_start_date, tour_end_date,
--        tour_remaining_slots, 0, tour_price, tour_meeting_point, 'OPEN' FROM tours;
-- ALTER TABLE tours DROP COLUMN tour_remaining_slots, drop column tour_start_date,
--   drop column tour_end_date, drop column tour_price, drop column tour_meeting_point;
-- ============================================================
