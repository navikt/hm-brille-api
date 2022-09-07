CREATE TABLE tssident_v1
(
    orgnr             VARCHAR(32) NOT NULL,
    tss_ident         VARCHAR(32) NOT NULL,
    opprettet         TIMESTAMP   NOT NULL,
    CONSTRAINT pk_tssident PRIMARY KEY (orgnr)
);

CREATE INDEX tssident_v1_tss_ident_idx ON tssident_v1 (tss_ident);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO "naisjob"
