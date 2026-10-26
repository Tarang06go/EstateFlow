package com.estateflow.model;

import com.estateflow.model.enums.Role;

import java.time.LocalDateTime;

/** An application account. Deliberately carries no password hash: hashes never leave the DAO/AuthService. */
public record AppUser(Long id, String username, Role role, boolean active, LocalDateTime createdAt,
                      LocalDateTime lastLoginAt) {
}
