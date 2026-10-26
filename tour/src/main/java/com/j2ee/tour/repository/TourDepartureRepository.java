package com.j2ee.tour.repository;

import com.j2ee.tour.model.TourDeparture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TourDepartureRepository extends JpaRepository<TourDeparture, Long> {

    List<TourDeparture> findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
            Long tourId, Collection<String> statuses, LocalDateTime now);

    @Query("""
            select d.tour.tourId as tourId,
                   min(d.departurePrice) as priceFrom,
                   min(d.departureStartDate) as nextDepartureDate,
                   sum(case when d.departureStatus = 'OPEN'
                            then d.departureCapacity - d.departureBookedSlots
                            else 0 end) as seatsAvailable,
                   count(d) as departureCount
            from TourDeparture d
            where d.tour.tourId in :tourIds
              and d.departureStatus in ('OPEN', 'FULL')
              and d.departureStartDate > :now
            group by d.tour.tourId
            """)
    List<TourStatsProjection> findStatsByTourIds(@Param("tourIds") Collection<Long> tourIds,
                                                 @Param("now") LocalDateTime now);
}
