package com.estateflow.dto;

import com.estateflow.model.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Cash-basis profitability for a date range. rentReceived is money actually received (valid payments dated in the
 * range); rentCharged is rent billed with a due date in the range and is shown for comparison only. NOI is
 * rentReceived minus operating expenses; it is not total profit.
 */
public record ProfitabilityReport(LocalDate from, LocalDate to, Long propertyIdOrNull, BigDecimal rentCharged,
                                  BigDecimal rentReceived, BigDecimal expenses, BigDecimal noi,
                                  BigDecimal maintenanceFromRequests, List<CategoryAmount> categories,
                                  List<PropertyProfit> properties, List<MonthlyProfit> months) {

    public record CategoryAmount(ExpenseCategory category, BigDecimal amount) {
    }

    public record PropertyProfit(long propertyId, String propertyName, boolean active, BigDecimal rentCharged,
                                 BigDecimal rentReceived, BigDecimal expenses, BigDecimal noi) {
    }

    public record MonthlyProfit(YearMonth month, BigDecimal income, BigDecimal expenses) {
        public BigDecimal noi() {
            return income.subtract(expenses);
        }
    }

    public boolean isEmpty() {
        return rentReceived.signum() == 0 && expenses.signum() == 0 && rentCharged.signum() == 0;
    }
}
