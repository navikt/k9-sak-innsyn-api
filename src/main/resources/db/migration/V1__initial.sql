CREATE TABLE søknad
(
    id              UUID PRIMARY KEY NOT NULL,
    søknad_id       UUID             NOT NULL,
    person_ident    VARCHAR(11)      NOT NULL,
    søknad          jsonb,
    opprettet       timestamp        NOT NULL,
    endret          timestamp        NOT NULL,
    behandlingsdato timestamp
)
