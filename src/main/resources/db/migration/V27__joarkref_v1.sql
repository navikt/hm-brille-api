CREATE TABLE joarkref_v1
(
    vedtak_id         BIGINT NOT NULL,
    joark_ref         BIGINT NOT NULL,
    opprettet         TIMESTAMP   NOT NULL,
    CONSTRAINT pk_joarkref PRIMARY KEY (vedtak_id)
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO "naisjob"
