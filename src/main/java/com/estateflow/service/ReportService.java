package com.estateflow.service;

import com.estateflow.dao.ReportingDao;
import com.estateflow.dto.*;
import com.estateflow.exception.ReportExportException;
import com.estateflow.exception.ValidationException;
import com.estateflow.model.enums.MaintenanceStatus;
import com.estateflow.report.*;
import com.estateflow.report.pdf.PdfTableReport;
import com.estateflow.util.DateFormats;
import com.estateflow.util.MoneyUtil;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Fetches report rows and hands them with a ReportDefinition to the CSV or PDF writer. */
public class ReportService {

    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", java.util.Locale.ENGLISH);
    private static final DateTimeFormatter PDF_DATE = DateTimeFormatter.ofPattern("'D:'yyyyMMddHHmmss'Z'");

    private final ReportingDao reportingDao;
    private final CsvWriter csvWriter;
    private final Clock clock;
    private final int reminderDays;

    public ReportService(ReportingDao reportingDao, CsvWriter csvWriter, Clock clock) {
        this(reportingDao, csvWriter, clock, 7);
    }

    public ReportService(ReportingDao reportingDao, CsvWriter csvWriter, Clock clock, int reminderDays) {
        this.reportingDao = reportingDao;
        this.csvWriter = csvWriter;
        this.clock = clock;
        this.reminderDays = reminderDays;
    }

    /** @return number of data rows written */
    public int exportPayments(LocalDate from, LocalDate to, Path target) throws ReportExportException, ValidationException {
        return exportPayments(from, to, target, ReportFormat.CSV);
    }

    public int exportPayments(LocalDate from, LocalDate to, Path target, ReportFormat format)
            throws ReportExportException, ValidationException {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ValidationException("Choose a valid date range.", "dateRange");
        }
        List<PaymentReportRow> rows = reportingDao.findPaymentRows(from, to);
        BigDecimal valid = rows.stream().filter(r -> !r.voided()).map(PaymentReportRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long voided = rows.stream().filter(PaymentReportRow::voided).count();
        write(format, target, "Payments report", "Payments dated " + DateFormats.format(from) + " to " + DateFormats.format(to),
                List.of("Valid payments: " + (rows.size() - voided) + "   Total received: Rs. " + MoneyUtil.format(valid),
                        "Voided payments listed: " + voided + " (excluded from the total)"), new PaymentsReport(), rows);
        return rows.size();
    }

    public int exportOverdue(Path target) throws ReportExportException {
        return exportOverdue(target, ReportFormat.CSV);
    }

    public int exportOverdue(Path target, ReportFormat format) throws ReportExportException {
        LocalDate today = LocalDate.now(clock);
        List<OverdueRentRow> rows = reportingDao.findOverdueRows(today);
        BigDecimal total = rows.stream().map(OverdueRentRow::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        write(format, target, "Overdue rent", "As of " + DateFormats.format(today),
                List.of(rows.size() + " overdue charge(s)   Total outstanding: Rs. " + MoneyUtil.format(total)),
                new OverdueRentReport(), rows);
        return rows.size();
    }

    public int exportOccupancy(Path target) throws ReportExportException {
        return exportOccupancy(target, ReportFormat.CSV);
    }

    public int exportOccupancy(Path target, ReportFormat format) throws ReportExportException {
        LocalDate today = LocalDate.now(clock);
        List<PropertyOccupancyRow> rows = reportingDao.findOccupancyRows(today);
        long active = rows.stream().filter(PropertyOccupancyRow::active).count();
        long occupied = rows.stream().filter(r -> r.active() && r.occupied()).count();
        write(format, target, "Property occupancy", "As of " + DateFormats.format(today),
                List.of("Occupied " + occupied + " of " + active + " active properties"), new OccupancyReport(), rows);
        return rows.size();
    }

    public int exportMaintenance(MaintenanceStatus statusOrNull, Path target) throws ReportExportException {
        return exportMaintenance(statusOrNull, target, ReportFormat.CSV);
    }

    public int exportMaintenance(MaintenanceStatus statusOrNull, Path target, ReportFormat format) throws ReportExportException {
        List<MaintenanceReportRow> rows = reportingDao.findMaintenanceRows(statusOrNull);
        BigDecimal cost = rows.stream().filter(r -> r.cost() != null).map(MaintenanceReportRow::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        write(format, target, "Maintenance requests", statusOrNull == null ? "All statuses" : "Status: " + statusOrNull,
                List.of(rows.size() + " request(s)   Recorded cost: Rs. " + MoneyUtil.format(cost)), new MaintenanceReport(), rows);
        return rows.size();
    }

    public int exportRentReminders(Path target, ReportFormat format) throws ReportExportException {
        LocalDate today = LocalDate.now(clock);
        List<RentReminderRow> rows = reportingDao.findRentReminders(today, reminderDays);
        long overdue = rows.stream().filter(r -> r.category() == RentReminderRow.Category.OVERDUE).count();
        BigDecimal total = rows.stream().map(RentReminderRow::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        write(format, target, "Rent reminders", "Overdue rent and rent due within " + reminderDays + " days of "
                        + DateFormats.format(today),
                List.of(rows.size() + " charge(s), " + overdue + " overdue   Total unpaid: Rs. " + MoneyUtil.format(total)),
                new RentRemindersReport(), rows);
        return rows.size();
    }

    public List<String> reportNames() {
        return List.of(new PaymentsReport().fileName(), new OverdueRentReport().fileName(),
                new OccupancyReport().fileName(), new MaintenanceReport().fileName(), new RentRemindersReport().fileName());
    }

    private <T> void write(ReportFormat format, Path target, String title, String subtitle, List<String> summary,
                           ReportDefinition<T> definition, List<T> rows) throws ReportExportException {
        if (format == ReportFormat.PDF) {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
            String pdfDate = PDF_DATE.format(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            PdfTableReport.write(target, title, subtitle, summary, definition, rows, GENERATED.format(now), pdfDate);
        } else {
            csvWriter.write(target, definition, rows);
        }
    }
}
