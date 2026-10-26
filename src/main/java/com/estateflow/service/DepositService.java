package com.estateflow.service;

import com.estateflow.dao.AgreementDao;
import com.estateflow.dao.DepositDao;
import com.estateflow.dao.TransactionManager;
import com.estateflow.dto.DepositActivityRow;
import com.estateflow.dto.DepositReport;
import com.estateflow.dto.DepositReport.GroupTotal;
import com.estateflow.dto.DepositRow;
import com.estateflow.dto.DepositTotals;
import com.estateflow.exception.*;
import com.estateflow.model.DepositTransaction;
import com.estateflow.model.RentalAgreement;
import com.estateflow.model.enums.DepositType;
import com.estateflow.model.enums.PaymentMethod;
import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Security deposits: a liability held for a tenant, never rental income. One deposit per agreement with an append-only
 * ledger (collection, deduction, refund, renewal carry-over); a mistake is voided, never edited or deleted. Reads need
 * VIEW_DATA, every change needs RECORD_PAYMENTS (ADMIN, MANAGER; VIEWER is read-only), and each change is audited with the
 * acting user in the same transaction. Balance checks run under a row lock so two changes cannot over-spend a deposit.
 */
public class DepositService {

    /** How the deposit report can be summarised. */
    public enum Grouping { AGREEMENT, TENANT, PROPERTY }

    static final LocalDate EARLIEST_DATE = LocalDate.of(2000, 1, 1);
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private final DepositDao depositDao;
    private final AgreementDao agreementDao;
    private final TransactionManager tx;
    private final AuditService audit;
    private final Clock clock;
    private final Authorizer auth;

    public DepositService(DepositDao depositDao, AgreementDao agreementDao, TransactionManager tx, AuditService audit,
                          Clock clock, Authorizer auth) {
        this.depositDao = depositDao;
        this.agreementDao = agreementDao;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
        this.auth = auth;
    }

    /** Fails with a setup message when the v1.5 migration has not been applied. */
    public void ensureReady() {
        auth.require(Permission.VIEW_DATA);
        if (!depositDao.isAvailable()) {
            throw new ConfigurationException("The security deposit tables are missing. Apply db/migrations/v1.5_deposits_leases.sql "
                    + "to the estateflow database (see README), then reopen this screen.");
        }
    }

    // ---------------------------------------------------------------- changes

    /** Records money received. The deposit record is created with the first collection. */
    public DepositRow collect(long agreementId, BigDecimal amount, LocalDate date, PaymentMethod method, String referenceNo,
                              String notes) throws EstateFlowException {
        auth.require(Permission.RECORD_PAYMENTS);
        BigDecimal amt = checkAmount(amount);
        LocalDate day = checkDate(date);
        if (method == null) {
            throw new ValidationException("Select a payment method.", "paymentMethod");
        }
        String ref = optional(referenceNo, 60, "Reference number", "referenceNo");
        String note = optional(notes, 500, "Notes", "notes");
        return tx.inTransaction(() -> {
            RentalAgreement a = agreement(agreementId);
            if (LocalDate.now(clock).isAfter(a.getEffectiveEndDate())) {
                throw new BusinessRuleException("This agreement has ended, so a new deposit cannot be collected on it.", "DEP-02");
            }
            long depositId = ensureDeposit(agreementId);
            DepositTotals totals = lock(depositId);
            BigDecimal required = a.getSecurityDeposit();
            if (required.signum() > 0 && totals.original().add(amt).compareTo(required) > 0) {
                throw new BusinessRuleException("The deposit agreed in the lease is " + MoneyUtil.format(required) + "; "
                        + MoneyUtil.format(totals.original()) + " is already held, so " + MoneyUtil.format(amt)
                        + " would exceed it.", "DEP-03");
            }
            long txnId = depositDao.insertTransaction(txn(depositId, DepositType.COLLECTION, amt, day, method, ref, null, note, null));
            audit.record("DEPOSIT", depositId, "COLLECTED", null, snapshot(agreementId, txnId, DepositType.COLLECTION, amt, day, ref, null));
            return position(agreementId);
        });
    }

