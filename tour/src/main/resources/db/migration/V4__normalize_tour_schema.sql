-- Tours no longer store dates, capacity, or price; those live on tour_departures.
ALTER TABLE public.tours
    ADD COLUMN IF NOT EXISTS tour_duration_days INT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'tours'
          AND column_name = 'tour_start_date'
    ) THEN
        EXECUTE $sql$
            UPDATE public.tours
            SET tour_duration_days = GREATEST(1, (tour_end_date::date - tour_start_date::date))
            WHERE tour_duration_days IS NULL
              AND tour_start_date IS NOT NULL
              AND tour_end_date IS NOT NULL
        $sql$;

        EXECUTE $sql$
            INSERT INTO public.tour_departures (
                tour_id, departure_start_date, departure_end_date, departure_capacity,
                departure_booked_slots, departure_price, departure_meeting_point, departure_status)
            SELECT t.tour_id, t.tour_start_date, t.tour_end_date,
                   GREATEST(COALESCE(t.tour_remaining_slots, 1), 1),
                   0, t.tour_price, t.tour_meeting_point, 'OPEN'
            FROM public.tours t
            WHERE t.tour_start_date IS NOT NULL
              AND t.tour_end_date > t.tour_start_date
              AND t.tour_price IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1 FROM public.tour_departures d WHERE d.tour_id = t.tour_id
              )
        $sql$;
    END IF;
END $$;

ALTER TABLE public.tours
    DROP COLUMN IF EXISTS tour_price,
    DROP COLUMN IF EXISTS tour_start_date,
    DROP COLUMN IF EXISTS tour_end_date,
    DROP COLUMN IF EXISTS tour_meeting_point,
    DROP COLUMN IF EXISTS tour_remaining_slots;
