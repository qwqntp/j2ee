package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.BookingUpdateRequest;
import com.j2ee.tour.dto.request.CreateBookingRequest;
import com.j2ee.tour.dto.response.BookingResponse;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.InvoiceRepository;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TravelerRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class BookingService {
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BookingRepository bookingRepository;
    private final TourDepartureRepository departureRepository;
    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final TravelerRepository travelerRepository;

    public BookingService(BookingRepository bookingRepository,
                          TourDepartureRepository departureRepository,
                          AccountRepository accountRepository,
                          PaymentRepository paymentRepository,
                          InvoiceRepository invoiceRepository,
                          TravelerRepository travelerRepository) {
        this.bookingRepository = bookingRepository;
        this.departureRepository = departureRepository;
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.travelerRepository = travelerRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> list(Pageable pageable) {
        return PageResponse.from(bookingRepository.findAll(pageable), BookingResponse::from);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long id) {
        return BookingResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public BookingResponse getByCode(String bookingCode) {
        return BookingResponse.from(bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new NoSuchElementException("Booking not found")));
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        TourDeparture departure = departureRepository.findByIdForUpdate(request.departureId())
                .orElseThrow(() -> new NoSuchElementException("Departure not found"));

        if (!"OPEN".equals(departure.getDepartureStatus())) {
            throw new IllegalStateException("Departure is not open for booking");
        }
        reserveSeats(departure, request.guestCount());

        BigDecimal subtotal = departure.getDeparturePrice().multiply(BigDecimal.valueOf(request.guestCount()));
        Booking booking = new Booking();
        booking.setBookingCode("BK-" + LocalDateTime.now().format(CODE_DATE) + "-" + UUID.randomUUID().toString().substring(0, 4));
        booking.setBookingCreatedAt(LocalDateTime.now());
        booking.setBookingGuestCount(request.guestCount());
        booking.setBookingUnitPrice(departure.getDeparturePrice());
        booking.setBookingSubtotal(subtotal);
        booking.setBookingDiscountAmount(BigDecimal.ZERO);
        booking.setBookingTotalAmount(subtotal);
        booking.setBookingStatus("PENDING");
        booking.setBookingNote(request.note());
        booking.setDeparture(departure);
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse update(Long id, BookingUpdateRequest request) {
        Booking booking = find(id);
        boolean seatsMayChange = request.guestCount() != null || request.status() != null;
        TourDeparture departure = seatsMayChange
                ? departureRepository.findByIdForUpdate(booking.getDeparture().getDepartureId())
                .orElseThrow(() -> new NoSuchElementException("Departure not found"))
                : booking.getDeparture();

        int oldGuestCount = booking.getBookingGuestCount();
        String oldStatus = booking.getBookingStatus();
        int newGuestCount = request.guestCount() == null ? oldGuestCount : request.guestCount();
        String newStatus = request.status() == null ? oldStatus : request.status();

        int oldReserved = "CANCELLED".equals(oldStatus) ? 0 : oldGuestCount;
        int newReserved = "CANCELLED".equals(newStatus) ? 0 : newGuestCount;
        int delta = newReserved - oldReserved;
        if (delta > 0) {
            reserveSeats(departure, delta);
        } else if (delta < 0) {
            releaseSeats(departure, -delta);
        }

        if (request.accountId() != null) {
            Account account = accountRepository.findById(request.accountId())
                    .orElseThrow(() -> new NoSuchElementException("Account not found"));
            booking.setAccount(account);
        }
        if (request.guestCount() != null) {
            BigDecimal newSubtotal = departure.getDeparturePrice().multiply(BigDecimal.valueOf(newGuestCount));
            booking.setBookingUnitPrice(departure.getDeparturePrice());
            booking.setBookingSubtotal(newSubtotal);
            BigDecimal discount = booking.getBookingDiscountAmount();
            BigDecimal total = newSubtotal.subtract(discount);
            if (total.compareTo(BigDecimal.ZERO) < 0) {
                total = BigDecimal.ZERO;
            }
            booking.setBookingTotalAmount(total);
        }
        if ("PAID".equals(newStatus)
                && paymentRepository.sumSuccessfulPayments(id).compareTo(booking.getBookingTotalAmount()) < 0) {
            throw new IllegalStateException("Booking can only be PAID when successful payments cover the total");
        }

        booking.setBookingGuestCount(newGuestCount);
        booking.setBookingStatus(newStatus);
        if (request.note() != null) {
            booking.setBookingNote(request.note());
        }
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public void delete(Long id) {
        Booking booking = find(id);
        if (paymentRepository.existsByBookingBookingId(id)) {
            throw new IllegalStateException("Cannot delete a booking that has payments");
        }
        if (invoiceRepository.existsByBookingBookingId(id)) {
            throw new IllegalStateException("Cannot delete a booking that has an invoice");
        }

        TourDeparture departure = departureRepository.findByIdForUpdate(booking.getDeparture().getDepartureId())
                .orElseThrow(() -> new NoSuchElementException("Departure not found"));
        if (!"CANCELLED".equals(booking.getBookingStatus())) {
            releaseSeats(departure, booking.getBookingGuestCount());
        }
        travelerRepository.deleteAll(booking.getTravelers());
        bookingRepository.delete(booking);
    }

    private void reserveSeats(TourDeparture departure, int seats) {
        if (seats > departure.getSeatsAvailable()) {
            throw new IllegalStateException("Not enough seats available");
        }
        departure.setDepartureBookedSlots(departure.getDepartureBookedSlots() + seats);
        syncDepartureStatus(departure);
        departureRepository.save(departure);
    }

    private void releaseSeats(TourDeparture departure, int seats) {
        int released = Math.min(seats, departure.getDepartureBookedSlots());
        departure.setDepartureBookedSlots(departure.getDepartureBookedSlots() - released);
        syncDepartureStatus(departure);
        departureRepository.save(departure);
    }

    private void syncDepartureStatus(TourDeparture departure) {
        if (departure.getDepartureBookedSlots() >= departure.getDepartureCapacity()) {
            if (!"CANCELLED".equals(departure.getDepartureStatus())
                    && !"CLOSED".equals(departure.getDepartureStatus())) {
                departure.setDepartureStatus("FULL");
            }
        } else if ("FULL".equals(departure.getDepartureStatus())) {
            departure.setDepartureStatus("OPEN");
        }
    }

    private Booking find(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Booking not found"));
    }
}
