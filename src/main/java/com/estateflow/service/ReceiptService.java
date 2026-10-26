package com.estateflow.service;

import com.estateflow.security.Authorizer;
import com.estateflow.security.Permission;
import com.estateflow.dao.PaymentDao;
import com.estateflow.dao.ReportingDao;
import com.estateflow.dto.PaymentReceiptData;
import com.estateflow.dto.ReceiptContext;
import com.estateflow.exception.*;
import com.estateflow.model.Payment;
import com.estateflow.report.ReceiptWriter;
import com.estateflow.report.pdf.PdfReceiptWriter;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Builds receipt data for a valid payment (BR-25) and writes it. */
public class ReceiptService {

    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", java.util.Locale.ENGLISH);
    private static final DateTimeFormatter PDF_DATE = DateTimeFormatter.ofPattern("'D:'yyyyMMddHHmmss'Z'");

    private final PaymentDao paymentDao;
    private final ReportingDao reportingDao;
    private final ReceiptWriter receiptWriter;
    private final PdfReceiptWriter pdfReceiptWriter = new PdfReceiptWriter();
    private final Clock clock;

    private final Authorizer auth;

    public ReceiptService(PaymentDao paymentDao, ReportingDao reportingDao, ReceiptWriter receiptWriter,
                          Authorizer auth) {
        this(paymentDao, reportingDao, receiptWriter, Clock.systemDefaultZone(), auth);
    }

    public ReceiptService(PaymentDao paymentDao, ReportingDao reportingDao, ReceiptWriter receiptWriter, Clock clock,
                          Authorizer auth) {
        this.auth = auth;
        this.paymentDao = paymentDao;
        this.reportingDao = reportingDao;
        this.receiptWriter = receiptWriter;
        this.clock = clock;
    }

    public PaymentReceiptData buildReceipt(Long paymentId) throws EstateFlowException {
        auth.require(Permission.VIEW_DATA);
        if (paymentId == null) {
            throw new ValidationException("Select a payment.", "payment");
        }
        Payment p = paymentDao.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment " + paymentId + " no longer exists."));
        if (p.isVoid()) {
            throw new BusinessRuleException("Payment " + p.receiptNumber()
                    + " was voided; a receipt cannot be issued for it.", "BR-25");
        }
        ReceiptContext c = reportingDao.findReceiptContext(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment " + paymentId + " no longer exists."));
        return new PaymentReceiptData(p.receiptNumber(), p.getPaymentDate(), c.tenantName(), c.propertyName(),
                c.periodStart(), c.periodEnd(), p.getAmount(), p.getPaymentMethod(), p.getReferenceNo(),
                c.currentBalance());
    }

    /** Writes a PDF when the file name ends with .pdf, otherwise the plain-text receipt. */
    public void writeReceipt(Long paymentId, Path target) throws EstateFlowException {
        auth.require(Permission.VIEW_DATA);
        PaymentReceiptData data = buildReceipt(paymentId);
        if (target.getFileName().toString().toLowerCase().endsWith(".pdf")) {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
            pdfReceiptWriter.write(target, data, GENERATED.format(now),
                    PDF_DATE.format(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
        } else {
            receiptWriter.write(target, data);
        }
    }
}
