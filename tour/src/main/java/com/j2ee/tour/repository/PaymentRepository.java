package com.j2ee.tour.repository;

import com.j2ee.tour.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPaymentProviderTxnId(String providerTransactionId);

    @Query("select p.booking.bookingId from Payment p where p.paymentId = :id")
    Optional<Long> findBookingIdByPaymentId(@Param("id") Long id);

    @Query("select p.booking.bookingId from Payment p "
            + "where p.paymentMethod = 'VNPAY' and p.paymentProviderTxnId = :reference")
    Optional<Long> findVnpayBookingId(@Param("reference") String reference);

    List<Payment> findByBookingBookingIdAndPaymentMethodAndPaymentProviderTxnIdStartingWith(
            Long bookingId, String method, String prefix);

    Optional<Payment> findFirstByBookingBookingIdAndPaymentStatusOrderByPaymentIdDesc(
            Long bookingId, String paymentStatus);

    List<Payment> findByBookingBookingId(Long bookingId);

    boolean existsByBookingBookingId(Long bookingId);

    long countByPaymentStatus(String status);

    @Query("select coalesce(sum(payment.paymentAmount), 0) from Payment payment "
            + "where payment.paymentStatus = :status")
    BigDecimal sumByPaymentStatus(@Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.paymentId = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Query("select coalesce(sum(payment.paymentAmount), 0) from Payment payment "
            + "where payment.booking.bookingId = :bookingId and payment.paymentStatus = 'SUCCESS'")
    java.math.BigDecimal sumSuccessfulPayments(@Param("bookingId") Long bookingId);

    @Query("select coalesce(sum(p.paymentAmount), 0) from Payment p "
            + "where p.paymentStatus = 'SUCCESS' and p.paymentPaidAt between :from and :to")
    BigDecimal sumSuccessfulPaymentsBetween(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    @Query("select count(p) from Payment p "
            + "where p.paymentStatus = 'SUCCESS' and p.paymentPaidAt between :from and :to")
    long countSuccessfulPaymentsBetween(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    @Query("select cast(p.paymentPaidAt as date), coalesce(sum(p.paymentAmount), 0) from Payment p "
            + "where p.paymentStatus = 'SUCCESS' and p.paymentPaidAt between :from and :to "
            + "group by cast(p.paymentPaidAt as date) order by cast(p.paymentPaidAt as date)")
    List<Object[]> dailySuccessfulPayments(@Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);
}
