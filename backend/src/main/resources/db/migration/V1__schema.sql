-- Esquema del servicio de autenticación. Compatible con MySQL 8 y con H2 en modo MySQL.
-- Nunca se guardan contraseñas ni tokens en claro: solo su hash.

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    -- Email normalizado (trim + minúsculas) con restricción única.
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE roles (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    CONSTRAINT uk_roles_name UNIQUE (name)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- Una sesión = un inicio de sesión en un dispositivo. El access token lleva su id (claim "sid").
CREATE TABLE sessions (
    id           VARCHAR(36)  PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    device_label VARCHAR(120),
    ip_address   VARCHAR(45),
    created_at   DATETIME(6)  NOT NULL,
    last_used_at DATETIME(6)  NOT NULL,
    expires_at   DATETIME(6)  NOT NULL,
    revoked_at   DATETIME(6),
    revoke_reason VARCHAR(40),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_sessions_user ON sessions (user_id);

-- Cada rotación crea un refresh token nuevo dentro de la misma sesión y marca el anterior como usado.
-- Reutilizar un token ya rotado indica robo: se revoca la sesión completa.
CREATE TABLE refresh_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  VARCHAR(36)  NOT NULL,
    token_hash  CHAR(64)     NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    used_at     DATETIME(6),
    revoked_at  DATETIME(6),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_session FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE CASCADE
);

CREATE TABLE password_reset_tokens (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6),
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);

-- Auditoría: nunca contiene contraseñas, hashes ni tokens.
CREATE TABLE audit_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type  VARCHAR(40)  NOT NULL,
    outcome     VARCHAR(20)  NOT NULL,
    user_id     BIGINT,
    actor_id    BIGINT,
    email       VARCHAR(160),
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(200),
    detail      VARCHAR(255),
    created_at  DATETIME(6)  NOT NULL
);
CREATE INDEX ix_audit_events_created ON audit_events (created_at);
CREATE INDEX ix_audit_events_user ON audit_events (user_id);

INSERT INTO roles (name) VALUES ('USER');
INSERT INTO roles (name) VALUES ('ADMIN');
