CREATE TABLE psb_søknad
(
    journalpost_id          VARCHAR(50) PRIMARY KEY NOT NULL,
    søker_aktør_id          VARCHAR(20)             NOT NULL,
    pleietrengende_aktør_id VARCHAR(20)             NOT NULL,
    søknad                  jsonb,
    opprettet_dato          TIMESTAMP               NOT NULL,
    oppdatert_dato          TIMESTAMP               NOT NULL
);

CREATE TABLE omsorg
(
    id                      VARCHAR(50) PRIMARY KEY NOT NULL,
    søker_aktør_id          VARCHAR(20)             NOT NULL,
    pleietrengende_aktør_id VARCHAR(20)             NOT NULL,
    har_omsorgen            BOOLEAN                 NOT NULL,
    opprettet_dato          TIMESTAMP               NOT NULL,
    oppdatert_dato          TIMESTAMP               NOT NULL
);
