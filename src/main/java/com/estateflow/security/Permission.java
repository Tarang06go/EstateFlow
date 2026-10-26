package com.estateflow.security;

/** What an action needs. Roles map to these in {@link com.estateflow.model.enums.Role}. */
public enum Permission {
    /** Read records, dashboards and searches. */
    VIEW_DATA,
    /** Open and export reports. */
    VIEW_REPORTS,
    /** View or export rent-collection insights that identify individual tenants. */
    VIEW_TENANT_INSIGHTS,
    /** Read the activity history. */
    VIEW_AUDIT,
    /** Create, edit, deactivate or reactivate owners, properties, tenants, agreements and maintenance. */
    MANAGE_RECORDS,
    /** Record and void rent payments. */
    RECORD_PAYMENTS,
    /** Change application settings. */
    MANAGE_SETTINGS,
    /** Create users, assign roles, activate/deactivate accounts, reset passwords. */
    MANAGE_USERS,
    /** Manage the signed-in user's own optional face profile, with password reauthentication. */
    MANAGE_OWN_FACE_PROFILE
}
