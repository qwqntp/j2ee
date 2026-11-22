package com.j2ee.tour.service;

import com.j2ee.tour.dto.request.TourDepartureRequest;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Payment;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TourDepartureCancelCascadeTest {

    private TourDepartureRepository departureRepository;
    private TourRepository tourRepository;
    private BookingRepository bookingRepository;
    private PaymentRepository paymentRepository;
    private RefundService refundService;
    private EmailService emailService;
    private AfterCommitMailDispatcher mailDispatcher;
    private TourDepartureService departureService;

    private TourDeparture departure;
    private Tour tour;

    @BeforeEach
    void setUp() {
        departureRepository = mock(TourDepartureRepository.class);
        tourRepository = mock(TourRepository.class);
        bookingRepository = mock(BookingRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        refundService = mock(RefundService.class);
        emailService = mock(EmailService.class);
        mailDispatcher = new AfterCommitMailDispatcher();
        departureService = new TourDepartureService(departureRepository, tourRepository,
                bookingRepository, paymentRepository, refundService, emailService,
                mailDispatcher,
                new GuideOverlapValidator(departureRepository, mock(TourGuideRepository.class)));

        tour = new Tour();
        tour.setTourId(1L);

        departure = new TourDeparture();
        departure.setDepartureId(5L);
        departure.setTour(tour);
        departure.setDepartureStartDate(LocalDateTime.now().plusDays(10));
        departure.setDepartureEndDate(LocalDateTime.now().plusDays(12));
        departure.setDepartureCapacity(10);
        departure.setDepartureBookedSlots(2);
        departure.setDeparturePrice(new BigDecimal("100.00"));
        departure.setDepartureStatus("OPEN");

        when(departureRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(departure));
        when(departureRepository.save(any(TourDeparture.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tourRepository.findById(1L)).thenReturn(Optional.of(tour));
    }

    @Test
    void cancellingDepartureCancelsLiveBookingsAndReservesRefunds() {
        Account account = new Account();
        account.setAccountEmail("buyer@example.com");

        Booking paid = new Booking();
        paid.setBookingId(1L);
        paid.setBookingCode("BK-1");
        paid.setBookingStatus("PAID");
        paid.setAccount(account);

        Booking completed = new Booking();
        completed.setBookingId(2L);
        completed.setBookingCode("BK-2");
        completed.setBookingStatus("COMPLETED");

        Payment successful = new Payment();
        successful.setPaymentId(11L);
        successful.setPaymentStatus("SUCCESS");

        when(bookingRepository.findBookingIdsByDepartureId(5L)).thenReturn(List.of(1L, 2L));
        when(bookingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(paid));
        when(bookingRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(completed));
        when(paymentRepository.findByBookingBookingId(1L))
                .thenReturn(List.of(successful));

        departureService.update(5L, cancelledRequest());

        assertEquals("CANCELLED", departure.getDepartureStatus());
        assertEquals(0, departure.getDepartureBookedSlots());
        assertEquals("CANCELLED", paid.getBookingStatus());
        assertEquals("COMPLETED", completed.getBookingStatus());
        verify(refundService).requestRemaining(11L, "Departure cancelled");
        verify(emailService).sendBookingCancellation("buyer@example.com", "BK-1");
        verify(paymentRepository, never()).findByBookingBookingId(2L);
    }

    @Test
    void cascadeDefersCancellationEmailsUntilCommit() {
        Account account = new Account();
        account.setAccountEmail("buyer@example.com");

        Booking paid = new Booking();
        paid.setBookingId(1L);
        paid.setBookingCode("BK-1");
        paid.setBookingStatus("PAID");
        paid.setAccount(account);

        when(bookingRepository.findBookingIdsByDepartureId(5L)).thenReturn(List.of(1L));
        when(bookingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(paid));

        TransactionSynchronizationManager.initSynchronization();
        try {
            departureService.update(5L, cancelledRequest());

            verify(emailService, never()).sendBookingCancellation(any(), any());
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(emailService).sendBookingCancellation("buyer@example.com", "BK-1");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void otherStatusUpdatesDoNotCascade() {
        TourDepartureRequest closed = new TourDepartureRequest(1L,
                departure.getDepartureStartDate(), departure.getDepartureEndDate(),
                10, null, new BigDecimal("100.00"), null, "CLOSED");

        departureService.update(5L, closed);

        assertEquals("CLOSED", departure.getDepartureStatus());
        verify(bookingRepository, never()).findBookingIdsByDepartureId(5L);
    }

    private TourDepartureRequest cancelledRequest() {
        return new TourDepartureRequest(1L,
                departure.getDepartureStartDate(), departure.getDepartureEndDate(),
                10, null, new BigDecimal("100.00"), null, "CANCELLED");
    }
}
