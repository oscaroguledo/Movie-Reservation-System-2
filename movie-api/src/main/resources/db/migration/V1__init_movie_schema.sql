-- Mirrors movie_api/models/schema.dbml from the Python reference implementation.
-- Enums are modeled as VARCHAR + CHECK rather than native Postgres enum
-- types, matching auth-api's V1 migration — sidesteps Hibernate/JDBC's
-- brittle mapping of native enum types; semantics are identical.

CREATE SCHEMA IF NOT EXISTS movie_api;

CREATE TABLE movie_api.genres (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX genres_name_key ON movie_api.genres (name);

CREATE TABLE movie_api.movies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    poster_image_url TEXT NOT NULL,
    release_date DATE,
    duration_minutes INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX movies_title_idx ON movie_api.movies (title);

CREATE TABLE movie_api.movie_genres (
    movie_id UUID NOT NULL REFERENCES movie_api.movies (id),
    genre_id UUID NOT NULL REFERENCES movie_api.genres (id),
    PRIMARY KEY (movie_id, genre_id)
);

CREATE TABLE movie_api.showrooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    capacity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX showrooms_name_key ON movie_api.showrooms (name);

CREATE TABLE movie_api.showroom_seats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    showroom_id UUID NOT NULL REFERENCES movie_api.showrooms (id),
    row VARCHAR(5) NOT NULL,
    number INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX showroom_seats_showroom_id_idx ON movie_api.showroom_seats (showroom_id);

-- A seat label is unique within its showroom, reusable across showrooms.
CREATE UNIQUE INDEX showroom_seats_showroom_row_number_key
    ON movie_api.showroom_seats (showroom_id, row, number);

CREATE TABLE movie_api.showtimes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX showtimes_start_time_idx ON movie_api.showtimes (start_time);

CREATE TABLE movie_api.movie_showtimes (
    movie_id UUID NOT NULL REFERENCES movie_api.movies (id),
    showroom_id UUID NOT NULL REFERENCES movie_api.showrooms (id),
    showtime_id UUID NOT NULL REFERENCES movie_api.showtimes (id),
    PRIMARY KEY (movie_id, showroom_id, showtime_id)
);

CREATE TABLE movie_api.reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- auth_api.users.id — no DB-level FK (movie_api does not own that
    -- table); null for GUEST bookings.
    user_id UUID,
    user_type VARCHAR(20) NOT NULL
        CHECK (user_type IN ('admin', 'regular', 'guest')),
    movie_id UUID NOT NULL,
    showroom_id UUID NOT NULL,
    showtime_id UUID NOT NULL,
    showroom_seat_id UUID NOT NULL REFERENCES movie_api.showroom_seats (id),
    status VARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'confirmed', 'cancelled', 'expired')),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (movie_id, showroom_id, showtime_id)
        REFERENCES movie_api.movie_showtimes (movie_id, showroom_id, showtime_id)
);

CREATE INDEX reservations_user_id_idx ON movie_api.reservations (user_id);

-- Backstop only — the real overbooking guard is the Redis seat lock
-- (ReservationService/acquire_seat); see the reservation service javadoc.
CREATE UNIQUE INDEX reservations_active_seat_per_screening_key
    ON movie_api.reservations (movie_id, showroom_id, showtime_id, showroom_seat_id)
    WHERE status IN ('pending', 'confirmed');

CREATE TABLE movie_api.payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID NOT NULL REFERENCES movie_api.reservations (id),
    amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'succeeded', 'failed', 'refunded')),
    provider_reference VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX payments_reservation_id_idx ON movie_api.payments (reservation_id);
