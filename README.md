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
