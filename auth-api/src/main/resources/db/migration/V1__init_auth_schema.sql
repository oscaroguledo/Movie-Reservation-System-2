-- Mirrors auth-api/models/schema.dbml from the Python reference implementation.
-- user_type is modeled as VARCHAR + CHECK rather than a native Postgres enum
-- type, to sidestep Hibernate/JDBC's brittle mapping of native enum types;
-- semantics are identical.

CREATE SCHEMA IF NOT EXISTS auth_api;

CREATE TABLE auth_api.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_type VARCHAR(20) NOT NULL DEFAULT 'regular'
        CHECK (user_type IN ('admin', 'regular')),
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX users_email_key ON auth_api.users (email);

CREATE TABLE auth_api.revoked_tokens (
    jti VARCHAR(255) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
