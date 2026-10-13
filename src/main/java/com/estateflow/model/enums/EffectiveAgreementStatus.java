package com.estateflow.model.enums;

/**
 * Derived (never stored) status reflecting the real-time state of an agreement
 * based on dates and stored status.
 */
public enum EffectiveAgreementStatus {
    UPCOMING,
    ACTIVE,
    EXPIRING_SOON,
    EXPIRED,
    TERMINATED
}
