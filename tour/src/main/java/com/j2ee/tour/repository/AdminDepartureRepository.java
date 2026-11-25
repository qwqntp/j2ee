package com.j2ee.tour.repository;

import com.j2ee.tour.dto.response.AdminDepartureItem;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Locale;

@Repository
public class AdminDepartureRepository {
    private final EntityManager em;

    public AdminDepartureRepository(EntityManager em) {
        this.em = em;
    }

    public Page<AdminDepartureItem> search(Pageable pageable, boolean upcomingOnly, String status,
                                         String keyword, LocalDateTime now) {
        String filters = (upcomingOnly ? " AND d.departure_start_date > :now" : "")
                + (status == null ? "" : " AND d.departure_status = :status")
                + (keyword == null ? "" : " AND lower(t.tour_name) LIKE :keyword ESCAPE '!'");
        String order = "start_date " + (upcomingOnly ? "ASC" : "DESC") + ", id DESC";
        // Count and page travel in one round-trip. LEFT JOIN keeps the count even when OFFSET
        // is beyond the last row, unlike a bare COUNT(*) OVER() projection.
        String sql = """
                WITH filtered AS (
                    SELECT d.departure_id AS id, t.tour_id, t.tour_name,
                           d.departure_start_date AS start_date, d.departure_end_date AS end_date,
                           d.departure_status AS status, d.departure_capacity AS capacity,
                           d.departure_booked_slots AS booked,
                           d.departure_capacity - d.departure_booked_slots AS remaining
                    FROM tour_departures d JOIN tours t ON t.tour_id = d.tour_id
                    WHERE 1 = 1 %s
                ), paged AS (
                    SELECT * FROM filtered ORDER BY %s LIMIT :limit OFFSET :offset
                )
                SELECT p.id, p.tour_id, p.tour_name, p.start_date, p.end_date, p.status,
                       p.capacity, p.booked, p.remaining, totals.total
                FROM (SELECT count(*) AS total FROM filtered) totals
                LEFT JOIN paged p ON true
                ORDER BY %s
                """.formatted(filters, order, order);
        var query = em.createNativeQuery(sql);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());
        if (upcomingOnly) query.setParameter("now", now);
        if (status != null) query.setParameter("status", status);
        if (keyword != null) query.setParameter("keyword", "%" + keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");

        var items = new ArrayList<AdminDepartureItem>();
        long total = 0;
        for (Object raw : query.getResultList()) {
            Object[] row = (Object[]) raw;
            total = ((Number) row[9]).longValue();
            if (row[0] == null) continue;
            items.add(new AdminDepartureItem(((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue(), (String) row[2], date(row[3]), date(row[4]),
                    (String) row[5], ((Number) row[6]).intValue(), ((Number) row[7]).intValue(),
                    ((Number) row[8]).intValue()));
        }
        return new PageImpl<>(items, pageable, total);
    }

    private static LocalDateTime date(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }
}
