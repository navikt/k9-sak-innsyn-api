CREATE TABLE IF NOT EXISTS inntektsmelding
(
    journalpost_id  VARCHAR(50) PRIMARY KEY NOT NULL,
    søker_aktør_id  VARCHAR(20)             NOT NULL,
    saksnummer      VARCHAR(20)             NOT NULL,
    inntektsmelding jsonb                   NOT NULL,
    ytelsetype      VARCHAR(8)              NOT NULL,
    status          VARCHAR(20)             NOT NULL,
    opprettet_dato  TIMESTAMP               NOT NULL,
    oppdatert_dato  TIMESTAMP               NOT NULL
);


CREATE INDEX IF NOT EXISTS idx_inntektsmelding_søker_aktør_id
    ON inntektsmelding (søker_aktør_id);

CREATE INDEX IF NOT EXISTS idx_inntektsmelding_saksnummer
    ON inntektsmelding (saksnummer);

CREATE INDEX IF NOT EXISTS idx_inntektsmelding_status
    ON inntektsmelding (status);

CREATE INDEX IF NOT EXISTS idx_inntektsmelding_ytelsetype
    ON inntektsmelding (ytelsetype);
