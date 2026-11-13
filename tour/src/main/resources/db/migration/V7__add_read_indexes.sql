-- Indexes for the read paths used by the public catalogue, booking list, and auth flows.
-- Additive only; existing indexes are left in place.

-- Keyword search: Spring Data ContainingIgnoreCase generates lower(tour_name) LIKE '%...%'.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_tour_status_id
    ON public.tours (tour_status, tour_id);

CREATE INDEX IF NOT EXISTS idx_tour_name_trgm
    ON public.tours USING gin (lower(tour_name) gin_trgm_ops);

-- Upcoming departures and per-tour stats: tour_id IN (...), status IN (...), start_date > now.
CREATE INDEX IF NOT EXISTS idx_departure_tour_status_date
    ON public.tour_departures (tour_id, departure_status, departure_start_date);

-- First image per tour: tour_id IN (...) ORDER BY image_id.
CREATE INDEX IF NOT EXISTS idx_image_tour_image
    ON public.tour_images (tour_id, image_id);

-- "My trips": owner + newest first.
CREATE INDEX IF NOT EXISTS idx_booking_account_created
    ON public.bookings (account_id, booking_created_at DESC, booking_id DESC);

-- Payment sum and refund reservation lookups.
CREATE INDEX IF NOT EXISTS idx_payment_booking_status
    ON public.payments (booking_id, payment_status);

CREATE INDEX IF NOT EXISTS idx_refund_payment_status
    ON public.refunds (payment_id, refund_status);

-- One-time token invalidation by account and type.
CREATE INDEX IF NOT EXISTS idx_auth_token_account_type
    ON public.auth_tokens (account_id, token_type);
