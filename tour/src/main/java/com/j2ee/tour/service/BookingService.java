package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.BookingTravelerRequest;
import com.j2ee.tour.dto.request.BookingUpdateRequest;
import com.j2ee.tour.dto.request.CreateBookingRequest;
import com.j2ee.tour.dto.response.BookingResponse;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Promotion;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.Traveler;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.InvoiceRepository;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.repository.PromotionRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TravelerRepository;
import com.j2ee.tour.security.AuthPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class BookingService {
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BookingRepository bookingRepository;
    private final TourDepartureRepository departureRepository;
    private final AccountRepository accountRepository;
    private final PromotionRepository promotionRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final TravelerRepository travelerRepository;
    private final EmailService emailService;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository,
                          TourDepartureRepository departureRepository,
                          AccountRepository accountRepository,
                          PromotionRepository promotionRepository,
                          PaymentRepository paymentRepository,
                          InvoiceRepository invoiceRepository,
                          InvoiceService invoiceService,
                          TravelerRepository travelerRepository,
                          EmailService emailService,
                          Clock clock) {
        this.bookingRepository = bookingRepository;
        this.departureRepository = departureRepository;
        this.accountRepository = accountRepository;
        this.promotionRepository = promotionRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.travelerRepository = travelerRepository;
        this.emailService = emailService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> list(Pageable pageable, AuthPrincipal principal) {
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "bookingCreatedAt")
                                .and(Sort.by(Sort.Direction.DESC, "bookingId")));
        Page<Booking> bookings = isAdmin(principal)
                ? bookingRepository.findAll(effective)
                : bookingRepository.findByAccountAccountId(principal.id(), effective);
        return PageResponse.from(bookings, BookingResponse::from);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long id, AuthPrincipal principal) {
        return BookingResponse.from(findOwned(id, principal));
    }

    @Transactional(readOnly = true)
    public BookingResponse getByCode(String bookingCode, AuthPrincipal principal) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new NoSuchElementException("Booking not found"));
        ensureOwner(booking, principal);
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request, AuthPrincipal principal) {
        List<BookingTravelerRequest> travelerRequests = request.travelers();
        if (travelerRequests != null && travelerRequests.size() != request.guestCount()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Traveler list size must match guestCount");
        }

        Account account = isAdmin(principal) ? null : accountRepository.findById(principal.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));

        TourDeparture departure = departureRepository.findByIdForUpdate(request.departureId())
                .orElseThrow(() -> new NoSuchElementException("Departure not found"));

        if (!"OPEN".equals(departure.getDepartureStatus())) {
            throw new IllegalStateException("Departure is not open for booking");
        }
        if (!departure.getDepartureStartDate().isAfter(LocalDateTime.now(clock))) {
            throw new IllegalStateException("Departure has already started");
        }
        reserveSeats(departure, request.guestCount());

        BigDecimal subtotal = departure.getDeparturePrice()
                .multiply(BigDecimal.valueOf(request.guestCount()));

        Promotion promotion = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (request.promotionCode() != null && !request.promotionCode().isBlank()) {
            promotion = findValidPromotion(request.promotionCode(), subtotal);
            discount = calculateDiscount(promotion, subtotal);
            int usedCount = promotion.getPromotionUsedCount() == null
                    ? 0
                    : promotion.getPromotionUsedCount();
            promotion.setPromotionUsedCount(usedCount + 1);
            promotionRepository.save(promotion);
        }

        BigDecimal total = subtotal.subtract(discount);
        if (total.signum() < 0) {
            total = BigDecimal.ZERO;
        }

        Booking booking = new Booking();
        booking.setBookingCode("BK-" + LocalDateTime.now(clock).format(CODE_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 4));
        booking.setBookingCreatedAt(LocalDateTime.now(clock));
        booking.setBookingGuestCount(request.guestCount());
        booking.setBookingUnitPrice(departure.getDeparturePrice());
        booking.setBookingSubtotal(subtotal);
        booking.setBookingDiscountAmount(discount);
        booking.setBookingTotalAmount(total);
        booking.setBookingStatus("PENDING");
        booking.setBookingNote(request.note());
        booking.setDeparture(departure);
        booking.setAccount(account);
        booking.setPromotion(promotion);

        Booking saved = bookingRepository.save(booking);
        if (travelerRequests != null) {
            List<Traveler> travelers = travelerRequests.stream()
                    .map(travelerRequest -> toTraveler(saved, travelerRequest))
                    .toList();
            saved.getTravelers().addAll(travelerRepository.saveAll(travelers));
        }
        sendBookingConfirmation(saved);
        return BookingResponse.from(saved);
    }

    @Transactional
    public BookingResponse update(Long id, BookingUpdateRequest request, AuthPrincipal principal) {
        Booking booking = findOwnedForUpdate(id, principal);
        if (!isAdmin(principal) && (request.accountId() != null || request.status() != null)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Only administrators can change the booking owner or status");
        }
        boolean seatsMayChange = request.guestCount() != null || request.status() != null;
        TourDeparture departure = seatsMayChange
                ? departureRepository.findByIdForUpdate(booking.getDeparture().getDepartureId())
                .orElseThrow(() -> new NoSuchElementException("Departure not found"))
                : booking.getDeparture();

        int oldGuestCount = booking.getBookingGuestCount();
        String oldStatus = booking.getBookingStatus();
        int newGuestCount = request.guestCount() == null ? oldGuestCount : request.guestCount();
        String newStatus = request.status() == null ? oldStatus : request.status();

        BigDecimal newSubtotal = null;
        BigDecimal newDiscount = null;
        if (request.guestCount() != null) {
            newSubtotal = departure.getDeparturePrice()
                    .multiply(BigDecimal.valueOf(newGuestCount));
            newDiscount = booking.getBookingDiscountAmount() == null
                    ? BigDecimal.ZERO
                    : booking.getBookingDiscountAmount();
            Promotion promotion = booking.getPromotion();
            if (promotion != null) {
                if (promotion.getPromotionMinOrderAmount() != null
                        && newSubtotal.compareTo(promotion.getPromotionMinOrderAmount()) < 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Order does not meet promotion minimum amount");
                }
                newDiscount = calculateDiscount(promotion, newSubtotal);
            }
        }

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
            booking.setBookingUnitPrice(departure.getDeparturePrice());
            booking.setBookingSubtotal(newSubtotal);
            booking.setBookingDiscountAmount(newDiscount);
            BigDecimal total = newSubtotal.subtract(newDiscount);
            if (total.signum() < 0) {
                total = BigDecimal.ZERO;
            }
            booking.setBookingTotalAmount(total);
        }
        if ("PAID".equals(newStatus)
                && paymentRepository.sumSuccessfulPayments(id)
                .compareTo(booking.getBookingTotalAmount()) < 0) {
            throw new IllegalStateException("Booking can only be PAID when successful payments cover the total");
        }
        if (!"CANCELLED".equals(oldStatus) && "CANCELLED".equals(newStatus)) {
            releasePromotionUsage(booking);
        }

        booking.setBookingGuestCount(newGuestCount);
        booking.setBookingStatus(newStatus);
        if (request.note() != null) {
            booking.setBookingNote(request.note());
        }
        Booking saved = bookingRepository.save(booking);
        if (!"PAID".equals(oldStatus) && "PAID".equals(newStatus)) {
            invoiceService.createForPaidBooking(saved);
        }
        if (!"CANCELLED".equals(oldStatus) && "CANCELLED".equals(newStatus)) {
            sendBookingCancellation(saved);
        }
        return BookingResponse.from(saved);
    }

    @Transactional
    public BookingResponse cancel(Long id, AuthPrincipal principal) {
        Booking booking = findOwnedForUpdate(id, principal);
        if (!"PENDING".equals(booking.getBookingStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Only pending unpaid bookings can be cancelled");
        }
        BigDecimal paid = paymentRepository.sumSuccessfulPayments(id);
        if (paid != null && paid.compareTo(BigDecimal.ZERO) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Only pending unpaid bookings can be cancelled");
        }

        TourDeparture departure = departureRepository
                .findByIdForUpdate(booking.getDeparture().getDepartureId())
                .orElseThrow(() -> new NoSuchElementException("Departure not found"));
        releaseSeats(departure, booking.getBookingGuestCount());
        releasePromotionUsage(booking);
        booking.setBookingStatus("CANCELLED");
        Booking saved = bookingRepository.save(booking);
        sendBookingCancellation(saved);
        return BookingResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, AuthPrincipal principal) {
        Booking booking = findOwned(id, principal);
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

    private void sendBookingConfirmation(Booking booking) {
        if (booking.getAccount() != null) {
            emailService.sendBookingConfirmation(
                    booking.getAccount().getAccountEmail(),
                    booking.getBookingCode(),
                    booking.getBookingTotalAmount());
        }
    }

    private void sendBookingCancellation(Booking booking) {
        if (booking.getAccount() != null) {
            emailService.sendBookingCancellation(
                    booking.getAccount().getAccountEmail(),
                    booking.getBookingCode());
        }
    }

    private Traveler toTraveler(Booking booking, BookingTravelerRequest request) {
        Traveler traveler = new Traveler();
        traveler.setBooking(booking);
        traveler.setTravelerName(request.fullName());
        traveler.setTravelerDateOfBirth(request.dateOfBirth());
        traveler.setTravelerGender(request.gender());
        traveler.setTravelerIdDocument(request.idDocument());
        traveler.setTravelerPhone(request.phone());
        traveler.setTravelerEmail(request.email());
        return traveler;
    }

    private Promotion findValidPromotion(String rawCode, BigDecimal subtotal) {
        String code = rawCode.trim();
        Promotion promotion = promotionRepository.findByPromotionCodeIgnoreCaseForUpdate(code)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Promotion code is invalid"));
        if (!Boolean.TRUE.equals(promotion.getPromotionActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Promotion is not active");
        }
        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(promotion.getStartDate()) || today.isAfter(promotion.getEndDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Promotion is not valid today");
        }
        int usedCount = promotion.getPromotionUsedCount() == null
                ? 0
                : promotion.getPromotionUsedCount();
        if (promotion.getPromotionUsageLimit() != null
                && usedCount >= promotion.getPromotionUsageLimit()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Promotion usage limit reached");
        }
        if (promotion.getPromotionMinOrderAmount() != null
                && subtotal.compareTo(promotion.getPromotionMinOrderAmount()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Order does not meet promotion minimum amount");
        }
        return promotion;
    }

    private BigDecimal calculateDiscount(Promotion promotion, BigDecimal subtotal) {
        BigDecimal discount = subtotal.multiply(promotion.getDiscountPercentage())
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return discount.compareTo(subtotal) > 0 ? subtotal : discount;
    }

    private void releasePromotionUsage(Booking booking) {
        Promotion promotion = booking.getPromotion();
        if (promotion == null || promotion.getPromotionId() == null) {
            return;
        }
        Promotion locked = promotionRepository.findByIdForUpdate(promotion.getPromotionId())
                .orElse(promotion);
        int usedCount = locked.getPromotionUsedCount() == null
                ? 0
                : locked.getPromotionUsedCount();
        if (usedCount > 0) {
            locked.setPromotionUsedCount(usedCount - 1);
            promotionRepository.save(locked);
        }
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

    private Booking findOwned(Long id, AuthPrincipal principal) {
        Booking booking = find(id);
        ensureOwner(booking, principal);
        return booking;
    }

    private Booking findOwnedForUpdate(Long id, AuthPrincipal principal) {
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Booking not found"));
        ensureOwner(booking, principal);
        return booking;
    }

    private void ensureOwner(Booking booking, AuthPrincipal principal) {
        if (isAdmin(principal)) {
            return;
        }
        if (booking.getAccount() == null
                || !principal.id().equals(booking.getAccount().getAccountId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Booking not found");
        }
    }

    private boolean isAdmin(AuthPrincipal principal) {
        return ADMIN_ROLE.equals(principal.role());
    }
}
