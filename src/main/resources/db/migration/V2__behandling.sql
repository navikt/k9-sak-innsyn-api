CREATE TABLE behandling
(
    behandling_id           VARCHAR(50) PRIMARY KEY NOT NULL,
    søker_aktør_id          VARCHAR(20)             NOT NULL,
    pleietrengende_aktør_id VARCHAR(20)             NOT NULL,
    ytelsetype_kode         VARCHAR(8)              NOT NULL,
    saksnummer              VARCHAR(20)             NOT NULL,
    behandling              jsonb                   NOT NULL,
    opprettet_dato          TIMESTAMP               NOT NULL,
    oppdatert_dato          TIMESTAMP               NOT NULL
);
