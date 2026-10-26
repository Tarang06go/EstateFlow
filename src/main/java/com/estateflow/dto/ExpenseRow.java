package com.estateflow.dto;

import com.estateflow.model.Expense;

/** An expense with the display names the list screen needs. */
public record ExpenseRow(Expense expense, String propertyName, String maintenanceTitle) {
}
