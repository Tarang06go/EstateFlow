package com.estateflow.dao;

import com.estateflow.model.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Read-only aggregate queries behind the profitability report. */
public interface ProfitabilityDao {

    record PropertyRef(long propertyId, String name, boolean active) {
    }

    record IncomeRow(long propertyId, YearMonth month, BigDecimal amount) {
    }

    /** {@code fromMaintenanceRequest} marks a resolved request's own cost that has no active expense linked to it. */
    record ExpenseAggRow(long propertyId, YearMonth month, ExpenseCategory category, BigDecimal amount,
                         boolean fromMaintenanceRequest) {
    }

    record ChargedRow(long propertyId, BigDecimal amount) {
    }

    List<PropertyRef> properties(Long propertyIdOrNull);

    /** Valid (not voided) rent payments by payment date; every payment belongs to a rent charge, so deposits are excluded. */
    List<IncomeRow> receivedRent(LocalDate from, LocalDate to, Long propertyIdOrNull);

    /** Active expenses by expense date plus unlinked resolved-maintenance costs by resolved date. */
    List<ExpenseAggRow> expenses(LocalDate from, LocalDate to, Long propertyIdOrNull);

    /** Non-cancelled rent charges by due date. */
    List<ChargedRow> chargedRent(LocalDate from, LocalDate to, Long propertyIdOrNull);
}
