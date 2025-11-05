# K9-sak Innsynstjeneste

![CI / CD](https://github.com/navikt/k9-sak-innsyn-api/workflows/CI%20/%20CD/badge.svg)
![Alerts](https://github.com/navikt/k9-sak-innsyn-api/workflows/Alerts/badge.svg)
![Vulnerabilities scanning of dependencies](https://github.com/navikt/k9-sak-innsyn-api/workflows/Vulnerabilities%20scanning%20of%20dependencies/badge.svg)

# Innholdsoversikt

* [1. Kontekst](#1-kontekst)
* [2. Funksjonelle Krav](#2-funksjonelle-krav)
* [3. Begrensninger](#3-begrensninger)
* [4. Programvarearkitektur](#5-programvarearkitektur)
* [5. Kode](#6-kode)
* [6. Data](#7-data)
* [7. Infrastrukturarkitektur](#8-infrastrukturarkitektur)
* [8. Distribusjon av tjenesten (deployment)](#9-distribusjon-av-tjenesten-deployment)
* [9. Utviklingsmiljø](#10-utviklingsmilj)
* [10. Drift og støtte](#11-drift-og-sttte)

# 1. Kontekst

Kafka konsumer og API-tjeneste for k9-sak innsynsplatform.

## Sekvensdiagram
```mermaid
sequenceDiagram
   participant k9s as K9 Sak
   participant topic as Topic: privat-k9-sak-innsyn-v1
   participant k9s-innsyn as K9 Sak Innsyn API
   participant k9s-innsyn-db as K9 Sak Innsyn API DB
   participant sif-innsyn-api as SIF Innsyn API
   participant dine-pleiepenger as Dine pleiepenger
   actor søker as søker
   
   k9s ->> topic: Publiserer hendelse om PsbSøknadsinnhold
   k9s ->> topic: Publiserer hendelse om Omsorg
   
   k9s-innsyn --) topic: Konsumerer innsynshendelse
   
   alt Innsynshendelse er PsbSøknadsinnhold
   k9s-innsyn -> k9s-innsyn: Map til PsbSøknadDAO
   k9s-innsyn ->> k9s-innsyn-db: Lagre PsbSøknadDAO
   
   else  Innsynshendelse er Omsorg
   k9s-innsyn ->> k9s-innsyn: Map til OmsorgDAO
   k9s-innsyn ->> k9s-innsyn-db: Lagre OmsorgDAO
   end
   
   søker ->> dine-pleiepenger: Hent søknadsopplysninger
   dine-pleiepenger ->> sif-innsyn-api: Hent søknader
   sif-innsyn-api ->> k9s-innsyn: Hent søknader
   k9s-innsyn ->> k9s-innsyn: Hent barna søker har omsorgen for
   k9s-innsyn ->> k9s-innsyn: Slå sammen søknadsopplysninger per barn
   k9s-innsyn ->> dine-pleiepenger: Returner sammenslåtte søknader per barn
```
[Mermaid Live Editor](https://mermaid.live/edit#pako:eNqVlN9umzAUxl_F8t0kiALNSOEiUqdsWtRVjZZdTdw44CYWYHu2qZZFeZy9w-77Yj3GIX8IzVSkSMbn_L5zfPjiLc5ETnGCNf1VU57RKSMrRaqUI3gkUYZlTBJuUBFrRDS6j9GCFJdhIyTLbMIPu0iQVOyZGL-IfU0Kn3GuN9x_Dnp19-GjPJq5jbv57Brg58teBk0_XWKaPbUYkcxyi9mXq4VyxqkvS8qopHxFlWWmsIdO9hxFMiMU0i__CpflVil3UTs5fzJxI0rQvF6WTFMFqWvKc1pqikSF5nq5AIyTXEOba1Hm76EfKy3UyhEHrp2r739o8XvBdV01uAvqVqXtlpRmP5VDCEH2W-2hbq3JyVuCHohEhpUn-PTu8S1ycv5tE_QNvEi7rKObvvoaPZ3E9RLH7hzz_s46HHTRtrf3giW7NkrQV2r9uJ-mkLLcaM6ObrrwnVU5t--5Rgt2LN49bR9yZThN-pIoTtrTrIkCr9kjU46exP8lFuXLX6RJVUF-33mRhJ8t0at0Objv1NSKA-M0NcgbQ49nOuhhD4PJK8JyuNq2Vj3FZk0rmuIEljlRRYpTvoM8Uhux2PAMJ0bV1MO1zIlpr8Hzzc85g_85Tp4IeM3DcEn8FKI6fcfJFv_GiR-Ew2AQRGEUjW7Ht8MoiDy8gf1xEA2GNzcfR8NwNIJwuPPwn0YjGIyHcRiH43A8imIIe5g25R7c_dxc07tXwkDwWw)

# 2. Funksjonelle Krav

Denne tjenesten understøtter behovet for innsyn for bruker. Tjenesten lytter etter diverse innsynshendelser som e.g.
søknadsopplysninger, informasjon om omsorg, tilbaketrekking av søknader. og lagrer/fjerner dem i/fra databasen.
Tjenesten eksponerer også api-er for henting av overnevnte data, for å gi bruker innsyn i egne saker.

Denne tjenesten understøtter også behovet for etterrapportering av søknadsopplysninger for pleiepenger sykt-barn.
For å gjøre det enklere konsumenter av disse opplysningene blir søknader slått sammen.
Alle søknader registrert på barna søker har omsorg for blir hentet opp og slått sammen (kun perioder på arbeidstimer og tilsyn pr. nå). 
I de tilfellene det er kommet inn søknad fra annen omsorgsperson på samme barn som søker har omsorg for, 
er det kun perioder med tilsyn som slås sammen, ikke info fra annen omsorgsperson.

# 3. Begrensninger

Innsyn i sakene til bruker begrenses til kapittel 9 ytelsene som e.g. omsorgspenger, pleiepenger, etc.

# 4. Programvarearkitektur

# 5. Kode

# 6. Data

## Innsynhendelse om PsbSøknadsinnhold

Hendelse med søknadsopplysninger om pleiepenger - sykt barn. Denne hendelsen konsumeres og persisteres i databasen.

## Innsynhendelse om Omsorg

Hendelse med informasjon om hvem som har omsorg for pleiepengetrengende. Denne hendelsen konsumeres og persisteres i
databasen for å bli brukt som tilgangskontroll ved tilgang til søknadsopplysninger.

## Innsynshendelse om tilbaketrekking av søknadsopplysninger

Hendelse som tilbaketrekking av søknader. Denne hendelsen konsumeres og sletter eventuelle søknader fra databasen.

# 7. Infrastrukturarkitektur

# 8. Distribusjon av tjenesten (deployment)

Distribusjon av tjenesten er gjort med bruk av Github Actions.
[K9-sak Innsyn API CI / CD](https://github.com/navikt/k9-sak-innsyn-api/actions)

Push/merge til dev-* branch vil teste, bygge og deploye til testmiljø.
Push/merge til master branch vil teste, bygge og deploye til produksjonsmiljø og testmiljø.

# 9. Utviklingsmiljø

## Forutsetninger

* docker
* docker-compose
* Java 11
* Kubectl

## Bygge Prosjekt

For å bygge kode, kjør:

```shell script
./gradlew clean build
```

## Kjøre Prosjekt

For å kjøre kode, kjør:

```shell script
./gradlew clean build && docker build --tag k9-sak-innsyn-api-local . && docker-compose up --build
```

Eller for å hoppe over tester under bygging:

```shell script
./gradlew clean build -x test && docker build --tag k9-sak-innsyn-api-local . && docker-compose up --build
```

### Produsere kafka meldinger

For produsere kafka meldinger, må man først exec inn på kafka kontaineren ved å bruker docker dashbord, eller ved å
kjøre følgende kommando:

```shell script
docker exec -it <container-id til kafka> /bin/sh; exit
```

Deretter, kjøre følgende kommando for å koble til kafka instansen:

```shell script
kafka-console-producer --broker-list localhost:9092 --topic privat-sif-innsyn-mottak --producer.config=$CLASSPATH/producer.properties
```

### Henting av data via api-endepunktene

Applikasjonen er konfigurert med en lokal oicd provider stub for å utsending og verifisering av tokens. For å kunne
gjøre kall på endepunktene, må man ha et gyldig token.

#### Henting av token

1. Åpne oicd-provider-gui i nettleseren enten ved å bruke docker dashbord, eller ved å gå til http://localhost:5000.
2. Trykk "Token for nivå 4" for å logge inn med ønsket bruker, ved å oppgi fødselsnummer. Tokenet blir da satt som en
   cookie (selvbetjening-idtoken) i nettleseren.
3. Deretter kan du åpne http://localhost:8085/swagger-ui.html for å teste ut endepunktene.

Om man ønsker å bruke postman må man selv, lage en cookie og sette tokenet manuelt. Eksempel:
selvbetjening-idtoken=eyJhbGciOiJSUzI1NiIsInR5cCI6Ikp.eyJzdWIiOiIwMTAxMDExMjM0NSIsImFjc.FBmVFuHI9d8akrVdAxi1dRg03qKV4EGk;
Path=/; Domain=localhost; Expires=Fri, 18 Jun 2021 08:46:13 GMT;

### Kjøre mot VTP lokalt

1. Kjør opp VTP via f.eks. k9-verdikjedetest repoet
2. Kjør k9-sak-innsyn-api databasen via docker-compose 
` docker-compose -f docker-compose-vtp.yml up db --no-deps -d`
3. Start SifInnsynApiApplication med profilen `vtp` og env variabler fra `docker-compose-env/vtp.env`. Variablene kan limes rett inn i Intellij Run Configuration. Alternativ, bruk docker-compose fila til å starte k9-sak-innsyn-api `docker-compose-vtp.yml`


# 10. Drift og støtte

## Feilsøking
For å feilsøke data kan du bruke Swagger-UI.

For å kunne bruke endepunktene under DriftController må du logge inn med en Azure AD-bruker som har rollen 0000-GA-k9-drift.

Gjør følgende for å logge inn og få tilgang til API-et:

1. Naviger til Swagger-UI ved å klikke på en av lenkene nedenfor.
2. Klikk på "Authorize"-knappen øverst til høyre.
3. Merk av i boksen for API-scopet og klikk på "Authorize".
4. NB! Du trenger ikke å fylle inn noen hemmelig nøkkel.
Lenker til Swagger-UI:

- [Swagger-ui - dev](https://k9-sak-innsyn-api.intern.dev.nav.no/swagger-ui/index.html)
- [Swagger-ui - prod](https://k9-sak-innsyn-api.intern.nav.no/swagger-ui/index.html)

## Logging

Loggene til tjenesten kan leses på to måter:

### Kibana

For [dev-gcp: https://logs.adeo.no/goto/7db198143c27f93228b17f3b07f16e39](https://logs.adeo.no/goto/7db198143c27f93228b17f3b07f16e39)

For [prod-gcp: https://logs.adeo.no/goto/e796ec96af7bb1032a11d388e6849451](https://logs.adeo.no/goto/e796ec96af7bb1032a11d388e6849451)

### Kubectl

For dev-gcp:

```shell script
kubectl config use-context dev-gcp
kubectl get pods -n dusseldorf | grep k9-sak-innsyn-api
kubectl logs -f k9-sak-innsyn-api-<POD-ID> --namespace dusseldorf -c k9-sak-innsyn-api
```

For prod-gcp:

```shell script
kubectl config use-context prod-gcp
kubectl get pods -n dusseldorf | grep k9-sak-innsyn-api
kubectl logs -f k9-sak-innsyn-api-<POD-ID> --namespace dusseldorf -c k9-sak-innsyn-api
```

## Alarmer

Vi bruker [nais-alerts](https://doc.nais.io/observability/alerts) for å sette opp alarmer. Disse finner man konfigurert
i [nais/alerterator-prod.yml](nais/alerterator-prod.yml).

## Metrics

## Henvendelser

Spørsmål koden eller prosjekttet kan rettes til team dusseldorf på:

* [\#sif-brukerdialog](https://nav-it.slack.com/archives/CQ7QKSHJR)
* [\#sif-innsynsplattform](https://nav-it.slack.com/archives/C013ZJTKUNB)


