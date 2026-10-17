-- ============================================================
-- QUANLYBANTOUR - FULL SCHEMA + SEED (PostgreSQL)
-- 1) 15 bang hien co (tach DDL tu seed MySQL cu)
-- 2) 5 bang booking floor moi: tour_departures, bookings, travelers, payments, refunds
-- 3) Seed du lieu cu + seed demo book/pay/manifest
-- Chen by chay:  psql -U <user> -d quanlybantour -f quanlybantour_booking_floor.sql
-- ============================================================

SET client_encoding = 'UTF8';

-- ============================================================
-- PHAN 1: BANG HIEN CO
-- ============================================================

CREATE TABLE account_roles (
    account_role_id VARCHAR(10)  PRIMARY KEY,
    role_name       VARCHAR(50)  NOT NULL
);

CREATE TABLE accounts (
    account_id         VARCHAR(10)  PRIMARY KEY,
    username           VARCHAR(50)  NOT NULL UNIQUE,
    password           VARCHAR(255) NOT NULL,                      -- bcrypt
    account_email      VARCHAR(255) NULL,                          -- seed khong nap; signup/OAuth can cot nay
    email_verified     SMALLINT     NOT NULL DEFAULT 0,
    status             SMALLINT     NOT NULL DEFAULT 1,
    account_created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    account_updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    role_id            VARCHAR(10)  NOT NULL,
    CONSTRAINT fk_account_role FOREIGN KEY (role_id) REFERENCES account_roles(account_role_id)
);

-- MySQL ON UPDATE NOW() tuong duong trong PG: trigger
CREATE OR REPLACE FUNCTION set_account_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.account_updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_accounts_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION set_account_updated_at();

CREATE TABLE accommodations (
    accommodation_id   VARCHAR(10)   PRIMARY KEY,
    accommodation_name VARCHAR(255)  NOT NULL,
    location           VARCHAR(255)  NOT NULL,
    rating             DECIMAL(3,1)  NULL,
    price_per_night    DECIMAL(12,2) NULL,
    accommodation_type VARCHAR(20)   NULL                      -- HOTEL / RESORT / HOMESTAY
);

CREATE TABLE travel_vehicles (
    vehicle_id           VARCHAR(10)   PRIMARY KEY,
    vehicle_type         VARCHAR(255)  NOT NULL,
    capacity             INT           NOT NULL,
    rental_price_per_day DECIMAL(12,2) NOT NULL
);

CREATE TABLE tour_guides (
    tour_guide_id               VARCHAR(10)  PRIMARY KEY,
    tour_guide_name             VARCHAR(255) NOT NULL,
    tour_guide_email            VARCHAR(255) NULL,
    tour_guide_phone            VARCHAR(20)  NULL,
    tour_guide_experience_years INT          NOT NULL DEFAULT 0
);

CREATE TABLE tourist_destinations (
    destination_id   VARCHAR(10)   PRIMARY KEY,
    destination_name VARCHAR(255)  NOT NULL,
    location         VARCHAR(255)  NULL,
    description      TEXT          NULL,
    entry_fee        DECIMAL(12,2) NULL
);

CREATE TABLE promotions (
    promotion_id        VARCHAR(10)  PRIMARY KEY,
    promotion_name      VARCHAR(255) NOT NULL,
    discount_percentage DECIMAL(5,2) NOT NULL,
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    description         TEXT         NULL
);

CREATE TABLE tours (
    tour_id             VARCHAR(10)   PRIMARY KEY,
    tour_name           VARCHAR(255)  NOT NULL,
    tour_description    TEXT          NULL,                       -- itinerary gan bang '|', chua tach rieng
    tour_price          DECIMAL(12,2) NOT NULL,                   -- sau migration: doc gia tu tour_departures
    tour_status         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',   -- DRAFT/OPEN/CLOSED/CANCELLED
    tour_start_date     TIMESTAMP     NULL,
    tour_end_date       TIMESTAMP     NULL,
    tour_meeting_point  VARCHAR(255)  NULL,
    tour_remaining_slots INT          NULL,
    accommodation_id    VARCHAR(10)   NULL,
    tour_guide_id       VARCHAR(10)   NULL,
    CONSTRAINT fk_tour_accommodation FOREIGN KEY (accommodation_id) REFERENCES accommodations(accommodation_id),
    CONSTRAINT fk_tour_guide         FOREIGN KEY (tour_guide_id)   REFERENCES tour_guides(tour_guide_id)
);

