package com.j2ee.tour;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.response.AdminInvoiceListItem;
import com.j2ee.tour.dto.response.InvoiceResponse;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.model.Accommodation;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Invoice;
import com.j2ee.tour.model.Promotion;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import com.j2ee.tour.repository.AccommodationRepository;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.InvoiceRepository;
import com.j2ee.tour.repository.PromotionRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.service.InvoiceService;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class AdminInvoiceListIntegrationTests {

    @Autowired private InvoiceService invoiceService;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private AccountRoleRepository accountRoleRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccommodationRepository accommodationRepository;
    @Autowired private TourGuideRepository tourGuideRepository;
    @Autowired private TourRepository tourRepository;
    @Autowired private TourDepartureRepository tourDepartureRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private Clock clock;

    private Account account;
    private TourDeparture departure;
    private String token;
    private Statistics statistics;

    @BeforeEach
    void setUp() {
        AccountRole role = new AccountRole();
        role.setRoleName("USER");
        role = accountRoleRepository.save(role);

        account = new Account();
        account.setUsername("ai-" + UUID.randomUUID());
        account.setPassword("secret");
        account.setAccountEmail(account.getUsername() + "@example.com");
        account.setEmailVerified(1);
        account.setStatus(1);
        account.setAccountCreatedAt(LocalDateTime.now());
        account.setAccountUpdatedAt(LocalDateTime.now());
        account.setRole(role);
        account = accountRepository.save(account);

        Accommodation accommodation = new Accommodation();
        accommodation.setAccommodationName("Hotel");
        accommodation.setLocation("HCM");
        accommodation = accommodationRepository.save(accommodation);

        TourGuide guide = new TourGuide();
        guide.setTourGuideName("Guide");
        guide.setTourGuideExperienceYears(1);
        guide = tourGuideRepository.save(guide);

        Tour tour = new Tour();
        tour.setTourName("Invoice Tour " + UUID.randomUUID());
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

        token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void fullDatasetFiltersAndPaging() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 12, 0);
        List<Long> paidIds = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            paidIds.add(createInvoice(token + " " + i, "PAID", "CARD",
                    new BigDecimal(String.format("%d.00", 100 + (i % 3))), created).getInvoiceId());
        }
        for (int i = 0; i < 3; i++) {
            createInvoice(token + " unpaid " + i, "UNPAID", "CASH",
                    new BigDecimal("200.00"), created);
        }
        for (int i = 0; i < 3; i++) {
            createInvoice("other paid " + i, "PAID", "VNPAY",
                    new BigDecimal("300.00"), created);
        }
        paidIds.sort(Comparator.reverseOrder());

        PageResponse<AdminInvoiceListItem> first = list(PageRequest.of(0, 10), "PAID", token);
        PageResponse<AdminInvoiceListItem> second = list(PageRequest.of(1, 10), "PAID", token);
        PageResponse<AdminInvoiceListItem> third = list(PageRequest.of(2, 10), "PAID", token);

        assertEquals(10, first.content().size());
        assertEquals(1, second.content().size());
        assertEquals(0, third.content().size());
        assertEquals(11, first.totalElements());
        assertEquals(2, first.totalPages());
        assertEquals(3, list(PageRequest.of(0, 10), "UNPAID", token).totalElements());

        List<Long> actualIds = new ArrayList<>();
        actualIds.addAll(first.content().stream().map(AdminInvoiceListItem::id).toList());
        actualIds.addAll(second.content().stream().map(AdminInvoiceListItem::id).toList());
        assertEquals(paidIds, actualIds);
        assertEquals(11, actualIds.stream().distinct().count());
    }

    @Test
    void amountSortUsesIdTieBreaker() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 12, 0);
        List<Long> tiedIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tiedIds.add(createInvoice(token + " tie " + i, "PAID", "CARD",
                    new BigDecimal("100.00"), created).getInvoiceId());
        }
        createInvoice(token + " high", "PAID", "CARD", new BigDecimal("900.00"), created);
        tiedIds.sort(Comparator.reverseOrder());

        PageResponse<AdminInvoiceListItem> page = list(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "totalAmount")),
                "PAID", token);

        assertEquals(new BigDecimal("100.00"), page.content().get(0).totalAmount());
        assertEquals(tiedIds, page.content().subList(0, 4).stream()
                .map(AdminInvoiceListItem::id).toList());
        assertEquals(new BigDecimal("900.00"), page.content().get(4).totalAmount());
    }

    @Test
    void literalSearchAndValidation() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 12, 0);
        Invoice named = createInvoice(token + " name", "PAID", "CARD",
                new BigDecimal("100.00"), created);

        assertBadRequest(() -> list(PageRequest.of(0, 10), "INVALID", token));
        assertBadRequest(() -> list(PageRequest.of(0, 10, Sort.by("bookingNote")), "PAID", token));
        assertEquals(1, list(PageRequest.of(0, 10), "PAID",
                "  " + token.toUpperCase() + "  ").totalElements());

        String emailToken = "mail" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Invoice emailed = createInvoice("someone", "PAID", "CARD",
                new BigDecimal("100.00"), created);
        emailed.setCustomerEmail(emailToken + "@example.com");
        invoiceRepository.save(emailed);
        PageResponse<AdminInvoiceListItem> emailPage = list(PageRequest.of(0, 10), null, emailToken);
        assertEquals(1, emailPage.totalElements());
        assertEquals(emailed.getInvoiceId(), emailPage.content().get(0).id());

        Invoice momo = createInvoice(token + " momo", "PAID", "MOMO",
                new BigDecimal("100.00"), created);
        PageResponse<AdminInvoiceListItem> momoPage = list(
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "id")), null, "MOMO");
        assertTrue(momoPage.content().stream().anyMatch(item -> item.id().equals(momo.getInvoiceId())));

        PageResponse<AdminInvoiceListItem> byBooking = list(PageRequest.of(0, 50), null,
                String.valueOf(named.getBooking().getBookingId()));
        assertTrue(byBooking.content().stream().anyMatch(item -> item.id().equals(named.getInvoiceId())));

        Invoice percent = createInvoice(token + "%-X", "PAID", "CARD",
                new BigDecimal("100.00"), created);
        Invoice underscore = createInvoice(token + "_X", "PAID", "CARD",
                new BigDecimal("100.00"), created);
        Invoice backslash = createInvoice(token + "\\X", "PAID", "CARD",
                new BigDecimal("100.00"), created);
        createInvoice(token + "PX", "PAID", "CARD", new BigDecimal("100.00"), created);

        assertCodes(token + "%", percent);
        assertCodes(token + "_", underscore);
        assertCodes(token + "\\", backslash);
    }

    @Test
    void clampControlsOffset() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 12, 0);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            ids.add(createInvoice(token + " clamp " + i, "PAID", "CARD",
                    new BigDecimal("100.00"), created).getInvoiceId());
        }
        ids.sort(Comparator.reverseOrder());

        InvoiceService capped = new InvoiceService(invoiceRepository, bookingRepository,
                promotionRepository, clock, 3);
        PageResponse<AdminInvoiceListItem> page = capped.list(
                PageRequest.of(1, 100), "PAID", token);

        assertEquals(3, page.size());
        assertEquals(1, page.page());
        assertEquals(11, page.totalElements());
        assertEquals(4, page.totalPages());
        assertEquals(ids.subList(3, 6), page.content().stream()
                .map(AdminInvoiceListItem::id).toList());
    }

    @Test
    void listUsesConstantSqlWithoutPromotionFetches() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 12, 0);
        Promotion first = makePromotion("INV-A-" + token);
        Promotion second = makePromotion("INV-B-" + token);
        for (int i = 0; i < 6; i++) {
            Invoice invoice = createInvoice(token + " sql " + i, "PAID", "CARD",
                    new BigDecimal("100.00"), created);
            invoice.getPromotions().add(first);
            invoice.getPromotions().add(second);
            invoiceRepository.save(invoice);
        }
        flushAndClear();

        assertPreparedStatementsAtMost(2, () -> list(PageRequest.of(0, 1), "PAID", token));
        assertEquals(0, statistics.getCollectionLoadCount());
        flushAndClear();
        assertPreparedStatementsAtMost(2, () -> list(PageRequest.of(0, 10), "PAID", token));
        assertEquals(0, statistics.getCollectionLoadCount());
        flushAndClear();
        assertPreparedStatementsAtMost(2, () -> list(PageRequest.of(0, 20), "PAID", token));
        assertEquals(0, statistics.getCollectionLoadCount());
    }

    @Test
    void detailsAndMineStillUseFullResponse() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 12, 0);
        Promotion first = makePromotion("DET-A-" + token);
        Promotion second = makePromotion("DET-B-" + token);
        Invoice invoice = createInvoice(token + " detail", "PAID", "CARD",
                new BigDecimal("100.00"), created);
        invoice.setCustomerPhone("0901234567");
        invoice.setTaxAmount(new BigDecimal("5.00"));
        invoice.setServiceFee(new BigDecimal("7.00"));
        invoice.getPromotions().add(first);
        invoice.getPromotions().add(second);
        invoiceRepository.save(invoice);

        InvoiceResponse byId = invoiceService.get(invoice.getInvoiceId());
        InvoiceResponse byBooking = invoiceService.getByBooking(invoice.getBooking().getBookingId());
        PageResponse<InvoiceResponse> mine = invoiceService.listMine(
                account.getAccountId(), PageRequest.of(0, 10));

        assertEquals(invoice.getInvoiceId(), byId.id());
        assertEquals(List.of(first.getPromotionId(), second.getPromotionId()),
                byId.promotionIds());
        assertEquals("0901234567", byId.customerPhone());
        assertEquals(new BigDecimal("5.00"), byId.taxAmount());
        assertEquals(new BigDecimal("7.00"), byId.serviceFee());
        assertEquals(invoice.getInvoiceId(), byBooking.id());
        assertEquals(1, mine.totalElements());
        assertEquals(List.of(first.getPromotionId(), second.getPromotionId()),
                mine.content().get(0).promotionIds());

        Account other = createAccount("other-inv-" + token);
        assertEquals(0, invoiceService.listMine(other.getAccountId(),
                PageRequest.of(0, 10)).totalElements());
        assertThrows(NoSuchElementException.class, () -> invoiceService.get(Long.MAX_VALUE));
    }

    private PageResponse<AdminInvoiceListItem> list(PageRequest pageable, String status, String q) {
        return invoiceService.list(pageable, status, q);
    }

    private void assertCodes(String query, Invoice expected) {
        PageResponse<AdminInvoiceListItem> page = list(PageRequest.of(0, 10), null, query);
        assertEquals(1, page.totalElements());
        assertEquals(expected.getInvoiceId(), page.content().get(0).id());
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

    private Account createAccount(String username) {
        Account other = new Account();
        other.setUsername(username);
        other.setPassword("secret");
        other.setAccountEmail(username + "@example.com");
        other.setEmailVerified(1);
        other.setStatus(1);
        other.setAccountCreatedAt(LocalDateTime.now());
        other.setAccountUpdatedAt(LocalDateTime.now());
        other.setRole(account.getRole());
        return accountRepository.save(other);
    }

    private Promotion makePromotion(String code) {
        Promotion promotion = new Promotion();
        promotion.setPromotionName("Promo " + code);
        promotion.setPromotionCode(code);
        promotion.setPromotionActive(true);
        promotion.setDiscountPercentage(new BigDecimal("10"));
        promotion.setStartDate(LocalDate.now().minusDays(1));
        promotion.setEndDate(LocalDate.now().plusDays(10));
        promotion.setPromotionUsageLimit(5);
        promotion.setPromotionUsedCount(0);
        promotion.setPromotionMinOrderAmount(new BigDecimal("500.00"));
        return promotionRepository.save(promotion);
    }

    private Invoice createInvoice(String customerName, String status, String paymentMethod,
                                  BigDecimal total, LocalDateTime createdAt) {
        Booking booking = new Booking();
        booking.setBookingCode("I" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 15));
        booking.setBookingCreatedAt(createdAt);
        booking.setBookingGuestCount(1);
        booking.setBookingUnitPrice(total);
        booking.setBookingSubtotal(total);
        booking.setBookingDiscountAmount(BigDecimal.ZERO);
        booking.setBookingTotalAmount(total);
        booking.setBookingStatus("PENDING");
        booking.setAccount(account);
        booking.setDeparture(departure);
        booking = bookingRepository.save(booking);

        Invoice invoice = new Invoice();
        invoice.setInvoiceCreatedAt(createdAt);
        invoice.setStatus(status);
        invoice.setDiscountAmount(BigDecimal.ZERO);
        invoice.setTaxAmount(BigDecimal.ZERO);
        invoice.setServiceFee(BigDecimal.ZERO);
        invoice.setTotalAmount(total);
        invoice.setPaymentMethod(paymentMethod);
        invoice.setCustomerName(customerName);
        invoice.setCustomerEmail(customerName.replace(" ", ".") + "@example.com");
        invoice.setBooking(booking);
        return invoiceRepository.save(invoice);
    }
}