    /** Keeps part of the deposit (damage, arrears). Needs a reason; cannot exceed the balance. */
    public DepositRow deduct(long agreementId, BigDecimal amount, LocalDate date, String reason, String referenceNo,
                             String notes) throws EstateFlowException {
        auth.require(Permission.RECORD_PAYMENTS);
        BigDecimal amt = checkAmount(amount);
        LocalDate day = checkDate(date);
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty() || why.length() > 255) {
            throw new ValidationException("Enter the reason for the deduction (1 to 255 characters).", "reason");
        }
        String ref = optional(referenceNo, 60, "Supporting reference", "referenceNo");
        String note = optional(notes, 500, "Notes", "notes");
        return tx.inTransaction(() -> {
            agreement(agreementId);
            long depositId = existingDeposit(agreementId);
            DepositTotals totals = lock(depositId);
            requireNotBeforeCollection(depositId, day);
            requireWithinBalance(totals, amt, "deduct");
            long txnId = depositDao.insertTransaction(txn(depositId, DepositType.DEDUCTION, amt, day, null, ref, why, note, null));
            audit.record("DEPOSIT", depositId, "DEDUCTED", null, snapshot(agreementId, txnId, DepositType.DEDUCTION, amt, day, ref, why));
            return position(agreementId);
        });
    }

    /** Returns part or all of the balance to the tenant, once the lease has ended or been terminated. */
    public DepositRow refund(long agreementId, BigDecimal amount, LocalDate date, PaymentMethod method, String referenceNo,
                             String notes) throws EstateFlowException {
        auth.require(Permission.RECORD_PAYMENTS);
        BigDecimal amt = checkAmount(amount);
        LocalDate day = checkDate(date);
        if (method == null) {
            throw new ValidationException("Select how the refund was paid.", "paymentMethod");
        }
        String ref = optional(referenceNo, 60, "Reference number", "referenceNo");
        String note = optional(notes, 500, "Notes", "notes");
        return tx.inTransaction(() -> {
            RentalAgreement a = agreement(agreementId);
            if (LocalDate.now(clock).isBefore(a.getEffectiveEndDate())) {
                throw new BusinessRuleException("The deposit can be refunded only after the lease ends (" + a.getEffectiveEndDate()
                        + ") or is terminated.", "DEP-05");
            }
            long depositId = existingDeposit(agreementId);
            DepositTotals totals = lock(depositId);
            requireNotBeforeCollection(depositId, day);
            requireWithinBalance(totals, amt, "refund");
            long txnId = depositDao.insertTransaction(txn(depositId, DepositType.REFUND, amt, day, method, ref, null, note, null));
            audit.record("DEPOSIT", depositId, "REFUNDED", null, snapshot(agreementId, txnId, DepositType.REFUND, amt, day, ref, null));
            return position(agreementId);
        });
    }

    /** Corrects a mistaken entry without erasing it: the entry stays in the ledger, flagged void with a reason. */
    public DepositRow voidTransaction(long txnId, String reason) throws EstateFlowException {
        auth.require(Permission.RECORD_PAYMENTS);
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty() || why.length() > 255) {
            throw new ValidationException("Enter a reason for voiding (1 to 255 characters).", "reason");
        }
        return tx.inTransaction(() -> {
            DepositTransaction first = depositDao.findTransaction(txnId)
                    .orElseThrow(() -> new EntityNotFoundException("That deposit entry no longer exists."));
            DepositTotals totals = lock(first.depositId());
            DepositTransaction t = depositDao.findTransaction(txnId).orElseThrow();
            if (t.isVoided()) {
                throw new BusinessRuleException("This entry is already void.", "DEP-08");
            }
            if (t.type() == DepositType.TRANSFER_IN || t.type() == DepositType.TRANSFER_OUT) {
                throw new BusinessRuleException("Entries created by a lease renewal cannot be voided.", "DEP-07");
            }
            if (t.type() == DepositType.COLLECTION && totals.balance().subtract(t.amount()).signum() < 0) {
                throw new BusinessRuleException("This collection has already been partly deducted or refunded, so voiding it "
                        + "would leave a negative balance. Void the later deduction or refund first.", "DEP-06");
            }
            if (depositDao.voidTransaction(txnId, why, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)) == 0) {
                throw new BusinessRuleException("This entry is already void.", "DEP-08");
            }
            audit.record("DEPOSIT", t.depositId(), "TXN_VOIDED", AuditService.details("txnId", txnId, "type", t.type().name(),
                    "amount", t.amount().toPlainString()), AuditService.details("reason", why));
            return depositDao.rows(null, null, LocalDate.now(clock)).stream()
                    .filter(r -> r.depositId() != null && r.depositId() == t.depositId()).findFirst().orElseThrow();
        });
    }

    // ---------------------------------------------------------------- reads

    /** The agreement's deposit position (also when nothing was collected yet). */
    public Optional<DepositRow> findPosition(long agreementId) {
        auth.require(Permission.VIEW_DATA);
        return depositDao.rowForAgreement(agreementId, LocalDate.now(clock));
    }

    /** All entries of the agreement's deposit including voided ones, oldest first; empty when none. */
    public List<DepositTransaction> transactions(long agreementId) {
        auth.require(Permission.VIEW_DATA);
        return depositDao.findDepositId(agreementId).map(depositDao::transactions).orElse(List.of());
    }

    /** Deposits held, liability and period activity from live records (VIEW_DATA). */
    public DepositReport report(LocalDate from, LocalDate to, Long propertyIdOrNull, Long tenantIdOrNull) throws ValidationException {
        auth.require(Permission.VIEW_DATA);
        if (from == null || to == null) {
            throw new ValidationException("Choose a start and an end date.", "dateRange");
        }
        if (to.isBefore(from)) {
            throw new ValidationException("The end date cannot be before the start date.", "dateRange");
        }
        List<DepositRow> rows = depositDao.rows(propertyIdOrNull, tenantIdOrNull, LocalDate.now(clock));
        List<DepositActivityRow> activity = depositDao.activity(from, to, propertyIdOrNull, tenantIdOrNull);
        BigDecimal liability = rows.stream().map(r -> r.totals().balance()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DepositReport(from, to, liability, sum(activity, DepositType.COLLECTION), sum(activity, DepositType.DEDUCTION),
                sum(activity, DepositType.REFUND), rows, activity);
    }

    /** Summarises deposit rows by agreement, tenant or property. */
    public static List<GroupTotal> group(List<DepositRow> rows, Grouping grouping) {
        Map<String, List<DepositRow>> groups = new TreeMap<>();
        for (DepositRow r : rows) {
            String key = switch (grouping) {
                case AGREEMENT -> String.format("#%06d  %s - %s", r.agreementId(), r.propertyName(), r.tenantName());
                case TENANT -> r.tenantName();
                case PROPERTY -> r.propertyName();
            };
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        List<GroupTotal> out = new ArrayList<>();
        groups.forEach((label, list) -> out.add(new GroupTotal(label, list.size(),
                total(list, DepositTotals::original), total(list, DepositTotals::deducted), total(list, DepositTotals::refunded),
                total(list, DepositTotals::transferredOut), total(list, DepositTotals::balance))));
        return out;
    }

    // ---------------------------------------------------------------- internals

    private DepositRow position(long agreementId) {
        return depositDao.rowForAgreement(agreementId, LocalDate.now(clock)).orElseThrow();
    }

    private RentalAgreement agreement(long agreementId) throws EntityNotFoundException {
        return agreementDao.findById(agreementId).orElseThrow(() -> new EntityNotFoundException("That agreement no longer exists."));
    }

    private long ensureDeposit(long agreementId) {
        Optional<Long> existing = depositDao.findDepositId(agreementId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return depositDao.insertDeposit(agreementId);
        } catch (DuplicateKeyException race) {
            return depositDao.findDepositId(agreementId).orElseThrow(() -> race);
        }
    }

    private long existingDeposit(long agreementId) throws BusinessRuleException {
        return depositDao.findDepositId(agreementId).orElseThrow(() -> new BusinessRuleException(
                "No deposit has been collected for this agreement yet.", "DEP-01"));
    }

    private DepositTotals lock(long depositId) throws EntityNotFoundException {
        if (!depositDao.lockDeposit(depositId)) {
            throw new EntityNotFoundException("That deposit no longer exists.");
        }
        return depositDao.totals(depositId);
    }

    private void requireWithinBalance(DepositTotals totals, BigDecimal amount, String verb) throws BusinessRuleException {
        BigDecimal balance = totals.balance();
        if (amount.compareTo(balance) > 0) {
            throw new BusinessRuleException("Cannot " + verb + " " + MoneyUtil.format(amount) + ": only " + MoneyUtil.format(balance)
                    + " remains in the deposit.", "DEP-04");
        }
    }

    private void requireNotBeforeCollection(long depositId, LocalDate day) throws BusinessRuleException {
        Optional<LocalDate> first = depositDao.firstCollectionDate(depositId);
        if (first.isPresent() && day.isBefore(first.get())) {
            throw new BusinessRuleException("The date cannot be before the deposit was collected (" + first.get() + ").", "DEP-09");
        }
    }

    private BigDecimal checkAmount(BigDecimal amount) throws ValidationException {
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException("Amount must be greater than zero.", "amount");
        }
        if (!MoneyUtil.hasValidScale(amount)) {
            throw new ValidationException("Amount can have at most 2 decimal places.", "amount");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ValidationException("Amount is too large.", "amount");
        }
        return amount.setScale(2);
    }

    private LocalDate checkDate(LocalDate date) throws ValidationException {
        if (date == null) {
            throw new ValidationException("Date is required.", "date");
        }
        if (date.isAfter(LocalDate.now(clock))) {
            throw new ValidationException("Date cannot be in the future.", "date");
        }
        if (date.isBefore(EARLIEST_DATE)) {
            throw new ValidationException("Date cannot be before " + EARLIEST_DATE + ".", "date");
        }
        return date;
    }

    private static String optional(String text, int max, String label, String field) throws ValidationException {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        if (t.length() > max) {
            throw new ValidationException(label + " can be at most " + max + " characters.", field);
        }
        return t;
    }

    private static DepositTransaction txn(long depositId, DepositType type, BigDecimal amount, LocalDate date, PaymentMethod method,
                                          String ref, String reason, String notes, Long related) {
        return new DepositTransaction(null, depositId, type, amount, date, method, ref, reason, notes, related, null, null, null);
    }

    private static Map<String, Object> snapshot(long agreementId, long txnId, DepositType type, BigDecimal amount, LocalDate date,
                                                String ref, String reason) {
        Map<String, Object> m = AuditService.details("agreementId", agreementId, "txnId", txnId, "type", type.name(),
                "amount", amount.toPlainString(), "date", String.valueOf(date));
        if (ref != null) {
            m.put("reference", ref);
        }
        if (reason != null) {
            m.put("reason", reason);
        }
        return m;
    }

    private static BigDecimal sum(List<DepositActivityRow> rows, DepositType type) {
        return rows.stream().filter(r -> r.txn().type() == type).map(r -> r.txn().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal total(List<DepositRow> rows, java.util.function.Function<DepositTotals, BigDecimal> part) {
        return rows.stream().map(r -> part.apply(r.totals())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
