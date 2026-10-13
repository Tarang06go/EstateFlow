package com.estateflow.service;

import com.estateflow.dao.*;
import com.estateflow.exception.*;
import com.estateflow.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;

import static com.estateflow.service.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PartyServicesTest {
    OwnerDao ownerDao = mock(OwnerDao.class);
    TenantDao tenantDao = mock(TenantDao.class);
    PropertyDao propertyDao = mock(PropertyDao.class);
    AgreementDao agreementDao = mock(AgreementDao.class);
    ReportingDao reportingDao = mock(ReportingDao.class);
    OwnerService owners;
    TenantService tenants;
    PropertyService properties;

    @BeforeEach
    void setUp() {
        owners = new OwnerService(ownerDao, propertyDao, new PassThroughTransactionManager(), AuditService.noop());
        tenants = new TenantService(tenantDao, agreementDao, new PassThroughTransactionManager(), AuditService.noop(), CLOCK);
        properties = new PropertyService(propertyDao, ownerDao, agreementDao, reportingDao,
                new PassThroughTransactionManager(), AuditService.noop(), CLOCK);
        when(ownerDao.insert(any(Owner.class))).thenAnswer(i -> {
            Owner o = i.getArgument(0);
            o.setId(1L);
            return o;
        });
        when(tenantDao.insert(any(Tenant.class))).thenAnswer(i -> {
            Tenant t = i.getArgument(0);
            t.setId(1L);
            return t;
        });
        when(propertyDao.insert(any(Property.class))).thenAnswer(i -> {
            Property p = i.getArgument(0);
            p.setPropertyId(1L);
            return p;
        });
    }

    // ---- BR-01 owners
    @Test
    @DisplayName("BR-01: owner phone is normalized before the uniqueness check and storage")
    void createOwner_formattedPhone_isNormalized() throws Exception {
        when(ownerDao.findByPhone("+919876543210")).thenReturn(Optional.empty());
        Owner saved = owners.create(owner(null, true));
        assertEquals("+919876543210", saved.getPhone());
        verify(ownerDao).findByPhone("+919876543210");
    }

    @Test
    @DisplayName("BR-01: duplicate owner phone is rejected")
    void createOwner_duplicatePhone_throwsDuplicate() {
        when(ownerDao.findByPhone(any())).thenReturn(Optional.of(owner(9L, true)));
        assertThrows(DuplicateEntityException.class, () -> owners.create(owner(null, true)));
        verify(ownerDao, never()).insert(any());
    }

    @Test
    @DisplayName("BR-01: a unique-key race in the database is translated with the cause kept")
    void createOwner_duplicateKeyFromDao_translatedWithCause() {
        when(ownerDao.findByPhone(any())).thenReturn(Optional.empty());
        DuplicateKeyException dke = new DuplicateKeyException("dup", new SQLException("dup", "23000", 1062), "uq_owner_phone");
        when(ownerDao.insert(any(Owner.class))).thenThrow(dke);
        DuplicateEntityException e = assertThrows(DuplicateEntityException.class, () -> owners.create(owner(null, true)));
        assertSame(dke, e.getCause());
    }

    @Test
    void createOwner_invalidPhone_throwsValidation() {
        Owner o = owner(null, true);
        o.setPhone("12");
        ValidationException e = assertThrows(ValidationException.class, () -> owners.create(o));
        assertEquals("phone", e.getFieldName());
    }

    @Test
    @DisplayName("BR-02: owner with active properties cannot be deactivated")
    void deactivateOwner_withActiveProperties_throwsBr02() {
        when(ownerDao.findById(1L)).thenReturn(Optional.of(owner(1L, true)));
        when(propertyDao.countActiveByOwner(1L)).thenReturn(2);
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> owners.deactivate(1L));
        assertEquals("BR-02", e.getRuleId());
        verify(ownerDao, never()).update(any());
    }

    @Test
    @DisplayName("BR-02: owner without active properties is deactivated; reactivation always allowed")
    void deactivateAndReactivateOwner_succeeds() throws Exception {
        Owner o = owner(1L, true);
        when(ownerDao.findById(1L)).thenReturn(Optional.of(o));
        when(propertyDao.countActiveByOwner(1L)).thenReturn(0);
        when(ownerDao.update(o)).thenReturn(1);
        owners.deactivate(1L);
        assertFalse(o.isActive());
        owners.reactivate(1L);
        assertTrue(o.isActive());
    }

    @Test
    void updateOwner_zeroRowsUpdated_throwsNotFound() {
        when(ownerDao.findById(1L)).thenReturn(Optional.of(owner(1L, true)));
        when(ownerDao.findByPhone(any())).thenReturn(Optional.empty());
        when(ownerDao.update(any(Owner.class))).thenReturn(0);
        assertThrows(EntityNotFoundException.class, () -> owners.update(owner(1L, true)));
    }

    // ---- BR-01 / BR-03 tenants
    @Test
    @DisplayName("BR-01: tenant ID proof must be both present or both absent")
    void createTenant_halfIdProof_throwsValidation() {
        Tenant t = tenant(null, true);
        t.setIdProofType("AADHAAR");
        assertThrows(ValidationException.class, () -> tenants.create(t));
    }

    @Test
    @DisplayName("BR-01: duplicate tenant ID proof pair is rejected")
    void createTenant_duplicateIdProof_throwsDuplicate() {
        Tenant t = tenant(null, true);
        t.setIdProofType("AADHAAR");
        t.setIdProofNumber("1234");
        when(tenantDao.findByPhone(any())).thenReturn(Optional.empty());
        when(tenantDao.findByIdProof("AADHAAR", "1234")).thenReturn(Optional.of(tenant(5L, true)));
        assertThrows(DuplicateEntityException.class, () -> tenants.create(t));
    }

    @Test
    @DisplayName("BR-01: blank ID proof strings are stored as absent")
    void createTenant_blankIdProof_storedAsNull() throws Exception {
        Tenant t = tenant(null, true);
        t.setIdProofType(" ");
        t.setIdProofNumber("");
        when(tenantDao.findByPhone(any())).thenReturn(Optional.empty());
        Tenant saved = tenants.create(t);
        assertNull(saved.getIdProofType());
        assertNull(saved.getIdProofNumber());
    }

    @Test
    @DisplayName("BR-03: tenant with open agreement cannot be deactivated; checked against today")
    void deactivateTenant_openAgreement_throwsBr03() {
        when(tenantDao.findById(1L)).thenReturn(Optional.of(tenant(1L, true)));
        when(agreementDao.existsOpenForTenant(1L, TODAY)).thenReturn(true);
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> tenants.deactivate(1L));
        assertEquals("BR-03", e.getRuleId());
    }

    @Test
    void deactivateTenant_noOpenAgreement_succeedsAndCanBeReactivated() throws Exception {
        Tenant t = tenant(1L, true);
        when(tenantDao.findById(1L)).thenReturn(Optional.of(t));
        when(agreementDao.existsOpenForTenant(1L, TODAY)).thenReturn(false);
        when(tenantDao.update(t)).thenReturn(1);
        tenants.deactivate(1L);
        assertFalse(t.isActive());
        tenants.reactivate(1L);
        assertTrue(t.isActive());
    }

    // ---- BR-04 / BR-05 properties
    @Test
    @DisplayName("BR-05: inactive owner cannot receive a new property")
    void createProperty_inactiveOwner_throwsBr05() {
        when(ownerDao.findById(1L)).thenReturn(Optional.of(owner(1L, false)));
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> properties.create(property(null, 1L, true)));
        assertEquals("BR-05", e.getRuleId());
        verify(propertyDao, never()).insert(any());
    }

    @Test
    void createProperty_activeOwner_inserts() throws Exception {
        when(ownerDao.findById(1L)).thenReturn(Optional.of(owner(1L, true)));
        assertEquals(1L, properties.create(property(null, 1L, true)).getPropertyId());
    }

    @Test
    void createProperty_negativeRent_throwsValidation() {
        Property p = property(null, 1L, true);
        p.setListedRent(new BigDecimal("-1"));
        assertThrows(ValidationException.class, () -> properties.create(p));
    }

    @Test
    @DisplayName("BR-04: property with open agreement cannot be deactivated")
    void deactivateProperty_openAgreement_throwsBr04() {
        when(propertyDao.findById(2L)).thenReturn(Optional.of(property(2L, 1L, true)));
        when(agreementDao.existsOpenForProperty(2L, TODAY)).thenReturn(true);
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> properties.deactivate(2L));
        assertEquals("BR-04", e.getRuleId());
    }

    @Test
    @DisplayName("BR-04: property is reactivated only when its owner is active")
    void reactivateProperty_ownerInactive_throwsBr04_ownerActive_succeeds() throws Exception {
        Property p = property(2L, 1L, false);
        when(propertyDao.findById(2L)).thenReturn(Optional.of(p));
        when(propertyDao.update(p)).thenReturn(1);
        when(ownerDao.findById(1L)).thenReturn(Optional.of(owner(1L, false)));
        assertEquals("BR-04", assertThrows(BusinessRuleException.class, () -> properties.reactivate(2L)).getRuleId());
        when(ownerDao.findById(1L)).thenReturn(Optional.of(owner(1L, true)));
        properties.reactivate(2L);
        assertTrue(p.isActive());
    }

    @Test
    void getProperty_missing_throwsNotFound() {
        when(propertyDao.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> properties.get(99L));
    }
}
