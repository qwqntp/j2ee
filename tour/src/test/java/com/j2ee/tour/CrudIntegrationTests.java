package com.j2ee.tour;

import com.j2ee.tour.dto.request.*;
import com.j2ee.tour.model.*;
import com.j2ee.tour.repository.*;
import com.j2ee.tour.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CrudIntegrationTests {

    @Autowired private AccountRoleService accountRoleService;
    @Autowired private AccountService accountService;
    @Autowired private AccommodationService accommodationService;
    @Autowired private PromotionService promotionService;
    @Autowired private TourGuideService tourGuideService;
    @Autowired private TouristDestinationService touristDestinationService;
    @Autowired private TravelVehicleService travelVehicleService;
    @Autowired private TourImageService tourImageService;
    @Autowired private TourService tourService;
    @Autowired private TourDepartureService tourDepartureService;
    @Autowired private TravelerService travelerService;
    @Autowired private CustomerService customerService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private PaymentService paymentService;
    @Autowired private RefundService refundService;
    @Autowired private BookingService bookingService;

    @Autowired private AccountRoleRepository accountRoleRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccommodationRepository accommodationRepository;
    @Autowired private TourGuideRepository tourGuideRepository;
    @Autowired private TourRepository tourRepository;
    @Autowired private TourDepartureRepository tourDepartureRepository;

    private AccountRole makeRole(String name) {
        AccountRole r = new AccountRole();
        r.setRoleName(name);
        return r;
    }

    private Accommodation makeAccommodation() {
        Accommodation a = new Accommodation();
        a.setAccommodationName("Hotel");
        a.setLocation("HCM");
        return a;
    }

    private TourGuide makeGuide() {
        TourGuide g = new TourGuide();
        g.setTourGuideName("Guide");
        g.setTourGuideExperienceYears(1);
        return g;
    }

    private Tour makeTour() {
        Accommodation acc = accommodationRepository.save(makeAccommodation());
        TourGuide guide = tourGuideRepository.save(makeGuide());
        Tour t = new Tour();
        t.setTourName("Tour");
        t.setTourStatus("OPEN");
        t.setAccommodation(acc);
        t.setTourGuide(guide);
        return t;
    }

    private TourDeparture makeDeparture(Tour tour) {
        TourDeparture d = new TourDeparture();
        d.setTour(tour);
        d.setDepartureStartDate(LocalDateTime.now().plusDays(1));
        d.setDepartureEndDate(LocalDateTime.now().plusDays(3));
        d.setDepartureCapacity(5);
        d.setDepartureBookedSlots(0);
        d.setDeparturePrice(new BigDecimal("500.00"));
        d.setDepartureStatus("OPEN");
        d.setDepartureCreatedAt(LocalDateTime.now());
        return d;
    }

    private Account makeAccount(AccountRole role) {
        Account a = new Account();
        a.setUsername("acc" + System.nanoTime());
        a.setPassword("secret");
        a.setAccountEmail("a@x.com");
        a.setEmailVerified(1);
        a.setStatus(1);
        a.setAccountCreatedAt(LocalDateTime.now());
        a.setAccountUpdatedAt(LocalDateTime.now());
        a.setRole(role);
        return a;
    }

    @Test
    void accountRoleCrud() {
        var created = accountRoleService.create(new AccountRoleRequest("ADMIN"));
        assertEquals("ADMIN", accountRoleService.get(created.id()).roleName());
        var updated = accountRoleService.update(created.id(), new AccountRoleRequest("SUPER"));
        assertEquals("SUPER", updated.roleName());
        accountRoleService.delete(created.id());
        assertThrows(Exception.class, () -> accountRoleService.get(created.id()));
    }

    @Test
    void accommodationCrud() {
        var created = accommodationService.create(new AccommodationRequest("H1", "HCM", new BigDecimal("4.5"), new BigDecimal("100"), "HOTEL"));
        assertEquals("H1", accommodationService.get(created.id()).name());
        var updated = accommodationService.update(created.id(), new AccommodationRequest("H2", "HN", new BigDecimal("3.0"), new BigDecimal("200"), "RESORT"));
        assertEquals("H2", updated.name());
        accommodationService.delete(created.id());
        assertThrows(Exception.class, () -> accommodationService.get(created.id()));
    }

    @Test
    void promotionCrud() {
        var created = promotionService.create(new PromotionRequest("Sale", new BigDecimal("10"), LocalDate.now(), LocalDate.now().plusDays(10), "desc"));
        assertEquals("Sale", promotionService.get(created.id()).name());
        promotionService.update(created.id(), new PromotionRequest("Sale2", new BigDecimal("20"), LocalDate.now(), LocalDate.now().plusDays(5), "d2"));
        assertEquals("Sale2", promotionService.get(created.id()).name());
        promotionService.delete(created.id());
        assertThrows(Exception.class, () -> promotionService.get(created.id()));
    }

    @Test
    void tourGuideCrud() {
        var created = tourGuideService.create(new TourGuideRequest("Guide", "g@x.com", "090", 5));
        assertEquals("Guide", tourGuideService.get(created.id()).name());
        tourGuideService.update(created.id(), new TourGuideRequest("Guide2", "g2@x.com", "091", 6));
        assertEquals("Guide2", tourGuideService.get(created.id()).name());
        tourGuideService.delete(created.id());
        assertThrows(Exception.class, () -> tourGuideService.get(created.id()));
    }

    @Test
    void touristDestinationCrud() {
        var created = touristDestinationService.create(new TouristDestinationRequest("DL", "Lam Dong", "desc", new BigDecimal("5")));
        assertEquals("DL", touristDestinationService.get(created.id()).name());
        touristDestinationService.update(created.id(), new TouristDestinationRequest("DL2", "LD2", "d2", new BigDecimal("6")));
        assertEquals("DL2", touristDestinationService.get(created.id()).name());
        touristDestinationService.delete(created.id());
        assertThrows(Exception.class, () -> touristDestinationService.get(created.id()));
    }

    @Test
    void travelVehicleCrud() {
        var created = travelVehicleService.create(new TravelVehicleRequest("Bus", 40, new BigDecimal("200")));
        assertEquals("Bus", travelVehicleService.get(created.id()).type());
        travelVehicleService.update(created.id(), new TravelVehicleRequest("Van", 10, new BigDecimal("100")));
        assertEquals("Van", travelVehicleService.get(created.id()).type());
        travelVehicleService.delete(created.id());
        assertThrows(Exception.class, () -> travelVehicleService.get(created.id()));
    }

    @Test
    void accountCrud() {
        AccountRole role = accountRoleRepository.save(makeRole("USER2"));
        var created = accountService.create(new AccountCreateRequest("accuser", "password123", "a@x.com", 1, 1, role.getAccountRoleId()));
        assertEquals("accuser", accountService.get(created.id()).username());
        accountService.update(created.id(), new AccountUpdateRequest("accuser2", null, "b@x.com", 0, 1, role.getAccountRoleId()));
        assertEquals("accuser2", accountService.get(created.id()).username());
        accountService.delete(created.id());
        assertThrows(Exception.class, () -> accountService.get(created.id()));
    }

    @Test
    void tourCrud() {
        Accommodation acc = accommodationRepository.save(makeAccommodation());
        TourGuide guide = tourGuideRepository.save(makeGuide());

        var created = tourService.create(new TourRequest("Tour1", "desc", 3, "OPEN", acc.getAccommodationId(), guide.getTourGuideId(), null, List.of("http://img/1.jpg"), List.of(), List.of()));
        assertEquals("Tour1", tourService.get(created.id()).name());
        assertEquals(1, tourService.get(created.id()).imageUrls().size());

        tourService.update(created.id(), new TourRequest("Tour2", "desc2", 4, "OPEN", acc.getAccommodationId(), guide.getTourGuideId(), null, List.of("http://img/2.jpg"), List.of(), List.of()));
        assertEquals("Tour2", tourService.get(created.id()).name());

        var img = tourImageService.create(new TourImageRequest("http://img/x.jpg", created.id()));
        assertEquals("http://img/x.jpg", tourImageService.get(img.id()).imageUrl());
        tourImageService.update(img.id(), new TourImageRequest("http://img/y.jpg", created.id()));
        assertEquals("http://img/y.jpg", tourImageService.get(img.id()).imageUrl());
        tourImageService.delete(img.id());
        assertThrows(Exception.class, () -> tourImageService.get(img.id()));

        var dep = tourDepartureService.create(new TourDepartureRequest(created.id(), LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3), 10, 0, new BigDecimal("500"), "mp", "OPEN"));
        assertEquals(10, tourDepartureService.get(dep.id()).capacity());
        tourDepartureService.update(dep.id(), new TourDepartureRequest(created.id(), LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3), 8, 0, new BigDecimal("600"), "mp2", "OPEN"));
        assertEquals(8, tourDepartureService.get(dep.id()).capacity());

        tourDepartureService.delete(dep.id());
        tourService.delete(created.id());
        assertThrows(Exception.class, () -> tourService.get(created.id()));
    }

    @Test
    void customerCrud() {
        AccountRole role = accountRoleRepository.save(makeRole("RC"));
        Account acc = accountRepository.save(makeAccount(role));
        var created = customerService.create(new CustomerRequest("Cust", "c@x.com", "090", "addr", LocalDate.now(), acc.getAccountId()));
        assertEquals("Cust", customerService.get(created.id()).name());
        customerService.update(created.id(), new CustomerRequest("Cust2", "c2@x.com", "091", "addr2", LocalDate.now(), acc.getAccountId()));
        assertEquals("Cust2", customerService.get(created.id()).name());
        customerService.delete(created.id());
        assertThrows(Exception.class, () -> customerService.get(created.id()));
    }

    @Test
    void invoiceCrud() {
        Tour tour = tourRepository.save(makeTour());
        TourDeparture dep = tourDepartureRepository.save(makeDeparture(tour));
        var booking = bookingService.create(new CreateBookingRequest(dep.getDepartureId(), 1, "n"));
        var created = invoiceService.create(new InvoiceRequest(booking.id(), "PAID", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000"), "CARD", "Cust", null, null, List.of()));
        assertEquals("PAID", invoiceService.get(created.id()).status());
        invoiceService.update(created.id(), new InvoiceRequest(booking.id(), "UNPAID", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000"), "CARD", "Cust2", null, null, List.of()));
        assertEquals("UNPAID", invoiceService.get(created.id()).status());
        invoiceService.delete(created.id());
        assertThrows(Exception.class, () -> invoiceService.get(created.id()));
    }

    @Test
    void travelerCrud() {
        Tour tour = tourRepository.save(makeTour());
        TourDeparture dep = tourDepartureRepository.save(makeDeparture(tour));
        var booking = bookingService.create(new CreateBookingRequest(dep.getDepartureId(), 1, "n"));
        var created = travelerService.create(new TravelerRequest(booking.id(), "T1", LocalDate.now(), "MALE", "ID1"));
        assertEquals("T1", travelerService.get(created.id()).name());
        travelerService.update(created.id(), new TravelerRequest(booking.id(), "T2", LocalDate.now(), "FEMALE", "ID2"));
        assertEquals("T2", travelerService.get(created.id()).name());
        travelerService.delete(created.id());
        assertThrows(Exception.class, () -> travelerService.get(created.id()));
    }

    @Test
    void paymentCrud() {
        Tour tour = tourRepository.save(makeTour());
        TourDeparture dep = tourDepartureRepository.save(makeDeparture(tour));
        var booking = bookingService.create(new CreateBookingRequest(dep.getDepartureId(), 1, "n"));
        var created = paymentService.create(new PaymentCreateRequest(booking.id(), new BigDecimal("100.00"), "CASH", "txn-p", "PENDING", null));
        assertEquals("PENDING", paymentService.get(created.id()).status());
        paymentService.update(created.id(), new PaymentUpdateRequest("CARD", "txn-p2", "SUCCESS", null));
        assertEquals("SUCCESS", paymentService.get(created.id()).status());
        paymentService.delete(created.id());
        assertThrows(Exception.class, () -> paymentService.get(created.id()));
    }

    @Test
    void refundUpdateAndDelete() {
        Tour tour = tourRepository.save(makeTour());
        TourDeparture dep = tourDepartureRepository.save(makeDeparture(tour));
        var booking = bookingService.create(new CreateBookingRequest(dep.getDepartureId(), 1, "n"));
        var payment = paymentService.handleCallback(booking.id(), new PaymentCallbackRequest("txn-r", new BigDecimal("500.00"), "CASH", true));
        var refund = refundService.request(payment.id(), new CreateRefundRequest(new BigDecimal("100.00"), "r"));
        refundService.update(refund.id(), new RefundUpdateRequest("APPROVED", "ok"));
        assertEquals("APPROVED", refundService.get(refund.id()).status());
        refundService.delete(refund.id());
        assertThrows(Exception.class, () -> refundService.get(refund.id()));
    }
}
