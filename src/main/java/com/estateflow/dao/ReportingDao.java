package com.estateflow.dao;

import com.estateflow.dto.*;
import com.estateflow.model.enums.MaintenanceStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Read-only aggregates and joined rows. Every date-dependent query receives its dates as parameters. */
public interface ReportingDao {
    DashboardStats dashboardStats(LocalDate today, int warningDays, LocalDate monthStart, LocalDate monthEnd);

    List<OverdueRentRow> findOverdueRows(LocalDate today);

    List<PropertyOccupancyRow> findOccupancyRows(LocalDate today);

    List<PaymentReportRow> findPaymentRows(LocalDate from, LocalDate to);

    /** Maintenance rows with property/tenant names; a null status returns all statuses. */
    List<MaintenanceReportRow> findMaintenanceRows(MaintenanceStatus statusOrNull);

    Optional<ReceiptContext> findReceiptContext(Long paymentId);

    // ---- v1.1 additions

    /** Valid payments per calendar month inside [from, to]; months without payments are absent. */
    List<MonthlyAmount> monthlyCollections(LocalDate from, LocalDate to);

    /** Recorded maintenance cost per month (IN_PROGRESS/RESOLVED, month of resolved or reported date). */
    List<MonthlyAmount> monthlyMaintenanceCosts(LocalDate from, LocalDate to);

    /** Property list rows, filtered and capped; blank text matches everything. */
    List<PropertyOccupancyRow> searchPropertyRows(String text, LocalDate today, int limit);

    /** Payment rows filtered by optional dates, optional voided flag and text; newest first, capped. */
    List<PaymentReportRow> searchPaymentRows(String text, LocalDate fromOrNull, LocalDate toOrNull,
                                             Boolean voidedOrNull, int limit);

    List<PaymentReportRow> findPaymentRowsForProperty(Long propertyId);

    List<MaintenanceReportRow> findMaintenanceRowsForProperty(Long propertyId);

    /** Outstanding balance (live charges already due) of one property. */
    java.math.BigDecimal outstandingForProperty(Long propertyId, LocalDate today);

    /** Unpaid live charges that are overdue or due on or before today + windowDays. One row per charge. */
    List<RentReminderRow> findRentReminders(LocalDate today, int windowDays);

    MaintenanceSummary maintenanceSummary(LocalDate monthsFrom, LocalDate monthsTo);
}
