CREATE DOMAIN fnr AS CHAR(11);
CREATE DOMAIN orgnr AS CHAR(9);
CREATE DOMAIN kontonr AS CHAR(11);
CREATE DOMAIN belop
    AS TEXT
    CONSTRAINT gyldig_belop CHECK (value ~ '^[+-]?(\d+([.]\d*)?|[.]\d+)$');

CREATE TABLE IF NOT EXISTS audit_v1
(
    id                  BIGSERIAL NOT NULL PRIMARY KEY,
    fnr_innlogget       FNR       NOT NULL,
    fnr_oppslag         FNR       NOT NULL,
    oppslag_beskrivelse TEXT      NOT NULL,
    oppslag_tidspunkt   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS audit_v1_fnr_innlogget ON audit_v1 (fnr_innlogget, oppslag_tidspunkt DESC);
CREATE INDEX IF NOT EXISTS audit_v1_fnr_oppslag ON audit_v1 (fnr_oppslag, oppslag_tidspunkt DESC);

CREATE TABLE IF NOT EXISTS virksomhet_v1
(
    orgnr          ORGNR     NOT NULL PRIMARY KEY,
    kontonr        KONTONR   NOT NULL,
    epost          TEXT      NULL,
    fnr_innsender  FNR       NOT NULL,
    navn_innsender TEXT      NOT NULL,
    aktiv          BOOLEAN   NOT NULL,
    avtaleversjon  TEXT,
    opprettet      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    oppdatert      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS virksomhet_v1_fnr_innsender ON virksomhet_v1 (fnr_innsender, opprettet DESC);

CREATE TABLE IF NOT EXISTS innsender_v1
(
    fnr_innsender FNR       NOT NULL PRIMARY KEY,
    godtatt       BOOLEAN   NOT NULL,
    opprettet     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vedtak_v1
(
    id                   BIGSERIAL NOT NULL PRIMARY KEY,
    fnr_barn             FNR       NOT NULL,
    fnr_innsender        FNR       NOT NULL,
    orgnr                ORGNR     NOT NULL REFERENCES virksomhet_v1 (orgnr),
    bestillingsdato      DATE      NOT NULL,
    brillepris           TEXT      NOT NULL,
    bestillingsreferanse TEXT      NOT NULL,
    vilkarsvurdering     JSONB     NOT NULL,
    behandlingsresultat  TEXT      NOT NULL,
    sats                 TEXT      NOT NULL,
    sats_belop           BELOP     NOT NULL,
    sats_beskrivelse     TEXT      NOT NULL,
    belop                BELOP     NOT NULL,
    opprettet            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS vedtak_v1_fnr_barn ON vedtak_v1 (fnr_barn, opprettet DESC);
CREATE INDEX IF NOT EXISTS vedtak_v1_fnr_innsender ON vedtak_v1 (fnr_innsender, opprettet DESC);
CREATE INDEX IF NOT EXISTS vedtak_v1_orgnr ON vedtak_v1 (orgnr, opprettet DESC);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
