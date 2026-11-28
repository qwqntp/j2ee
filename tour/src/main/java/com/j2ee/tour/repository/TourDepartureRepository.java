package com.j2ee.tour.repository;

import com.j2ee.tour.model.TourDeparture;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TourDepartureRepository extends JpaRepository<TourDeparture, Long> {

    @Override
    @EntityGraph(attributePaths = "tour")
    Page<TourDeparture> findAll(Pageable pageable);

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

    boolean existsByTourTourId(Long tourId);

    List<TourDeparture> findByTourTourIdAndDepartureStatusNot(Long tourId, String departureStatus);

    @Query("""
            select count(d) from TourDeparture d
            where d.tour.tourGuide.tourGuideId = :guideId
              and d.departureStatus <> 'CANCELLED'
              and d.departureStartDate < :end
              and d.departureEndDate > :start
              and (:excludeId is null or d.departureId <> :excludeId)
            """)
    long countGuideOverlap(@Param("guideId") Long guideId,
                           @Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end,
                           @Param("excludeId") Long excludeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select departure from TourDeparture departure where departure.departureId = :id")
    Optional<TourDeparture> findByIdForUpdate(@Param("id") Long id);

    @Query("select count(d) from TourDeparture d "
            + "where d.departureStartDate >= :from and d.departureStartDate < :to "
            + "and d.departureStatus in ('OPEN','FULL')")
    long countUpcomingOpen(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(d) from TourDeparture d "
            + "where d.departureStartDate >= :from and d.departureStartDate < :to "
            + "and d.departureCapacity - d.departureBookedSlots <= :remaining "
            + "and d.departureStatus in ('OPEN','FULL')")
    long countUpcomingCapacityAtMost(@Param("remaining") int remaining,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    @Query("select count(d) from TourDeparture d "
            + "where d.departureStartDate >= :from and d.departureStartDate < :to "
            + "and d.departureCapacity - d.departureBookedSlots <= 0 "
            + "and d.departureStatus in ('OPEN','FULL')")
    long countUpcomingSoldOut(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select d.departureId, d.tour.tourId, d.tour.tourName, d.departureStartDate, "
            + "d.departureCapacity, d.departureCapacity - d.departureBookedSlots, d.departureStatus "
            + "from TourDeparture d "
            + "where d.departureStartDate >= :from and d.departureStartDate < :to "
            + "and d.departureCapacity - d.departureBookedSlots <= :remaining "
            + "and d.departureStatus in ('OPEN','FULL') order by d.departureStartDate")
    List<Object[]> findCapacityRisks(@Param("from") LocalDateTime from,
                                     @Param("remaining") int remaining,
                                     @Param("to") LocalDateTime to);
}
