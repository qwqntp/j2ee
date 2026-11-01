package com.j2ee.tour.repository;

import com.j2ee.tour.model.TourImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TourImageRepository extends JpaRepository<TourImage, Long> {
    List<TourImage> findByTourTourId(Long tourId);

    @Query("""
            select ti.tour.tourId as tourId, ti.imageUrl as imageUrl
            from TourImage ti
            where ti.tour.tourId in :tourIds
            order by ti.imageId
            """)
    List<TourImageProjection> findImageUrlsByTourIds(@Param("tourIds") Collection<Long> tourIds);

    interface TourImageProjection {
        Long getTourId();

        String getImageUrl();
    }
}
