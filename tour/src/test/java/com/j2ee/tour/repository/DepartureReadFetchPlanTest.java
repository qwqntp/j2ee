package com.j2ee.tour.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import static org.assertj.core.api.Assertions.assertThat;

class DepartureReadFetchPlanTest {
    @Test
    void departurePageFetchesItsTourInThePageQuery() throws Exception {
        EntityGraph graph = TourDepartureRepository.class.getMethod("findAll", Pageable.class)
                .getAnnotation(EntityGraph.class);
        assertThat(graph).isNotNull();
        assertThat(graph.attributePaths()).contains("tour");
    }

    @Test
    void adminTourPageFetchesTheToOneFieldsUsedByTheMapper() throws Exception {
        EntityGraph graph = TourRepository.class.getMethod("findAll", Pageable.class)
                .getAnnotation(EntityGraph.class);
        assertThat(graph).isNotNull();
        assertThat(graph.attributePaths()).contains("accommodation", "tourGuide");
    }
}
