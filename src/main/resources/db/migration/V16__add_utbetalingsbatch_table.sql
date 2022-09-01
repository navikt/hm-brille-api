CREATE TABLE IF NOT EXISTS utbetalingsbatch_v1
(
    batch_id    VARCHAR(139) NOT NULL UNIQUE,
    antall_utbetalinger INTEGER NOT NULL,
    totalbelop NUMERIC(8, 2) NOT NULL,
    opprettet   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;