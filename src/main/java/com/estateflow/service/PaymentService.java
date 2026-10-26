package com.estateflow.service;

import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.dao.*;
import com.estateflow.dto.OverdueRentRow;
import com.estateflow.dto.PaymentReportRow;
import com.estateflow.exception.*;
import com.estateflow.model.Payment;
import com.estateflow.model.RentCharge;
import com.estateflow.model.enums.PaymentMethod;
import com.estateflow.service.rules.Validators;
import com.estateflow.util.MoneyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Rent payments: BR-20 to BR-25. */
public class PaymentService {

    static final int ROW_LIMIT = 500;

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentDao paymentDao;
    private final RentChargeDao chargeDao;
    private final ReportingDao reportingDao;
    private final TransactionManager tx;
    private final AuditService audit;
    private final Clock clock;

    private final Authorizer auth;

    public PaymentService(PaymentDao paymentDao, RentChargeDao chargeDao, ReportingDao reportingDao,
                          TransactionManager tx, AuditService audit, Clock clock,
            Authorizer auth) {
        this.auth = auth;
        this.paymentDao = paymentDao;
        this.chargeDao = chargeDao;
        this.reportingDao = reportingDao;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * BR-20 to BR-24. Inside one transaction the charge row is locked first (its balance is then
     * read), so the overpayment check and the insert are atomic.
     */
    public Payment recordPayment(Long chargeId, BigDecimal amount, LocalDate paymentDate,
                                 PaymentMethod method, String referenceNo, String notes)
            throws EstateFlowException {
        auth.require(Permission.RECORD_PAYMENTS);
        if (chargeId == null) {
            throw new ValidationException("Select a rent charge.", "charge");
        }
        Validators.validatePositiveMoney(amount, "amount", "Payment amount must be greater than zero");
        if (paymentDate == null) {
            throw new ValidationException("Payment date is required.", "paymentDate");
        }
        LocalDate today = LocalDate.now(clock);
        if (paymentDate.isAfter(today)) {
            throw new ValidationException("Payment date cannot be in the future.", "paymentDate");
        }
        if (method == null) {
            throw new ValidationException("Select a payment method.", "paymentMethod");
        }
        if (referenceNo != null && referenceNo.length() > 60) {
            throw new ValidationException("Reference number cannot exceed 60 characters.", "referenceNo");
        }
        if (notes != null && notes.length() > 255) {
            throw new ValidationException("Notes cannot exceed 255 characters.", "notes");
        }

        Payment saved = tx.inTransaction(() -> {
            RentCharge charge = chargeDao.findByIdForUpdate(chargeId)
                    .orElseThrow(() -> new EntityNotFoundException("Rent charge " + chargeId + " no longer exists."));
            if (charge.getCancelledAt() != null) {
                throw new BusinessRuleException("The rent charge for the period starting "
                        + charge.getPeriodStart() + " was cancelled and cannot receive payments.", "BR-20");
            }
            if (!charge.isPayable(today)) {
                throw new BusinessRuleException("Rent for the period starting " + charge.getPeriodStart()
                        + " is not due until " + charge.getDueDate() + ". Advance payments are not accepted.", "BR-20");
            }
            BigDecimal balance = charge.getBalance();
            if (amount.compareTo(balance) > 0) {
                throw new BusinessRuleException("Payment of " + MoneyUtil.format(amount)
                        + " exceeds the remaining balance of " + MoneyUtil.format(balance)
                        + " for the period starting " + charge.getPeriodStart() + ".", "BR-21");
            }
            Payment inserted = paymentDao.insert(new Payment(chargeId, amount, paymentDate, method,
                    blankToNull(referenceNo), blankToNull(notes)));
            audit.record("PAYMENT", inserted.getPaymentId(), "RECORDED", null, AuditService.details("chargeId", chargeId,
                    "amount", amount, "paymentDate", paymentDate, "method", method.name()));
            return inserted;
        });
        log.info("Payment {} recorded against charge {}", saved.getPaymentId(), chargeId);
        return saved;
    }

    /** BR-25: void a wrong payment; never edit or delete it. */
    public void voidPayment(Long paymentId, String reason) throws EstateFlowException {
        auth.require(Permission.RECORD_PAYMENTS);
        if (paymentId == null) {
            throw new ValidationException("Select a payment.", "payment");
        }
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > 255) {
            throw new ValidationException("A void reason of 1 to 255 characters is required.", "reason");
        }
        tx.runInTransaction(() -> {
            Payment payment = paymentDao.findById(paymentId)
                    .orElseThrow(() -> new EntityNotFoundException("Payment " + paymentId + " no longer exists."));
            if (payment.isVoid()) {
                throw new BusinessRuleException("Payment " + payment.receiptNumber() + " is already voided.", "BR-25");
            }
            int rows = paymentDao.markVoided(paymentId, trimmed, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            if (rows == 0) {
                throw new BusinessRuleException("Payment " + payment.receiptNumber()
                        + " was already voided by another action.", "BR-25");
            }
            audit.record("PAYMENT", paymentId, "VOIDED", AuditService.details("status", "VALID", "amount", payment.getAmount()),
                    AuditService.details("status", "VOID", "reason", trimmed));
        });
        log.info("Payment {} voided", paymentId);
    }

    /** Filtered, capped payment rows; null dates / null voided flag mean "no filter". */
    public List<PaymentReportRow> search(String text, LocalDate from, LocalDate to, Boolean voidedOrNull)
            throws ValidationException {
        auth.require(Permission.VIEW_DATA);
        if (from != null && to != null && to.isBefore(from)) {
            throw new ValidationException("The end date cannot be before the start date.", "dateRange");
        }
        return reportingDao.searchPaymentRows(text, from, to, voidedOrNull, ROW_LIMIT);
    }

    public List<RentCharge> findCharges(Long agreementId) {
        auth.require(Permission.VIEW_DATA);
        return chargeDao.findByAgreement(agreementId);
    }

    public List<Payment> findPayments(Long chargeId) {
        auth.require(Permission.VIEW_DATA);
        return paymentDao.findByCharge(chargeId);
    }

    /** Outstanding balance of an agreement: balances of live charges already due (Section 5.4). */
    public BigDecimal outstandingBalance(Long agreementId) {
        auth.require(Permission.VIEW_DATA);
        LocalDate today = LocalDate.now(clock);
        return chargeDao.findByAgreement(agreementId).stream()
                .filter(c -> c.isPayable(today))
                .map(RentCharge::getBalance)
                .reduce(MoneyUtil.ZERO, BigDecimal::add);
    }

    public List<OverdueRentRow> findOverdue() {
        auth.require(Permission.VIEW_DATA);
        return reportingDao.findOverdueRows(LocalDate.now(clock));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
