CREATE TABLE IF NOT EXISTS soknad (
    soknads_id              UUID            NOT NULL PRIMARY KEY,
    fnr_bruker              varchar(11)     NOT NULL,
    fnr_innsender           varchar(11)     NOT NULL,
    soknad_json             text            NULL,
    created                 timestamp       NOT NULL
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