CREATE INDEX idx_tour_status ON tours (tour_status);

CREATE TABLE tour_images (
    image_id  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    image_url VARCHAR(512) NOT NULL,
    tour_id   VARCHAR(10)  NOT NULL,
    CONSTRAINT fk_image_tour FOREIGN KEY (tour_id) REFERENCES tours(tour_id)
);

CREATE INDEX idx_image_tour ON tour_images (tour_id);

CREATE TABLE tour_destinations (
    tour_id        VARCHAR(10) NOT NULL,
    destination_id VARCHAR(10) NOT NULL,
    PRIMARY KEY (tour_id, destination_id),
    CONSTRAINT fk_td_tour        FOREIGN KEY (tour_id)        REFERENCES tours(tour_id),
    CONSTRAINT fk_td_destination FOREIGN KEY (destination_id) REFERENCES tourist_destinations(destination_id)
);

CREATE TABLE tour_vehicles (
    tour_id    VARCHAR(10) NOT NULL,
    vehicle_id VARCHAR(10) NOT NULL,
    PRIMARY KEY (tour_id, vehicle_id),
    CONSTRAINT fk_tv_tour    FOREIGN KEY (tour_id)    REFERENCES tours(tour_id),
    CONSTRAINT fk_tv_vehicle FOREIGN KEY (vehicle_id) REFERENCES travel_vehicles(vehicle_id)
);

CREATE TABLE tour_promotions (
    tour_id      VARCHAR(10) NOT NULL,
    promotion_id VARCHAR(10) NOT NULL,
    PRIMARY KEY (tour_id, promotion_id),
    CONSTRAINT fk_tp_tour      FOREIGN KEY (tour_id)      REFERENCES tours(tour_id),
    CONSTRAINT fk_tp_promotion FOREIGN KEY (promotion_id) REFERENCES promotions(promotion_id)
);

CREATE TABLE invoices (
    invoice_id         VARCHAR(10)   PRIMARY KEY,
    invoice_created_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    status             VARCHAR(20)   NOT NULL,                   -- PAID / UNPAID (doc cu)
    number_of_people   INT           NOT NULL,
    discount_amount    DECIMAL(12,2) NOT NULL DEFAULT 0,
    tax_amount         DECIMAL(12,2) NOT NULL DEFAULT 0,
    service_fee        DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount       DECIMAL(12,2) NOT NULL,
    payment_method     VARCHAR(20)   NULL,
    customer_name      VARCHAR(255)  NULL,                       -- snapshot
    customer_phone     VARCHAR(20)   NULL,
    customer_email     VARCHAR(255)  NULL,
    account_id         VARCHAR(10)   NULL,
    tour_id            VARCHAR(10)   NOT NULL,
    CONSTRAINT fk_invoice_account FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    CONSTRAINT fk_invoice_tour    FOREIGN KEY (tour_id)    REFERENCES tours(tour_id)
);

CREATE INDEX idx_invoice_account ON invoices (account_id);

CREATE TABLE invoice_promotions (
    invoice_id   VARCHAR(10) NOT NULL,
    promotion_id VARCHAR(10) NOT NULL,
    PRIMARY KEY (invoice_id, promotion_id),
    CONSTRAINT fk_ip_invoice   FOREIGN KEY (invoice_id)   REFERENCES invoices(invoice_id),
    CONSTRAINT fk_ip_promotion FOREIGN KEY (promotion_id) REFERENCES promotions(promotion_id)
);

CREATE TABLE customers (
    customer_id            VARCHAR(10)  PRIMARY KEY,
    customer_name          VARCHAR(255) NOT NULL,
    customer_email         VARCHAR(255) NULL,
    customer_phone         VARCHAR(20)  NULL,
    customer_address       VARCHAR(255) NULL,
    customer_date_of_birth DATE         NULL,
    account_id             VARCHAR(10)  NULL UNIQUE,
    CONSTRAINT fk_customer_account FOREIGN KEY (account_id) REFERENCES accounts(account_id)
);

-- ============================================================
-- PHAN 2: BOOKING FLOOR (5 bang moi)
-- ============================================================

