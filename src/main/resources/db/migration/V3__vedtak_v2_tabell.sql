CREATE TABLE IF NOT EXISTS vedtak_v2
(
    id                   serial    NOT NULL PRIMARY KEY,
    fnr_bruker           char(11)  NOT NULL,
    fnr_innsender        char(11)  NOT NULL,
    orgnr                char(9)   NOT NULL,
    bestillingsdato      date      NOT NULL,
    brillepris           text      NULL NULL,
    bestillingsreferanse text      NOT NULL,
    vilkarsvurdering     jsonb     NOT NULL,
    status               text      NOT NULL,
    opprettet            timestamp NOT NULL DEFAULT (NOW())
);

CREATE INDEX IF NOT EXISTS vedtak_v2_fnr_bruker ON vedtak_v2 (fnr_bruker, opprettet DESC);
CREATE INDEX IF NOT EXISTS vedtak_v2_fnr_innsender ON vedtak_v2 (fnr_innsender, opprettet DESC);
CREATE INDEX IF NOT EXISTS vedtak_v2_orgnr ON vedtak_v2 (orgnr, opprettet DESC);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
