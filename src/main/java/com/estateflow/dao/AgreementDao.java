package com.estateflow.dao;

import com.estateflow.dto.AgreementListRow;
import com.estateflow.dto.ExpiringLeaseRow;
import com.estateflow.model.RentalAgreement;

import java.time.LocalDate;
import java.util.List;

/** All date logic uses effective_end_date (BR-12). */
public interface AgreementDao extends CrudDao<RentalAgreement, Long> {
    List<RentalAgreement> findByProperty(Long propertyId);

    List<RentalAgreement> findByTenant(Long tenantId);

    /** Agreements of the property whose effective period intersects [from, to] inclusive. */
    List<RentalAgreement> findOverlapping(Long propertyId, LocalDate from, LocalDate to);

    /** Agreements whose effective period ends within [from, to], soonest first. */
    List<ExpiringLeaseRow> findEndingBetween(LocalDate from, LocalDate to);

    boolean existsOpenForTenant(Long tenantId, LocalDate today);

    boolean existsOpenForProperty(Long propertyId, LocalDate today);

    /** True if the tenant has or had any agreement on the property (BR-30). */
    boolean existsForPropertyAndTenant(Long propertyId, Long tenantId);

    List<AgreementListRow> findAllRows();

    /** Filtered, capped agreement rows; text matches property name, tenant name, or agreement number. */
    List<AgreementListRow> searchRows(String text, com.estateflow.model.enums.AgreementFilter filter,
                                      LocalDate today, int limit);

    List<AgreementListRow> findRowsByProperty(Long propertyId);

    List<AgreementListRow> findRowsByTenant(Long tenantId);
}
