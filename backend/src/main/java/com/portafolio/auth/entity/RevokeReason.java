package com.portafolio.auth.entity;

public enum RevokeReason {
    LOGOUT,
    REVOKED_BY_USER,
    TOKEN_REUSE,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    ACCOUNT_DISABLED,
    ROLES_CHANGED
}
