CREATE TABLE IF NOT EXISTS enhetsregisteret_v1
(
    orgnr        CHAR(11)       NOT NULL,
    opprettet    TIMESTAMP      NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    type         VARCHAR(30)    NOT NULL,
    data         JSONB          NOT NULL,
    PRIMARY KEY (orgnr, opprettet)
);
