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

git Application (git)                                                                             | Redis database                         
--------------------------------------------------------------------------------------------- | --------------
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
[ismanglendemedvirkning](https://github.com/navikt/ismanglendemedvirkning)                    | 11
[ismeroppfolging](https://github.com/navikt/ismeroppfolging)                                  | 12
[isnarmesteleder](https://github.com/navikt/isnarmesteleder)                                  | 13
[isoppfolgingstilfelle](https://github.com/navikt/isoppfolgingstilfelle)                      | 14
[ispengestopp](https://github.com/navikt/ispengestopp)                                        | 15
[padm2](https://github.com/navikt/padm2)                                                      | 16
[syfobehandlendeenhet](https://github.com/navikt/syfobehandlendeenhet)                        | 17
[syfomodiaperson](https://github.com/navikt/syfomodiaperson)                                  | 18
[syfomoteoversikt](https://github.com/navikt/syfomoteoversikt)                                | 19
[syfooversikt](https://github.com/navikt/syfooversikt)                                        | 20
[syfooversiktsrv](https://github.com/navikt/syfooversiktsrv)                                  | 21
[syfopartnerinfo](https://github.com/navikt/syfopartnerinfo)                                  | 22
[syfoperson](https://github.com/navikt/syfoperson)                                            | 23
[syfoveileder](https://github.com/navikt/syfoveileder)                                        | 24

Man kan aksessere Redis-cachene på Aiven fra kommandolinja ved behov (feks hvis man trenger å flushe en cache).

For å gjøre dette trenger man en Redis-klient, man kan bruke den offisielle https://redis.io/docs/latest/develop/tools/cli/ 
men denne kan være krevende å installere på noen OS (feks Windows). Et godt alternativ er redli:
https://github.com/IBM-Cloud/redli

Host, port, brukernavn og passord for å koble til Aiven Redis finner man i secret'en `redis-teamsykefravr-cache` 
(som finnes både i dev-gcp og prod-gcp i vårt namespace).

Feks for å flushe cachen til isnarmesteleder:
```
$ redli -u rediss://default:xxx@<host>:<port>
> ping
PONG
> select 13
OK
> dbsize
(integer) 9
> flushdb
OK
> dbsize
(integer) 0
```

NB! Flushing av cache (særlig `istilgangskontroll` sin) kan skape tregheter/timeouts i produksjon og bør bare gjøres ved behov.

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
