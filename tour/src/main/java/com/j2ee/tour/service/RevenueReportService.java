package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RevenueReportService {

    private final PaymentRepository paymentRepository;

    public List<RevenueRow> daily(LocalDate from, LocalDate to) {
        requireValid(from, to);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay().minusNanos(1);
        return paymentRepository.dailySuccessfulPayments(start, end).stream()
                .map(row -> new RevenueRow(toDate(row[0]), decimal(row[1])))
                .toList();
    }

    public String csv(LocalDate from, LocalDate to) {
        StringBuilder csv = new StringBuilder("\uFEFFdate,amount\n");
        for (RevenueRow row : daily(from, to)) {
            csv.append(row.date()).append(',').append(row.amount().toPlainString()).append('\n');
        }
        return csv.toString();
    }

    private static void requireValid(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
    }

    private static LocalDate toDate(Object value) {
        return value instanceof LocalDate date ? date : ((Date) value).toLocalDate();
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    public record RevenueRow(LocalDate date, BigDecimal amount) {
    }
}
