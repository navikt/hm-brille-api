CREATE TABLE IF NOT EXISTS vedtak (
    id                      UUID            NOT NULL PRIMARY KEY,
    fnr_bruker              varchar(11)     NOT NULL,
    fnr_innsender           varchar(11)     NOT NULL,
    data                    text            NOT NULL,
    opprettet               timestamp       NOT NULL DEFAULT (NOW())
);

CREATE INDEX vedtak_fnr_bruker ON vedtak (fnr_bruker, opprettet DESC);
CREATE INDEX vedtak_fnr_innsender ON vedtak (fnr_innsender, opprettet DESC);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
