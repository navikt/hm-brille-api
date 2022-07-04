CREATE TABLE IF NOT EXISTS virksomhet (
    orgnr                   char(9)         NOT NULL PRIMARY KEY,
    kontonr                 char(11)        NOT NULL,
    fnr_innsender           char(11)        NOT NULL,
    navn_innsender          varchar(255)    NOT NULL,
    har_nav_avtale          boolean         NOT NULL,
    avtale_versjon          varchar(255),
    opprettet               timestamp       NOT NULL DEFAULT (NOW())
);

CREATE INDEX virksomhet_orgnr    ON virksomhet (orgnr);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
