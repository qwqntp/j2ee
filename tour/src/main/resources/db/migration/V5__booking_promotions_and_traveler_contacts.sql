-- Batch 1: promotion application and traveler contact details.
-- Additive only; existing rows keep their current behavior.

ALTER TABLE public.promotions
    ADD COLUMN IF NOT EXISTS promotion_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS usage_limit INT,
    ADD COLUMN IF NOT EXISTS used_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS min_order_amount DECIMAL(12,2);

CREATE UNIQUE INDEX IF NOT EXISTS uq_promotion_code
    ON public.promotions (promotion_code)
    WHERE promotion_code IS NOT NULL;

ALTER TABLE public.bookings
    ADD COLUMN IF NOT EXISTS promotion_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_booking_promotion
    ON public.bookings (promotion_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_booking_promotion'
          AND conrelid = 'public.bookings'::regclass
    ) THEN
        ALTER TABLE public.bookings
            ADD CONSTRAINT fk_booking_promotion
            FOREIGN KEY (promotion_id) REFERENCES public.promotions (promotion_id);
    END IF;
END $$;

ALTER TABLE public.travelers
    ADD COLUMN IF NOT EXISTS traveler_phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS traveler_email VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_promotion_usage'
          AND conrelid = 'public.promotions'::regclass
    ) THEN
        ALTER TABLE public.promotions
            ADD CONSTRAINT ck_promotion_usage
                CHECK (usage_limit IS NULL OR usage_limit >= 0),
            ADD CONSTRAINT ck_promotion_used_count
                CHECK (used_count >= 0),
            ADD CONSTRAINT ck_promotion_min_order
                CHECK (min_order_amount IS NULL OR min_order_amount >= 0);
    END IF;
END $$;
