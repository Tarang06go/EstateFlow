package com.estateflow.support;

import com.estateflow.dao.DepositDao;
import com.estateflow.dto.DepositActivityRow;
import com.estateflow.dto.DepositRow;
import com.estateflow.dto.DepositTotals;
import com.estateflow.exception.DuplicateKeyException;
import com.estateflow.model.DepositTransaction;
import com.estateflow.model.RentalAgreement;
import com.estateflow.model.enums.DepositType;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** In-memory deposit ledger for unit tests; follows the same rules as the SQL (voided rows ignored, one deposit per agreement). */
public class FakeDepositDao implements DepositDao {

    public final Map<Long, RentalAgreement> agreements = new HashMap<>();
    private final Map<Long, Long> depositByAgreement = new LinkedHashMap<>();
    public final List<DepositTransaction> ledger = new ArrayList<>();
    private long nextDeposit = 1, nextTxn = 1;

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<Long> findDepositId(long agreementId) {
        return Optional.ofNullable(depositByAgreement.get(agreementId));
    }

    @Override
    public long insertDeposit(long agreementId) {
        if (depositByAgreement.containsKey(agreementId)) {
            throw new DuplicateKeyException("dup", new SQLException("dup", "23000", 1062), "uq_deposit_agreement");
        }
        long id = nextDeposit++;
        depositByAgreement.put(agreementId, id);
        return id;
    }

    @Override
    public boolean lockDeposit(long depositId) {
        return depositByAgreement.containsValue(depositId);
    }

    @Override
    public DepositTotals totals(long depositId) {
        BigDecimal c = BigDecimal.ZERO, in = BigDecimal.ZERO, d = BigDecimal.ZERO, r = BigDecimal.ZERO, out = BigDecimal.ZERO;
        for (DepositTransaction t : ledger) {
            if (t.depositId() != depositId || t.isVoided()) {
                continue;
            }
            switch (t.type()) {
                case COLLECTION -> c = c.add(t.amount());
                case TRANSFER_IN -> in = in.add(t.amount());
                case DEDUCTION -> d = d.add(t.amount());
                case REFUND -> r = r.add(t.amount());
                case TRANSFER_OUT -> out = out.add(t.amount());
            }
        }
        return new DepositTotals(c, in, d, r, out);
    }

    @Override
    public Optional<LocalDate> firstCollectionDate(long depositId) {
        return ledger.stream().filter(t -> t.depositId() == depositId && t.type() == DepositType.COLLECTION && !t.isVoided())
                .map(DepositTransaction::date).min(Comparator.naturalOrder());
    }

    @Override
    public long insertTransaction(DepositTransaction t) {
        long id = nextTxn++;
        ledger.add(new DepositTransaction(id, t.depositId(), t.type(), t.amount(), t.date(), t.method(), t.referenceNo(), t.reason(),
                t.notes(), t.relatedAgreementId(), LocalDateTime.of(2026, 10, 10, 8, 0), null, null));
        return id;
    }

    @Override
    public Optional<DepositTransaction> findTransaction(long txnId) {
        return ledger.stream().filter(t -> t.txnId() == txnId).findFirst();
    }

    @Override
    public int voidTransaction(long txnId, String reason, LocalDateTime at) {
        for (int i = 0; i < ledger.size(); i++) {
            DepositTransaction t = ledger.get(i);
            if (t.txnId() == txnId && !t.isVoided()) {
                ledger.set(i, new DepositTransaction(t.txnId(), t.depositId(), t.type(), t.amount(), t.date(), t.method(), t.referenceNo(),
                        t.reason(), t.notes(), t.relatedAgreementId(), t.createdAt(), at, reason));
                return 1;
            }
        }
        return 0;
    }

    @Override
    public List<DepositTransaction> transactions(long depositId) {
        return ledger.stream().filter(t -> t.depositId() == depositId).toList();
    }

    @Override
    public List<DepositRow> rows(Long propertyId, Long tenantId, LocalDate today) {
        List<DepositRow> rows = new ArrayList<>();
        for (RentalAgreement a : agreements.values()) {
            if ((propertyId != null && !propertyId.equals(a.getPropertyId())) || (tenantId != null && !tenantId.equals(a.getTenantId()))) {
                continue;
            }
            Long dep = depositByAgreement.get(a.getAgreementId());
            boolean pending = a.getSecurityDeposit().signum() > 0 && !a.getEffectiveEndDate().isBefore(today);
            if (dep != null || pending) {
                rows.add(row(a, dep));
            }
        }
        return rows;
    }

    @Override
    public Optional<DepositRow> rowForAgreement(long agreementId, LocalDate today) {
        RentalAgreement a = agreements.get(agreementId);
        return a == null ? Optional.empty() : Optional.of(row(a, depositByAgreement.get(agreementId)));
    }

    private DepositRow row(RentalAgreement a, Long depositId) {
        return new DepositRow(depositId, a.getAgreementId(), a.getPropertyId(), "Property " + a.getPropertyId(), a.getTenantId(),
                "Tenant " + a.getTenantId(), a.getSecurityDeposit(), depositId == null ? DepositTotals.EMPTY : totals(depositId),
                a.getStartDate(), a.getEffectiveEndDate(), a.getStatus(), false,
                depositId == null ? null : firstCollectionDate(depositId).orElse(null));
    }

    @Override
    public List<DepositActivityRow> activity(LocalDate from, LocalDate to, Long propertyId, Long tenantId) {
        List<DepositActivityRow> out = new ArrayList<>();
        for (DepositTransaction t : ledger) {
            if (t.isVoided() || t.type() == DepositType.TRANSFER_IN || t.type() == DepositType.TRANSFER_OUT
                    || t.date().isBefore(from) || t.date().isAfter(to)) {
                continue;
            }
            Long agreementId = depositByAgreement.entrySet().stream().filter(e -> e.getValue() == t.depositId()).map(Map.Entry::getKey).findFirst().orElse(0L);
            out.add(new DepositActivityRow(t, agreementId, "Property", "Tenant"));
        }
        return out;
    }
}
