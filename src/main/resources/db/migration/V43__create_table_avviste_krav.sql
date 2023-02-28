CREATE TABLE IF NOT EXISTS avviste_krav_v1
(
    id              BIGSERIAL   NOT NULL    PRIMARY KEY,
    fnrBarn         FNR         NOT NULL,
    fnrInnsender    FNR         NOT NULL,
    orgnr           ORGNR       NOT NULL,
    begrunnelser    JSONB       NOT NULL,
    opprettet       TIMESTAMP   NOT NULL    DEFAULT CURRENT_TIMESTAMP
);
