CREATE INDEX idx_legacy_søknad_søker_fnr ON legacy_søknad ((søknad -> 'søker' ->> 'fødselsnummer'));
