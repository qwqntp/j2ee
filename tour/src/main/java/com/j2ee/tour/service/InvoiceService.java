package com.j2ee.tour.service;

import com.j2ee.tour.common.AdminListPageRequest;
import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.InvoiceRequest;
import com.j2ee.tour.dto.response.AdminInvoiceListItem;
import com.j2ee.tour.dto.response.InvoiceResponse;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Invoice;
import com.j2ee.tour.model.Promotion;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.BookingSpecifications;
import com.j2ee.tour.repository.InvoiceRepository;
import com.j2ee.tour.repository.PromotionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class InvoiceService {
    private static final Set<String> STATUSES = Set.of("PAID", "UNPAID");
    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "invoiceCreatedAt",
            "totalAmount", "totalAmount",
            "id", "invoiceId");

    private final InvoiceRepository invoiceRepository;
    private final BookingRepository bookingRepository;
    private final PromotionRepository promotionRepository;
    private final Clock clock;
    private final int maxPageSize;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          BookingRepository bookingRepository,
                          PromotionRepository promotionRepository,
                          Clock clock,
                          @Value("${app.admin.list.max-page-size:50}") int maxPageSize) {
        this.invoiceRepository = invoiceRepository;
        this.bookingRepository = bookingRepository;
        this.promotionRepository = promotionRepository;
        this.clock = clock;
        this.maxPageSize = maxPageSize;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminInvoiceListItem> list(Pageable pageable, String status, String q) {
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim();
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid invoice status");
        }
        Pageable effective = AdminListPageRequest.normalize(
                pageable, maxPageSize, SORTABLE, "createdAt", "id");
        String pattern = q == null || q.isBlank() ? null : BookingSpecifications.likePattern(q);
        return PageResponse.from(invoiceRepository.findAdminPage(normalizedStatus, pattern, effective));
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> listMine(Long accountId, Pageable pageable) {
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "invoiceCreatedAt")
                                .and(Sort.by(Sort.Direction.DESC, "invoiceId")));
        Page<Invoice> invoices = invoiceRepository
                .findByBookingAccountAccountId(accountId, effective);
        return PageResponse.from(invoices, InvoiceResponse::from);
    }

    @Transactional
    public InvoiceResponse createForPaidBooking(Booking booking) {
        return createForPaidBooking(booking, null);
    }

    @Transactional
    public InvoiceResponse createForPaidBooking(Booking booking, String settlementMethod) {
        var existing = settlementMethod == null
                ? invoiceRepository.findByBookingBookingId(booking.getBookingId())
                : invoiceRepository.findByBookingIdForUpdate(booking.getBookingId());
        return existing
                .map(invoice -> {
                    if (settlementMethod != null && "UNPAID".equals(invoice.getStatus())) {
                        invoice.setStatus("PAID");
                        invoice.setPaymentMethod(settlementMethod);
                        return InvoiceResponse.from(invoiceRepository.save(invoice));
                    }
                    return InvoiceResponse.from(invoice);
                })
                .orElseGet(() -> {
                    Invoice invoice = new Invoice();
                    invoice.setInvoiceCreatedAt(LocalDateTime.now(clock));
                    invoice.setStatus("PAID");
                    invoice.setDiscountAmount(nonNull(booking.getBookingDiscountAmount()));
                    invoice.setTaxAmount(BigDecimal.ZERO);
                    invoice.setServiceFee(BigDecimal.ZERO);
                    invoice.setTotalAmount(nonNull(booking.getBookingTotalAmount()));
                    invoice.setPaymentMethod(settlementMethod);
                    invoice.setBooking(booking);

                    if (booking.getTravelers() != null && !booking.getTravelers().isEmpty()) {
                        var traveler = booking.getTravelers().get(0);
                        invoice.setCustomerName(traveler.getTravelerName());
                        invoice.setCustomerPhone(traveler.getTravelerPhone());
                        if (traveler.getTravelerEmail() != null) {
                            invoice.setCustomerEmail(traveler.getTravelerEmail());
                        }
                    }
                    if (booking.getAccount() != null) {
                        if (invoice.getCustomerName() == null) {
                            invoice.setCustomerName(booking.getAccount().getUsername());
                        }
                        if (invoice.getCustomerEmail() == null) {
                            invoice.setCustomerEmail(booking.getAccount().getAccountEmail());
                        }
                    }
                    if (booking.getPromotion() != null) {
                        invoice.getPromotions().add(booking.getPromotion());
                    }
                    return InvoiceResponse.from(invoiceRepository.save(invoice));
                });
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long id) {
        return InvoiceResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getByBooking(Long bookingId) {
        return InvoiceResponse.from(invoiceRepository.findByBookingBookingId(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Invoice not found for booking")));
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceCreatedAt(LocalDateTime.now(clock));
        apply(invoice, request);
        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse update(Long id, InvoiceRequest request) {
        Invoice invoice = find(id);
        apply(invoice, request);
        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }

    @Transactional
    public void delete(Long id) {
        invoiceRepository.delete(find(id));
    }

    private void apply(Invoice invoice, InvoiceRequest request) {
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new NoSuchElementException("Booking not found"));
        invoice.setBooking(booking);
        invoice.setStatus(request.status());
        invoice.setDiscountAmount(request.discountAmount());
        invoice.setTaxAmount(request.taxAmount());
        invoice.setServiceFee(request.serviceFee());
        invoice.setTotalAmount(request.totalAmount());
        invoice.setPaymentMethod(request.paymentMethod());
        invoice.setCustomerName(request.customerName());
        invoice.setCustomerPhone(request.customerPhone());
        invoice.setCustomerEmail(request.customerEmail());
        invoice.setPromotions(resolvePromotions(request.promotionIds()));
    }

    private List<Promotion> resolvePromotions(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Promotion> promotions = promotionRepository.findAllById(ids);
        if (promotions.size() != ids.stream().distinct().count()) {
            throw new NoSuchElementException("One or more promotions were not found");
        }
        return new ArrayList<>(promotions);
    }

    private Invoice find(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Invoice not found"));
    }

    private BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
