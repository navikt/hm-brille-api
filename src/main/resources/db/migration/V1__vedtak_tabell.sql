CREATE TABLE IF NOT EXISTS vedtak (
    id                      UUID            NOT NULL PRIMARY KEY,
    fnr_bruker              char(11)        NOT NULL,
    fnr_innsender           char(11)        NOT NULL,
    orgnr                   char(9)         NOT NULL,
    data                    JSONB           NOT NULL,
    opprettet               timestamp       NOT NULL DEFAULT (NOW())
);

CREATE INDEX vedtak_fnr_bruker ON vedtak (fnr_bruker, opprettet DESC);
CREATE INDEX vedtak_fnr_innsender ON vedtak (fnr_innsender, opprettet DESC);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
