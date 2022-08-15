
CREATE TABLE IF NOT EXISTS vedtak_slettet_v1
(
    id                   BIGINT    NOT NULL,
    fnr_barn             FNR       NOT NULL,
    fnr_innsender        FNR       NOT NULL,
    orgnr                ORGNR     NOT NULL,
    bestillingsdato      DATE      NOT NULL,
    brillepris           TEXT      NOT NULL,
    bestillingsreferanse TEXT      NOT NULL,
    vilkarsvurdering     JSONB     NOT NULL,
    behandlingsresultat  TEXT      NOT NULL,
    sats                 TEXT      NOT NULL,
    sats_belop           NUMERIC(8, 2)     NOT NULL,
    sats_beskrivelse     TEXT      NOT NULL,
    belop                NUMERIC(8, 2)     NOT NULL,
    opprettet            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO vedtak_slettet_v1
    SELECT id, fnr_barn, fnr_innsender, orgnr, bestillingsdato, brillepris, bestillingsreferanse, vilkarsvurdering, behandlingsresultat, sats, sats_belop, sats_beskrivelse, belop
    FROM vedtak_v1
    WHERE id = 746;

DELETE FROM vedtak_v1 WHERE id = 746;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
