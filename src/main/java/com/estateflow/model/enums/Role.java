package com.estateflow.model.enums;

import com.estateflow.security.Permission;

import java.util.EnumSet;
import java.util.Set;

/** Application roles and the permissions each one grants. The single place where roles map to permissions. */
public enum Role {
    ADMIN("Administrator", EnumSet.allOf(Permission.class)),
    MANAGER("Manager", EnumSet.of(Permission.VIEW_DATA, Permission.VIEW_REPORTS, Permission.VIEW_TENANT_INSIGHTS, Permission.VIEW_AUDIT,
            Permission.MANAGE_RECORDS, Permission.RECORD_PAYMENTS, Permission.MANAGE_SETTINGS, Permission.MANAGE_OWN_FACE_PROFILE)),
    VIEWER("Viewer", EnumSet.of(Permission.VIEW_DATA, Permission.VIEW_REPORTS, Permission.MANAGE_OWN_FACE_PROFILE));

    private final String label;
    private final Set<Permission> permissions;

    Role(String label, Set<Permission> permissions) {
        this.label = label;
        this.permissions = permissions;
    }

    public String label() {
        return label;
    }

    public boolean grants(Permission permission) {
        return permissions.contains(permission);
    }
}
