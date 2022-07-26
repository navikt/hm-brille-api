
CREATE TABLE IF NOT EXISTS utbetaling_v1
(
    id             BIGSERIAL NOT NULL PRIMARY KEY,
    vedtak_id      BIGINT NOT NULL REFERENCES vedtak_v1(id) UNIQUE,
    referanse       VARCHAR(30) NOT NULL,
    utbetalingsdato   DATE      NOT NULL,
    opprettet      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    oppdatert      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
