create table if not exists ettersendelse
(
    journalpost_id          varchar(50) primary key not null,
    søker_aktør_id          varchar(20)             not null,
    pleietrengende_aktør_id varchar(20)             not null,
    ettersendelse           jsonb                   not null,
    opprettet_dato          timestamp               not null,
    oppdatert_dato          timestamp               not null
);
