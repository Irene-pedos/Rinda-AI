CREATE TABLE cooperatives (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(150) NOT NULL,
    district   VARCHAR(80)  NOT NULL,
    sector     VARCHAR(80),
    is_demo    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_cooperatives_name (name)
);

CREATE TABLE users (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone      VARCHAR(16)  NOT NULL,
    full_name  VARCHAR(120) NOT NULL,
    role       VARCHAR(10)  NOT NULL,
    language   VARCHAR(2)   NOT NULL DEFAULT 'rw',
    status     VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE',
    is_demo    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_users_phone (phone),
    CONSTRAINT ck_users_role CHECK (role IN ('FARMER', 'AGENT', 'OFFICER', 'ADMIN')),
    CONSTRAINT ck_users_language CHECK (language IN ('rw', 'en')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE consents (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    version     VARCHAR(20) NOT NULL,
    accepted_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_consents_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE farmers (
    user_id           BIGINT PRIMARY KEY,
    cooperative_id    BIGINT NOT NULL,
    national_id_last4 CHAR(4),
    CONSTRAINT fk_farmers_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_farmers_cooperative FOREIGN KEY (cooperative_id) REFERENCES cooperatives (id)
);

CREATE TABLE agents (
    user_id        BIGINT PRIMARY KEY,
    cooperative_id BIGINT NOT NULL,
    approved_by    BIGINT,
    CONSTRAINT fk_agents_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_agents_cooperative FOREIGN KEY (cooperative_id) REFERENCES cooperatives (id),
    CONSTRAINT fk_agents_approved_by FOREIGN KEY (approved_by) REFERENCES users (id)
);

CREATE TABLE otp_codes (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone       VARCHAR(16)  NOT NULL,
    code_hash   VARCHAR(100) NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    consumed_at DATETIME(6),
    created_at  DATETIME(6)  NOT NULL,
    KEY ix_otp_codes_phone (phone, created_at)
);

CREATE TABLE refresh_tokens (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_refresh_tokens_hash (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);