-- A. TOUR_DEPARTURES - lich khoi han theo tung ngay cua tour
-- Tour giu thong tin san pham (ten, mo ta, anh); ngay di, so cho, gia nam day.
-- Bookings tro vao Departure, KHONG tro vao Tour.
CREATE TABLE tour_departures (
    departure_id            VARCHAR(10)   PRIMARY KEY,
    tour_id                 VARCHAR(10)   NOT NULL,
    departure_start_date    TIMESTAMP     NOT NULL,
    departure_end_date      TIMESTAMP     NOT NULL,
    departure_capacity      INT           NOT NULL,
    departure_booked_slots  INT           NOT NULL DEFAULT 0,
    departure_price         DECIMAL(12,2) NOT NULL,
    departure_meeting_point VARCHAR(255)  NULL,
    departure_status        VARCHAR(20)   NOT NULL DEFAULT 'OPEN',  -- OPEN / FULL / CLOSED / CANCELLED
    departure_created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_departure_tour FOREIGN KEY (tour_id) REFERENCES tours(tour_id),
    CONSTRAINT ck_departure_capacity CHECK (departure_booked_slots <= departure_capacity)
);

CREATE INDEX idx_departure_tour ON tour_departures (tour_id);
CREATE INDEX idx_departure_status_date ON tour_departures (departure_status, departure_start_date);

-- B. BOOKINGS - dat cho (header cua don)
CREATE TABLE bookings (
    booking_id              VARCHAR(10)   PRIMARY KEY,
    booking_code            VARCHAR(16)   NOT NULL UNIQUE,            -- 'BK-YYYYMMDD-0001'
    account_id              VARCHAR(10)   NULL,                       -- khach da dang nhap; NULL = guest
    departure_id            VARCHAR(10)   NOT NULL,
    booking_created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    booking_guest_count     INT           NOT NULL DEFAULT 1,
    booking_unit_price      DECIMAL(12,2) NOT NULL,                   -- snapshot gia tai thoi diem dat
    booking_subtotal        DECIMAL(12,2) NOT NULL,
    booking_discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    booking_total_amount    DECIMAL(12,2) NOT NULL,
    booking_status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING', -- PENDING/PAID/CONFIRMED/COMPLETED/CANCELLED
    booking_note            TEXT          NULL,
    CONSTRAINT fk_booking_account   FOREIGN KEY (account_id)   REFERENCES accounts(account_id),
    CONSTRAINT fk_booking_departure FOREIGN KEY (departure_id) REFERENCES tour_departures(departure_id)
);

CREATE INDEX idx_booking_departure ON bookings (departure_id);
CREATE INDEX idx_booking_account ON bookings (account_id);
CREATE INDEX idx_booking_status ON bookings (booking_status);

-- C. TRAVELERS - danh sach khach di thuc te (guest manifest)
-- 1 booking N travelers; khong phan loai tre em/nguoi lon
CREATE TABLE travelers (
    traveler_id            VARCHAR(10)  PRIMARY KEY,
    booking_id             VARCHAR(10)  NOT NULL,
    traveler_name          VARCHAR(255) NOT NULL,
    traveler_date_of_birth DATE         NULL,
    traveler_gender        VARCHAR(10)  NULL,                       -- MALE / FEMALE / OTHER
    traveler_id_document   VARCHAR(64)  NULL,                       -- so CCCD / ho chieu
    CONSTRAINT fk_traveler_booking FOREIGN KEY (booking_id) REFERENCES bookings(booking_id)
);

CREATE INDEX idx_traveler_booking ON travelers (booking_id);

-- D. PAYMENTS - cac lan ghi nhan thanh toan (that bai cung giu lai de audit)
-- Booking PAID khi SUM(payment SUCCESS) >= booking_total_amount
CREATE TABLE payments (
    payment_id              VARCHAR(10)   PRIMARY KEY,
    booking_id              VARCHAR(10)   NOT NULL,
    payment_amount          DECIMAL(12,2) NOT NULL,
    payment_method          VARCHAR(20)   NOT NULL,                   -- CASH / VNPAY / MOMO / CARD
    payment_provider_txn_id VARCHAR(128)  NULL,                       -- ma giao dich gateway; NULL voi CASH
    payment_status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING', -- PENDING / SUCCESS / FAILED
    payment_paid_at         TIMESTAMP     NULL,
    payment_created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES bookings(booking_id)
);

CREATE INDEX idx_payment_booking ON payments (booking_id);
CREATE INDEX idx_payment_status ON payments (payment_status);

