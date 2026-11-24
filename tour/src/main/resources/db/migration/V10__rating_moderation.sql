-- Moderate tour ratings before they appear publicly.

ALTER TABLE tour_ratings
    ADD COLUMN IF NOT EXISTS rating_status VARCHAR(16) NOT NULL DEFAULT 'PENDING';

-- Rows that predate moderation were already visible to the public.
UPDATE tour_ratings SET rating_status = 'APPROVED' WHERE rating_status = 'PENDING';

ALTER TABLE tour_ratings
    ADD CONSTRAINT ck_rating_status CHECK (rating_status IN ('PENDING', 'APPROVED', 'REJECTED'));

CREATE INDEX IF NOT EXISTS idx_rating_status_tour
    ON tour_ratings (rating_status, tour_id, rating_created_at DESC);
