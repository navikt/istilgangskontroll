![Build status](https://github.com/navikt/istilgangskontroll/workflows/main/badge.svg?branch=master)

# istilgangskontroll

istilgangskontroll er en felles applikasjon for å sikre api'ene til sykefraværs-appene i fagsystem-sonen.
I korte trekk gjør den oppslag mot Microsoft GraphAPI for å finne ut hvilke roller den innloggede veilederen har,
og så sier ja eller nei til om veilederen har tilgang å bruke REST-endepunktet ut i fra det. Om veilederen prøver å få
tilgang til informasjon om en person sjekkes det om personen er diskresjonsmerket, egen ansatt eller tilhører en annen NAV-enhet o.l.

## Technologies used

* Docker
* Gradle
* Kafka
* Kotlin
* Ktor
* Redis

##### Aiven Redis Cache:

istilgangskontroll har deklarasjonen av Redis-cache'n på Aiven som brukes av alle 
app'ene til teamsykefravr. Redis-instansen er delt opp i databaser der nummer 0 
brukes av istilgangskontroll. Fordelingen er som følger:  

Application (git)                                                                             | Redis database                         
--------------------------------------------------------------------------------------------- | -------------------------------
[istilgangskontroll](https://github.com/navikt/istilgangskontroll)                            | 0
[fastlegerest](https://github.com/navikt/fastlegerest)                                        | 1
[finnfastlege](https://github.com/navikt/finnfastlege)                                        | 2
[isaktivitetskrav](https://github.com/navikt/isaktivitetskrav)                                | 3
[isarbeidsuforhet](https://github.com/navikt/isarbeidsuforhet)                                | 4
[isbehandlerdialog](https://github.com/navikt/isbehandlerdialog)                              | 5
[isdialogmelding](https://github.com/navikt/isdialogmelding)                                  | 6
[isdialogmote](https://github.com/navikt/isdialogmote)                                        | 7
[isdialogmotekandidat](https://github.com/navikt/isdialogmotekandidat)                        | 8
[isfrisktilarbeid](https://github.com/navikt/isfrisktilarbeid)                                | 9
[ishuskelapp](https://github.com/navikt/ishuskelapp)                                          | 10
[ismeroppfolging](https://github.com/navikt/ismeroppfolging)                                  | 11
[isnarmesteleder](https://github.com/navikt/isnarmesteleder)                                  | 12
[isoppfolgingstilfelle](https://github.com/navikt/isoppfolgingstilfelle)                      | 13
[ispengestopp](https://github.com/navikt/ispengestopp)                                        | 14
[padm2](https://github.com/navikt/padm2)                                                      | 15
[syfobehandlendeenhet](https://github.com/navikt/syfobehandlendeenhet)                        | 16
[syfomodiaperson](https://github.com/navikt/syfomodiaperson)                                  | 17
[syfomoteoversikt](https://github.com/navikt/syfomoteoversikt)                                | 18
[syfooversikt](https://github.com/navikt/syfooversikt)                                        | 19
[syfooversiktsrv](https://github.com/navikt/syfooversiktsrv)                                  | 20
[syfopartnerinfo](https://github.com/navikt/syfopartnerinfo)                                  | 21
[syfoperson](https://github.com/navikt/syfoperson)                                            | 22
[syfoveileder](https://github.com/navikt/syfoveileder)                                        | 23


##### Test Libraries:

* Kluent
* Mockk
* Spek

#### Requirements

* JDK 21

### Build

Run `./gradlew clean shadowJar`

### Test
Run test: `./gradlew  test`


### Lint (Ktlint)
##### Command line
Run checking: `./gradlew --continue ktlintCheck`

Run formatting: `./gradlew ktlintFormat`

##### Git Hooks
Apply checking: `./gradlew addKtlintCheckGitPreCommitHook`

Apply formatting: `./gradlew addKtlintFormatGitPreCommitHook`

### Lint

Kjør `./gradlew --continue ktlintCheck`


## Contact

### For NAV employees

We are available at the Slack channel `#isyfo`.