-- E. REFUNDS - hoan tien (partial duoc phep)
CREATE TABLE refunds (
    refund_id           VARCHAR(10)   PRIMARY KEY,
    payment_id          VARCHAR(10)   NOT NULL,
    booking_id          VARCHAR(10)   NOT NULL,                   -- de truy van nhanh
    refund_amount       DECIMAL(12,2) NOT NULL,
    refund_reason       TEXT          NULL,
    refund_status       VARCHAR(20)   NOT NULL DEFAULT 'REQUESTED', -- REQUESTED/APPROVED/REJECTED/DONE
    refund_requested_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    refund_processed_at TIMESTAMP     NULL,
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(payment_id),
    CONSTRAINT fk_refund_booking FOREIGN KEY (booking_id) REFERENCES bookings(booking_id)
);

CREATE INDEX idx_refund_booking ON refunds (booking_id);

-- F. INVOICES bang hien co khong doi bat buoc. Neu muon bind doc hoa don vao don cu the:
-- ALTER TABLE invoices ADD COLUMN booking_id VARCHAR(10),
--   ADD CONSTRAINT fk_invoice_booking FOREIGN KEY (booking_id) REFERENCES bookings(booking_id);

-- ============================================================
-- SEED DU LIEU (thu tu bang cha -> bang con, khong can tat FK)
-- ============================================================

-- A. ROLES
INSERT INTO account_roles (account_role_id, role_name) VALUES
('ROLE000001', 'ROLE_ADMIN'),
('ROLE000002', 'ROLE_USER'),
('ROLE000003', 'ROLE_STAFF');

-- B. ACCOMMODATIONS
INSERT INTO accommodations (accommodation_id, accommodation_name, location, rating, price_per_night, accommodation_type) VALUES
('ACCO000001', 'Khách sạn Mường Thanh Grand', '962 Ngô Quyền, Sơn Trà, Đà Nẵng', 4.5, 1200000, 'HOTEL'),
('ACCO000002', 'Vinpearl Resort & Spa', 'Đảo Hòn Tre, Nha Trang', 5.0, 3500000, 'RESORT'),
('ACCO000003', 'Homestay Cánh Đồng Hoa', 'Phường 8, Đà Lạt', 4.0, 500000, 'HOMESTAY');

-- C. VEHICLES
INSERT INTO travel_vehicles (vehicle_id, vehicle_type, capacity, rental_price_per_day) VALUES
('VEHI000001', 'Xe Giường Nằm Thaco', 40, 3000000),
('VEHI000002', 'Xe Limousine Dcar 9 chỗ', 9, 2500000),
('VEHI000003', 'Tàu Cao Tốc Superdong', 100, 5000000);

-- D. GUIDES
INSERT INTO tour_guides (tour_guide_id, tour_guide_name, tour_guide_email, tour_guide_phone, tour_guide_experience_years) VALUES
('GUID000001', 'Nguyễn Văn Hướng', 'huong.nv@tour.com', '0901234567', 5),
('GUID000002', 'Trần Thị Dẫn', 'dan.tt@tour.com', '0909888777', 3);

-- E. DESTINATIONS
INSERT INTO tourist_destinations (destination_id, destination_name, location, description, entry_fee) VALUES
('DEST000001', 'Phố Cổ Hội An', 'Quảng Nam', 'Di sản văn hóa thế giới.', 120000),
('DEST000002', 'Bà Nà Hills', 'Đà Nẵng', 'Đường lên tiên cảnh.', 900000),
('DEST000003', 'Tháp Bà Ponagar', 'Nha Trang', 'Di tích Chăm Pa.', 30000);

-- F. PROMOTIONS
INSERT INTO promotions (promotion_id, promotion_name, discount_percentage, start_date, end_date, description) VALUES
('PROM000001', 'Chào Hè 2026', 10.0, '2026-05-01', '2026-08-31', 'Giảm giá mùa hè tương lai.'),
('PROM000002', 'Black Friday', 20.0, '2026-11-20', '2026-11-30', 'Sale cuối năm tương lai.'),
('PROM000003', 'Siêu Sale Tháng 11', 15.0, '2025-11-01', '2025-12-31', 'Khuyến mãi test chức năng.');

