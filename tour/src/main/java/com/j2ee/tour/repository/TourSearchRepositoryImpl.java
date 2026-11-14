package com.j2ee.tour.repository;

import com.j2ee.tour.dto.request.TourSearchCriteria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public class TourSearchRepositoryImpl implements TourSearchRepository {

    private static final String UPCOMING_JOIN = """
            left join TourDeparture sortDeparture
              on sortDeparture.tour = t
             and sortDeparture.departureStatus in ('OPEN', 'FULL')
             and sortDeparture.departureStartDate > :now
            """;

    private final EntityManager entityManager;

    public TourSearchRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<Long> searchTourIds(TourSearchCriteria criteria,
                                    LocalDateTime now,
                                    Pageable pageable) {
        String where = where(criteria);
        boolean aggregateSort = criteria.sort() == TourSearchCriteria.Sort.PRICE_ASC
                || criteria.sort() == TourSearchCriteria.Sort.PRICE_DESC
                || criteria.sort() == TourSearchCriteria.Sort.DATE_ASC;

        String jpql = "select t.tourId from Tour t "
                + (aggregateSort ? UPCOMING_JOIN : "")
                + where
                + orderBy(criteria.sort());

        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        bind(query, criteria, now, aggregateSort);
        query.setFirstResult(Math.toIntExact(pageable.getOffset()));
        query.setMaxResults(pageable.getPageSize());
        List<Long> ids = query.getResultList();

        // The count query has no join, so it only needs :now when the WHERE clause uses it.
        TypedQuery<Long> countQuery = entityManager.createQuery(
                "select count(t.tourId) from Tour t " + where, Long.class);
        bind(countQuery, criteria, now, false);
        long total = countQuery.getSingleResult();

        return new PageImpl<>(ids, pageable, total);
    }

    /**
     * Tour-level filters plus a single {@code exists} for departure filters. One upcoming
     * departure must satisfy every supplied price/date predicate, so a tour drops out only
     * when no single departure matches all of them.
     */
    private String where(TourSearchCriteria criteria) {
        StringBuilder where = new StringBuilder(" where t.tourStatus = :status");

        if (criteria.keyword() != null) {
            where.append(" and lower(t.tourName) like lower(concat('%', :keyword, '%'))");
        }
        if (criteria.destinationId() != null) {
            where.append(" and exists (select dest.destinationId from t.destinations dest")
                    .append(" where dest.destinationId = :destinationId)");
        }
        if (criteria.minDays() != null) {
            where.append(" and t.tourDurationDays >= :minDays");
        }
        if (criteria.maxDays() != null) {
            where.append(" and t.tourDurationDays <= :maxDays");
        }
        if (criteria.hasDepartureFilter()) {
            where.append(" and exists (select dep.departureId from TourDeparture dep")
                    .append(" where dep.tour = t")
                    .append(" and dep.departureStatus in ('OPEN', 'FULL')")
                    .append(" and dep.departureStartDate > :now");
            if (criteria.minPrice() != null) {
                where.append(" and dep.departurePrice >= :minPrice");
            }
            if (criteria.maxPrice() != null) {
                where.append(" and dep.departurePrice <= :maxPrice");
            }
            if (criteria.startFrom() != null) {
                where.append(" and dep.departureStartDate >= :startFrom");
            }
            if (criteria.startTo() != null) {
                where.append(" and dep.departureStartDate < :startTo");
            }
            where.append(")");
        }
        return where.toString();
    }

    private String orderBy(TourSearchCriteria.Sort sort) {
        return switch (sort) {
            case NEWEST -> " order by t.tourId desc";
            case PRICE_ASC -> " group by t.tourId"
                    + " order by min(sortDeparture.departurePrice) asc nulls last, t.tourId asc";
            case PRICE_DESC -> " group by t.tourId"
                    + " order by min(sortDeparture.departurePrice) desc nulls last, t.tourId asc";
            case DATE_ASC -> " group by t.tourId"
                    + " order by min(sortDeparture.departureStartDate) asc nulls last, t.tourId asc";
            case DEFAULT -> " order by t.tourId asc";
        };
    }

    private void bind(TypedQuery<?> query, TourSearchCriteria criteria, LocalDateTime now,
                      boolean aggregateSort) {
        query.setParameter("status", "OPEN");
        if (criteria.keyword() != null) {
            query.setParameter("keyword", criteria.keyword());
        }
        if (criteria.destinationId() != null) {
            query.setParameter("destinationId", criteria.destinationId());
        }
        if (criteria.minDays() != null) {
            query.setParameter("minDays", criteria.minDays());
        }
        if (criteria.maxDays() != null) {
            query.setParameter("maxDays", criteria.maxDays());
        }
        if (criteria.hasDepartureFilter()) {
            query.setParameter("now", now);
            if (criteria.minPrice() != null) {
                query.setParameter("minPrice", criteria.minPrice());
            }
            if (criteria.maxPrice() != null) {
                query.setParameter("maxPrice", criteria.maxPrice());
            }
            if (criteria.startFrom() != null) {
                // startFrom and startTo are inclusive calendar days.
                query.setParameter("startFrom", criteria.startFrom().atStartOfDay());
            }
            if (criteria.startTo() != null) {
                query.setParameter("startTo", criteria.startTo().plusDays(1).atStartOfDay());
            }
        }
        if (!criteria.hasDepartureFilter() && aggregateSort) {
            query.setParameter("now", now);
        }
    }
}
