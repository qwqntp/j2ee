package com.j2ee.tour;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.AdminBookingFilter;
import com.j2ee.tour.dto.response.AdminBookingListItem;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.model.Accommodation;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import com.j2ee.tour.repository.AccommodationRepository;
import com.j2ee.tour.repository.AdminBookingListRepository;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.service.AdminBookingQueryService;
import com.j2ee.tour.service.BookingService;
import com.j2ee.tour.service.PaymentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class AdminBookingListIntegrationTests {

    @Autowired private AdminBookingQueryService adminBookingQueryService;
    @Autowired private AdminBookingListRepository adminBookingListRepository;
    @Autowired private BookingService bookingService;
    @Autowired private PaymentService paymentService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private AccountRoleRepository accountRoleRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccommodationRepository accommodationRepository;
    @Autowired private TourGuideRepository tourGuideRepository;
    @Autowired private TourRepository tourRepository;
    @Autowired private TourDepartureRepository tourDepartureRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Account account;
    private TourDeparture departure;
    private String tourName;
    private AuthPrincipal ownerPrincipal;
    private Statistics statistics;

    @BeforeEach
    void setUp() {
        AccountRole role = new AccountRole();
        role.setRoleName("USER");
        role = accountRoleRepository.save(role);

        account = new Account();
        account.setUsername("al-" + UUID.randomUUID());
        account.setPassword("secret");
        account.setAccountEmail(account.getUsername() + "@example.com");
        account.setEmailVerified(1);
        account.setStatus(1);
        account.setAccountCreatedAt(LocalDateTime.now());
        account.setAccountUpdatedAt(LocalDateTime.now());
        account.setRole(role);
        account = accountRepository.save(account);
        ownerPrincipal = new AuthPrincipal(account.getAccountId(), account.getUsername(), "ROLE_USER");

        Accommodation accommodation = new Accommodation();
        accommodation.setAccommodationName("Hotel");
        accommodation.setLocation("HCM");
        accommodation = accommodationRepository.save(accommodation);

        TourGuide guide = new TourGuide();
        guide.setTourGuideName("Guide");
        guide.setTourGuideExperienceYears(1);
        guide = tourGuideRepository.save(guide);

        tourName = "Admin Booking List " + UUID.randomUUID();
        Tour tour = new Tour();
        tour.setTourName(tourName);
        tour.setTourStatus("OPEN");
        tour.setAccommodation(accommodation);
        tour.setTourGuide(guide);
        tour = tourRepository.save(tour);

        departure = new TourDeparture();
        departure.setTour(tour);
        departure.setDepartureStartDate(LocalDateTime.now().plusDays(1));
        departure.setDepartureEndDate(LocalDateTime.now().plusDays(3));
        departure.setDepartureCapacity(100);
        departure.setDepartureBookedSlots(0);
        departure.setDeparturePrice(new BigDecimal("500.00"));
        departure.setDepartureStatus("OPEN");
        departure.setDepartureCreatedAt(LocalDateTime.now());
        departure = tourDepartureRepository.save(departure);

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void pagingIsStableAndCountsUseAllMatches() {
        List<Long> ids = new ArrayList<>();
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        for (int i = 0; i < 25; i++) {
            BigDecimal amount = new BigDecimal(String.format("%d.00", 100 + (i % 5)));
            ids.add(createBooking("BK-" + String.format("%03d", i), createdAt, amount).getBookingId());
        }
        ids.sort(Comparator.reverseOrder());

        PageResponse<AdminBookingListItem> first = list(PageRequest.of(0, 10));
        PageResponse<AdminBookingListItem> second = list(PageRequest.of(1, 10));
        PageResponse<AdminBookingListItem> third = list(PageRequest.of(2, 10));
        PageResponse<AdminBookingListItem> empty = list(PageRequest.of(3, 10));

        assertEquals(10, first.content().size());
        assertEquals(10, second.content().size());
        assertEquals(5, third.content().size());
        assertEquals(0, empty.content().size());
        assertEquals(25, first.totalElements());
        assertEquals(3, first.totalPages());
        List<Long> actualIds = new ArrayList<>();
        actualIds.addAll(first.content().stream().map(AdminBookingListItem::id).toList());
        actualIds.addAll(second.content().stream().map(AdminBookingListItem::id).toList());
        actualIds.addAll(third.content().stream().map(AdminBookingListItem::id).toList());
        assertEquals(ids, actualIds);
        assertEquals(25, actualIds.stream().distinct().count());
    }

    @Test
    void amountSortUsesIdTieBreaker() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        List<Long> tiedIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tiedIds.add(createBooking("BK-TIE-" + i, createdAt, new BigDecimal("100.00")).getBookingId());
        }
        createBooking("BK-HIGH", createdAt, new BigDecimal("900.00"));
        tiedIds.sort(Comparator.reverseOrder());

        PageResponse<AdminBookingListItem> page = adminBookingQueryService.list(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "totalAmount")),
                new AdminBookingFilter(null, null, null, tourName));

        assertEquals(new BigDecimal("100.00"), page.content().get(0).totalAmount());
        assertEquals(tiedIds, page.content().subList(0, 4).stream()
                .map(AdminBookingListItem::id).toList());
        assertEquals(new BigDecimal("900.00"), page.content().get(4).totalAmount());
    }

    @Test
    void invalidInputsAndLiteralSearch() {
        assertBadRequest(() -> list(PageRequest.of(0, 10, Sort.by("bookingNote"))));
        assertBadRequest(() -> adminBookingQueryService.list(
                PageRequest.of(0, 10),
                new AdminBookingFilter("INVALID", null, null, null)));
        assertBadRequest(() -> adminBookingQueryService.list(
                PageRequest.of(0, 10),
                new AdminBookingFilter(null, LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1), null)));

        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        String token = UUID.randomUUID().toString().substring(0, 6);
        createBooking("BK-" + token + "%-X", createdAt, new BigDecimal("100.00"));
        createBooking("BK-" + token + "_X", createdAt, new BigDecimal("100.00"));
        createBooking("BK-" + token + "\\X", createdAt, new BigDecimal("100.00"));
        createBooking("BK-" + token + "PX", createdAt, new BigDecimal("100.00"));

        assertCodes(token + "%", "BK-" + token + "%-X");
        assertCodes(token + "_", "BK-" + token + "_X");
        assertCodes(token + "\\", "BK-" + token + "\\X");
        assertEquals(4, adminBookingQueryService.list(
                PageRequest.of(0, 10),
                new AdminBookingFilter(null, null, null, "  " + tourName.toUpperCase() + "  "))
                .totalElements());
    }

    @Test
    void sizeClampAppliesBeforeOffset() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            ids.add(createBooking("BK-CLAMP-" + String.format("%03d", i), createdAt,
                    new BigDecimal("100.00")).getBookingId());
        }
        ids.sort(Comparator.reverseOrder());

        AdminBookingQueryService capped = new AdminBookingQueryService(adminBookingListRepository, 3);
        PageResponse<AdminBookingListItem> page = capped.list(
                PageRequest.of(1, 100),
                new AdminBookingFilter(null, null, null, tourName));

        assertEquals(3, page.size());
        assertEquals(1, page.page());
        assertEquals(25, page.totalElements());
        assertEquals(9, page.totalPages());
        assertEquals(ids.subList(3, 6), page.content().stream().map(AdminBookingListItem::id).toList());
    }

    @Test
    void listSqlDoesNotGrowWithPageSize() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        for (int i = 0; i < 25; i++) {
            createBooking("BK-SQL-" + String.format("%03d", i), createdAt, new BigDecimal("100.00"));
        }
        flushAndClear();

        assertPreparedStatementsAtMost(2, () -> list(PageRequest.of(0, 1)));
        flushAndClear();
        assertPreparedStatementsAtMost(2, () -> list(PageRequest.of(0, 10)));
        flushAndClear();
        assertPreparedStatementsAtMost(2, () -> list(PageRequest.of(0, 20)));
    }

    @Test
    void existingDetailReadsRemainAvailable() {
        Booking booking = createBooking("BK-DETAIL", LocalDateTime.of(2026, 1, 1, 12, 0),
                new BigDecimal("100.00"));
        booking.setBookingNote("Keep this note");
        bookingRepository.save(booking);

        var ownerView = bookingService.get(booking.getBookingId(), ownerPrincipal);
        var adminView = bookingService.get(booking.getBookingId(),
                new AuthPrincipal(account.getAccountId() + 1, "admin", "ROLE_ADMIN"));

        assertEquals("Keep this note", ownerView.note());
        assertEquals("Keep this note", adminView.note());
        assertEquals(booking.getBookingId(), adminView.id());
        assertEquals(0, paymentService.findByBooking(booking.getBookingId()).size());
        ApiException missing = assertThrows(ApiException.class, () -> bookingService.get(
                booking.getBookingId(),
                new AuthPrincipal(account.getAccountId() + 2, "other", "ROLE_USER")));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatus());
    }

    private PageResponse<AdminBookingListItem> list(PageRequest pageable) {
        return adminBookingQueryService.list(pageable,
                new AdminBookingFilter(null, null, null, tourName));
    }

    private void assertCodes(String query, String expectedCode) {
        PageResponse<AdminBookingListItem> page = adminBookingQueryService.list(
                PageRequest.of(0, 10),
                new AdminBookingFilter(null, null, null, query));
        assertEquals(1, page.totalElements());
        assertEquals(expectedCode, page.content().get(0).bookingCode());
    }

    private void assertBadRequest(org.junit.jupiter.api.function.Executable executable) {
        ApiException error = assertThrows(ApiException.class, executable);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
    }

    private void assertPreparedStatementsAtMost(long expected, Runnable call) {
        call.run();
        assertTrue(statistics.getPrepareStatementCount() <= expected,
                "Expected at most " + expected + " statements but was "
                        + statistics.getPrepareStatementCount());
    }

    private Booking createBooking(String code, LocalDateTime createdAt, BigDecimal total) {
        Booking booking = new Booking();
        booking.setBookingCode(code);
        booking.setBookingCreatedAt(createdAt);
        booking.setBookingGuestCount(1);
        booking.setBookingUnitPrice(total);
        booking.setBookingSubtotal(total);
        booking.setBookingDiscountAmount(BigDecimal.ZERO);
        booking.setBookingTotalAmount(total);
        booking.setBookingStatus("PENDING");
        booking.setAccount(account);
        booking.setDeparture(departure);
        return bookingRepository.save(booking);
    }
}
