package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.InvoiceRequest;
import com.j2ee.tour.dto.response.InvoiceResponse;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Invoice;
import com.j2ee.tour.model.Promotion;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.InvoiceRepository;
import com.j2ee.tour.repository.PromotionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final BookingRepository bookingRepository;
    private final PromotionRepository promotionRepository;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          BookingRepository bookingRepository,
                          PromotionRepository promotionRepository) {
        this.invoiceRepository = invoiceRepository;
        this.bookingRepository = bookingRepository;
        this.promotionRepository = promotionRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> list(Pageable pageable) {
        return PageResponse.from(invoiceRepository.findAll(pageable), InvoiceResponse::from);
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
        invoice.setInvoiceCreatedAt(LocalDateTime.now());
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
}
