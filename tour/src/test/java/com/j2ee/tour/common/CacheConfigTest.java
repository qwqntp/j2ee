package com.j2ee.tour.common;

import com.j2ee.tour.dto.cache.TourCardContent;
import com.j2ee.tour.dto.response.TourDepartureResponse;
import com.j2ee.tour.dto.response.TourResponse;
import com.j2ee.tour.dto.response.TourSummaryResponse;
import com.j2ee.tour.service.TourContentCache.TourCardCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.SerializationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheConfigTest {

    private final SerializationPair<Object> pair =
            SerializationPair.fromSerializer(CacheConfig.valueSerializer());

    @Test
    void roundTripsTourListPage() {
        var page = new PageResponse<>(
                List.of(new TourSummaryResponse(
                        1L,
                        "Da Nang - Hoi An",
                        "Ngay 1: Da Nang",
                        "OPEN",
                        "https://img/1.jpg",
                        new BigDecimal("4500000"),
                        LocalDateTime.of(2026, 11, 1, 8, 0),
                        28,
                        1)),
                0, 12, 1, 1, true, false, false);

        assertThat(pair.read(pair.write(page))).isEqualTo(page);
    }

    @Test
    void roundTripsTourDetail() {
        var departure = new TourDepartureResponse(
                1L,
                2L,
                LocalDateTime.of(2026, 11, 1, 8, 0),
                LocalDateTime.of(2026, 11, 3, 8, 0),
                30,
                2,
                28,
                new BigDecimal("4500000"),
                "Hoi An",
                "OPEN",
                LocalDateTime.of(2026, 10, 1, 8, 0));
        var tour = new TourResponse(
                2L,
                "Da Nang - Hoi An",
                "Ngay 1: Da Nang",
                "OPEN",
                "Beach Resort",
                new BigDecimal("4.5"),
                "RESORT",
                "Nguyen Van A",
                "0900000000",
                List.of("https://img/2.jpg"),
                List.of("Ngay 1: Da Nang", "Ngay 2: Hoi An"),
                List.of("Hoi An"),
                List.of("BUS"),
                List.of(departure),
                3,
                1L,
                1L,
                1L,
                List.of(1L),
                List.of(1L));

        assertThat(pair.read(pair.write(tour))).isEqualTo(tour);
    }

    @Test
    void roundTripsStableCatalogContent() {
        var card = new TourCardContent(1L, "Da Nang", "Ngay 1: Da Nang", "OPEN", null);
        var catalog = new TourCardCatalog(List.of(card,
                new TourCardContent(2L, "Hoi An", "Ngay 1: Hoi An", "OPEN", "https://img/2.jpg")));

        TourCardCatalog decoded = (TourCardCatalog) pair.read(pair.write(catalog));
        assertThat(decoded).isEqualTo(catalog);
        assertThatThrownBy(() -> decoded.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        // The provider cannot deserialize a top-level immutable List; the wrapper record can.
        var listOfJson = pair.write(List.of(card));
        assertThatThrownBy(() -> pair.read(listOfJson)).isInstanceOf(SerializationException.class);
        var streamJson = pair.write(Stream.of(card).toList());
        assertThatThrownBy(() -> pair.read(streamJson)).isInstanceOf(SerializationException.class);
    }

    @Test
    void catalogCopiesMutableItems() {
        List<TourCardContent> mutable = new ArrayList<>(
                List.of(new TourCardContent(1L, "Da Nang", "Ngay 1: Da Nang", "OPEN", null)));
        var catalog = new TourCardCatalog(mutable);

        mutable.clear();

        assertThat(catalog.items()).hasSize(1);
        assertThatThrownBy(() -> catalog.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void roundTripsDepartureFreeDetail() {
        var tour = new TourResponse(
                2L,
                "Da Nang - Hoi An",
                "Ngay 1: Da Nang",
                "OPEN",
                "Beach Resort",
                new BigDecimal("4.5"),
                "RESORT",
                "Nguyen Van A",
                "0900000000",
                List.of("https://img/2.jpg"),
                List.of("Ngay 1: Da Nang", "Ngay 2: Hoi An"),
                List.of("Hoi An"),
                List.of("BUS"),
                List.of(),
                3,
                1L,
                1L,
                1L,
                List.of(1L),
                List.of(1L));

        assertThat(pair.read(pair.write(tour))).isEqualTo(tour);
        assertThat(tour.departures()).isEmpty();
    }
}
