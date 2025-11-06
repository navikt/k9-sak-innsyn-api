CREATE TABLE legacy_søknad
(
    søknad_id       VARCHAR(50) PRIMARY KEY NOT NULL,
    søknadstype     VARCHAR(50)             NOT NULL,
    søknad          jsonb                   NOT NULL,
    saks_id         VARCHAR(50),
    journalpost_id  VARCHAR(50)             NOT NULL,
    opprettet_dato  TIMESTAMP               NOT NULL
);

CREATE INDEX idx_legacy_søknad_journalpost_id ON legacy_søknad(journalpost_id);
