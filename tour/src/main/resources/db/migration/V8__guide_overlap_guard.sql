-- Backstop for guide double-booking: the service layer rejects overlapping
-- writes with a friendly 409 first; these triggers close the race window for
-- concurrent transactions and for any path that bypasses the services.
-- Windows compare as [start, end): back-to-back departures are allowed.
-- CANCELLED departures never occupy a guide.

CREATE OR REPLACE FUNCTION check_departure_guide_overlap() RETURNS trigger AS $$
DECLARE
    guide_id BIGINT;
BEGIN
    IF NEW.departure_status = 'CANCELLED' THEN
        RETURN NEW;
    END IF;

    SELECT t.tour_guide_id INTO guide_id
    FROM tours t
    WHERE t.tour_id = NEW.tour_id;

    IF guide_id IS NULL THEN
        RETURN NEW;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM tour_departures d
        JOIN tours t ON t.tour_id = d.tour_id
        WHERE t.tour_guide_id = guide_id
          AND d.departure_status <> 'CANCELLED'
          AND d.departure_id <> COALESCE(NEW.departure_id, -1)
          AND d.departure_start_date < NEW.departure_end_date
          AND d.departure_end_date   > NEW.departure_start_date
    ) THEN
        RAISE EXCEPTION 'Guide % already has an overlapping departure', guide_id
            USING ERRCODE = '23P01';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_departure_guide_overlap ON tour_departures;
CREATE TRIGGER trg_departure_guide_overlap
    BEFORE INSERT OR UPDATE ON tour_departures
    FOR EACH ROW EXECUTE FUNCTION check_departure_guide_overlap();

CREATE OR REPLACE FUNCTION check_tour_guide_overlap() RETURNS trigger AS $$
DECLARE
    dep RECORD;
BEGIN
    IF NEW.tour_guide_id IS NULL OR NEW.tour_guide_id = OLD.tour_guide_id THEN
        RETURN NEW;
    END IF;

    FOR dep IN
        SELECT departure_id, departure_start_date, departure_end_date
        FROM tour_departures
        WHERE tour_id = NEW.tour_id
          AND departure_status <> 'CANCELLED'
    LOOP
        IF EXISTS (
            SELECT 1
            FROM tour_departures d
            JOIN tours t ON t.tour_id = d.tour_id
            WHERE t.tour_guide_id = NEW.tour_guide_id
              AND d.departure_status <> 'CANCELLED'
              AND d.departure_id <> dep.departure_id
              AND d.departure_start_date < dep.departure_end_date
              AND d.departure_end_date   > dep.departure_start_date
        ) THEN
            RAISE EXCEPTION 'Guide % already has an overlapping departure', NEW.tour_guide_id
                USING ERRCODE = '23P01';
        END IF;
    END LOOP;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_tour_guide_overlap ON tours;
CREATE TRIGGER trg_tour_guide_overlap
    BEFORE UPDATE OF tour_guide_id ON tours
    FOR EACH ROW EXECUTE FUNCTION check_tour_guide_overlap();
