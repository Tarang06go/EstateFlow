package com.estateflow.service;

import com.estateflow.dao.AgreementDao;
import com.estateflow.dao.OwnerDao;
import com.estateflow.dao.ReportingDao;
import com.estateflow.dao.TenantDao;
import com.estateflow.dto.SearchResults;
import com.estateflow.exception.ValidationException;
import com.estateflow.model.enums.AgreementFilter;

import java.time.Clock;
import java.time.LocalDate;

/** Global search across owners, tenants, properties, agreements and payments; every list is capped. */
public class SearchService {

    public static final int PER_TYPE_LIMIT = 15;
    static final int MIN_LENGTH = 2;

    private final OwnerDao ownerDao;
    private final TenantDao tenantDao;
    private final AgreementDao agreementDao;
    private final ReportingDao reportingDao;
    private final Clock clock;

    public SearchService(OwnerDao ownerDao, TenantDao tenantDao, AgreementDao agreementDao, ReportingDao reportingDao,
                         Clock clock) {
        this.ownerDao = ownerDao;
        this.tenantDao = tenantDao;
        this.agreementDao = agreementDao;
        this.reportingDao = reportingDao;
        this.clock = clock;
    }

    public SearchResults search(String text) throws ValidationException {
        String needle = text == null ? "" : text.trim();
        if (needle.length() < MIN_LENGTH) {
            throw new ValidationException("Type at least " + MIN_LENGTH + " characters to search.", "search");
        }
        LocalDate today = LocalDate.now(clock);
        return new SearchResults(
                ownerDao.search(needle, PER_TYPE_LIMIT),
                tenantDao.search(needle, PER_TYPE_LIMIT),
                reportingDao.searchPropertyRows(needle, today, PER_TYPE_LIMIT),
                agreementDao.searchRows(needle, AgreementFilter.ALL, today, PER_TYPE_LIMIT),
                reportingDao.searchPaymentRows(needle, null, null, null, PER_TYPE_LIMIT));
    }
}