-- G. ACCOUNTS (Pass: 123456)
INSERT INTO accounts (account_id, username, password, email_verified, status, account_created_at, account_updated_at, role_id) VALUES
('ACCT000001', 'user01', '$2a$10$EnfdjqhOU9a7wzPUVFYENO3i6K..WMO1M92uKmhZnMReJjZl/rxr6', 1, 1, NOW(), NOW(), 'ROLE000002'),
('ACCT000002', 'user02', '$2a$10$EnfdjqhOU9a7wzPUVFYENO3i6K..WMO1M92uKmhZnMReJjZl/rxr6', 1, 1, NOW(), NOW(), 'ROLE000002');

-- H. CUSTOMERS
INSERT INTO customers (customer_id, customer_name, customer_email, customer_phone, customer_address, customer_date_of_birth, account_id) VALUES
('CUST000002', 'Nguyễn Văn Khách', 'khach01@gmail.com', '0911111111', 'TP.HCM', '1995-05-15', 'ACCT000002'),
('CUST000003', 'Lê Thị Mua', 'mua.le@gmail.com', '0922222222', 'Đà Nẵng', '2000-10-20', 'ACCT000001');

-- I. TOURS
INSERT INTO tours (tour_id, tour_name, tour_description, tour_price, tour_status, tour_start_date, tour_end_date, tour_meeting_point, tour_remaining_slots, accommodation_id, tour_guide_id) VALUES
('TOUR000001', 'Đà Nẵng - Hội An: Hành Trình Di Sản',
'Ngày 1: Xe đón quý khách tại sân bay Đà Nẵng, đưa về khách sạn Mường Thanh nhận phòng. Chiều tham quan Bán đảo Sơn Trà.|Ngày 2: Khởi hành đi Bà Nà Hills - Đường lên tiên cảnh. Check-in Cầu Vàng. Ăn trưa Buffet. Chiều về Hội An tham quan phố cổ.|Ngày 3: Mua sắm đặc sản Miền Trung tại chợ Hàn. Xe đưa quý khách ra sân bay.',
4500000, 'OPEN', '2026-06-15 08:00:00', '2026-06-17 17:00:00', 'Sân bay Đà Nẵng', 28, 'ACCO000001', 'GUID000001'),
('TOUR000002', 'Nha Trang Biển Gọi',
'Ngày 1: Đón khách tại sân bay Cam Ranh. Về trung tâm thành phố, nhận phòng Vinpearl Resort.|Ngày 2: Du ngoạn 4 đảo: Hòn Mun, Hòn Một, Bãi Tranh. Lặn ngắm san hô.|Ngày 3: Vui chơi không giới hạn tại VinWonders Nha Trang.|Ngày 4: Tắm bùn khoáng nóng I-Resort. Tiễn đoàn.',
6500000, 'OPEN', '2026-07-10 07:00:00', '2026-07-13 18:00:00', 'Sân bay Cam Ranh', 40, 'ACCO000002', 'GUID000002');

-- J. TOUR IMAGES
INSERT INTO tour_images (image_url, tour_id) VALUES
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764752418/tours/68a8ed00-a2ca-4c8c-8d94-ef65e878f805_40-trai-nghiem-du-thuyen-tren-song-hoai.png.png', 'TOUR000001'),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764752414/tours/86aeb11f-acdd-4bc9-a9b9-9d71e8a73766_hoi-an-1680591517857660432696.webp.webp', 'TOUR000001'),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764752414/tours/5ae3201b-903f-4090-828b-f1b02ed491c2_du-lich-da-nang-hoi-an-kinh-nghiem-cho-nguoi-moi-bat-dau-1.png.png', 'TOUR000001'),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764752413/tours/96bdc303-a1ef-43c5-8ed7-3a7b56cef264_tour-da-nang.jpg.jpg', 'TOUR000001'),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764751830/tours/1d0193d8-32e0-4144-8ff1-2f5f4f3e3611_adgahjd-1755152740753.jpg.jpg', 'TOUR000002'),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764751830/tours/ec62a7f6-c557-4c8f-9a9b-9d71e8a73766_Bien-nha-trang-3-jpg.webp.webp', 'TOUR000002'),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764751830/tours/db9f9072-4c8b-405f-8b84-2b93b882e3ea_nha-trang_1.jpg.jpg', 'TOUR000002'),
('https://res.cloudinary.com/dafcmtcp2/image/upload/v1764751830/tours/981214ff-327b-4f46-89ec-0263125e4d5b_kinh-nghiem-du-lich-nha-trang-2_1689411065.jpg.jpg', 'TOUR000002');

