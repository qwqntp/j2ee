package com.j2ee.tour.repository;

import com.j2ee.tour.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    @Query("select coalesce(sum(refund.refundAmount), 0) from Refund refund "
            + "where refund.payment.paymentId = :paymentId "
            + "and refund.refundStatus in ('REQUESTED', 'APPROVED', 'DONE')")
    BigDecimal sumReservedRefunds(@Param("paymentId") Long paymentId);
}