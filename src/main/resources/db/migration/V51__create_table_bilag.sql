
CREATE TABLE IF NOT EXISTS bilagsdefinisjon_v1
(
    id             BIGSERIAL NOT NULL PRIMARY KEY,
    bilagsnavn     TEXT,
    versjon        SERIAL
);

INSERT INTO bilagsdefinisjon_v1(id, bilagsnavn, versjon)
VALUES (1, 'Formål med innhenting/utveksling av personopplysninger og opplysningstyper', 1);

INSERT INTO bilagsdefinisjon_v1(id, bilagsnavn, versjon)
VALUES (2, 'Teknisk grensesnitt og særskilte krav til sikkerhet', 1);

INSERT INTO bilagsdefinisjon_v1(id, bilagsnavn, versjon)
VALUES (3, 'Varsling, feilretting og kontakt', 1);

INSERT INTO bilagsdefinisjon_v1(id, bilagsnavn, versjon)
VALUES (4, 'Endringslogg for avtalen', 1);

CREATE TABLE IF NOT EXISTS bilag_v1
(
    id             BIGSERIAL NOT NULL PRIMARY KEY,
    orgnr          ORGNR     NOT NULL REFERENCES virksomhet_v1 (orgnr),
    fnr_innsender  FNR       NOT NULL,
    aktiv          BOOLEAN   NOT NULL,
    avtale_id      BIGSERIAL NOT NULL REFERENCES avtale_v1 (id),
    bilagsdefinisjon_id       BIGSERIAL NOT NULL REFERENCES bilagsdefinisjon_v1 (id),
    opprettet      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    oppdatert      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