-- K. LIÊN KẾT BẢNG
INSERT INTO tour_destinations (tour_id, destination_id) VALUES
('TOUR000001', 'DEST000001'),
('TOUR000001', 'DEST000002'),
('TOUR000002', 'DEST000003');

INSERT INTO tour_vehicles (tour_id, vehicle_id) VALUES
('TOUR000001', 'VEHI000002'),
('TOUR000002', 'VEHI000001');

INSERT INTO tour_promotions (tour_id, promotion_id) VALUES
('TOUR000001', 'PROM000001'),
('TOUR000001', 'PROM000003');

-- L. INVOICES (du lieu cu - giu nguyen)
INSERT INTO invoices (invoice_id, invoice_created_at, status, number_of_people, discount_amount, tax_amount, total_amount, payment_method, customer_name, customer_phone, customer_email, account_id, tour_id, service_fee) VALUES
('INVO000001', NOW(), 'PAID', 2, 0, 0, 9000000, 'VNPAY', 'Nguyễn Văn Khách', '0911111111', 'khach01@gmail.com', 'ACCT000002', 'TOUR000001', 0),
('INVO000002', NOW(), 'UNPAID', 1, 0, 0, 4500000, 'CASH', 'Lê Thị Mua', '0922222222', 'mua.le@gmail.com', 'ACCT000002', 'TOUR000001', 0),
('INVO000003', NOW(), 'PAID', 4, 500000, 0, 25500000, 'VNPAY', 'Trần Văn Lạ', '0988777666', 'khach.la@gmail.com', NULL, 'TOUR000002', 0);

INSERT INTO invoice_promotions (invoice_id, promotion_id) VALUES ('INVO000003', 'PROM000001');

-- M. BOOKING FLOOR - DEPARTURES
INSERT INTO tour_departures (departure_id, tour_id, departure_start_date, departure_end_date,
    departure_capacity, departure_booked_slots, departure_price, departure_meeting_point, departure_status)
VALUES ('DEPA000001', 'TOUR000001', '2026-06-15 08:00:00', '2026-06-17 17:00:00', 30, 2, 4500000, 'Sân bay Đà Nẵng', 'OPEN');

-- N. BOOKING + MANIFEST + PAYMENT
INSERT INTO bookings (booking_id, booking_code, account_id, departure_id,
    booking_created_at, booking_guest_count, booking_unit_price, booking_subtotal,
    booking_discount_amount, booking_total_amount, booking_status, booking_note)
VALUES ('BOOK000001', 'BK-20260303-0001', 'ACCT000002', 'DEPA000001',
    NOW(), 2, 4500000, 9000000, 0, 9000000, 'PAID', 'Xin đón tại cổng A');

INSERT INTO travelers (traveler_id, booking_id, traveler_name, traveler_date_of_birth, traveler_gender, traveler_id_document) VALUES
('TRAV000001', 'BOOK000001', 'Nguyễn Văn Khách', '1995-05-15', 'MALE',   '012345678901'),
('TRAV000002', 'BOOK000001', 'Lê Thị Mua',        '2000-10-20', 'FEMALE', '098765432109');

INSERT INTO payments (payment_id, booking_id, payment_amount, payment_method,
    payment_provider_txn_id, payment_status, payment_paid_at)
VALUES ('PAYM000001', 'BOOK000001', 9000000, 'VNPAY', 'VNP_20260303_000123456', 'SUCCESS', NOW());

-- Refund ma vidu (kich hoat khi can test hoan tien):
-- INSERT INTO refunds (refund_id, payment_id, booking_id, refund_amount, refund_reason, refund_status)
-- VALUES ('REFU000001', 'PAYM000001', 'BOOK000001', 4500000, 'Khách xin hoàn một chỗ', 'REQUESTED');

-- ============================================================
-- MIGRATION (sau khi da thay code doc tu departures):
-- INSERT INTO tour_departures (departure_id, tour_id, departure_start_date, departure_end_date, departure_capacity, departure_booked_slots, departure_price, departure_meeting_point, departure_status)
-- SELECT 'DEPA' || LPAD(ROW_NUMBER() OVER (ORDER BY tour_id)::text, 6, '0'), tour_id, tour_start_date, tour_end_date,
--        tour_remaining_slots, 0, tour_price, tour_meeting_point, 'OPEN' FROM tours;
-- ALTER TABLE tours DROP COLUMN tour_remaining_slots, drop column tour_start_date,
--   drop column tour_end_date, drop column tour_price, drop column tour_meeting_point;
-- ============================================================
