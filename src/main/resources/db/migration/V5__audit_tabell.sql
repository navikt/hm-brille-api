CREATE TABLE IF NOT EXISTS audit_v1
(
    id                serial    NOT NULL PRIMARY KEY,
    fnr_innlogget     char(11)  NOT NULL,
    fnr_oppslag       char(11)  NOT NULL,
    tidspunkt_oppslag timestamp NOT NULL DEFAULT (NOW())
);

CREATE INDEX IF NOT EXISTS audit_v1_fnr_innlogget ON audit_v1 (fnr_innlogget, tidspunkt_oppslag DESC);
CREATE INDEX IF NOT EXISTS audit_v1_fnr_oppslag ON audit_v1 (fnr_oppslag, tidspunkt_oppslag DESC);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
