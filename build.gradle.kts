import com.adarshr.gradle.testlogger.theme.ThemeType

group = "no.nav.syfo"
version = "0.0.1"

val jacksonDataType = "2.20.1"
val ktor = "3.3.3"
val logback = "1.5.21"
val logbackSyslog4jVersion = "1.0.0"
val logstashEncoder = "9.0"
val micrometerRegistry = "1.16.1"
val mockk = "1.14.7"
val nimbusJoseJwt = "10.6"
val jedis = "5.2.0"
val microsoftGraphVersion = "6.58.0"

plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "8.3.8"
    id("org.jlleitschuh.gradle.ktlint") version "11.4.1"
    id("com.adarshr.test-logger") version "4.0.0"
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.ktor:ktor-client-apache:$ktor")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-jackson:$ktor")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor")
    implementation("io.ktor:ktor-server-call-id:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")

    // Microsoft Graph SDK
    implementation("com.microsoft.graph:microsoft-graph:$microsoftGraphVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logback")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoder")
    implementation("com.papertrailapp:logback-syslog4j:$logbackSyslog4jVersion")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktor")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistry")

    // Cache
    implementation("redis.clients:jedis:$jedis")

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonDataType")

    // Tests
    testImplementation("io.ktor:ktor-server-test-host:$ktor")
    testImplementation("io.mockk:mockk:$mockk")
    testImplementation("io.ktor:ktor-client-mock:$ktor")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwt")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        manifest.attributes["Main-Class"] = "no.nav.syfo.AppKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    shadowJar {
        isZip64 = true
        mergeServiceFiles()
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    test {
        useJUnitPlatform()
        testlogger {
            theme = ThemeType.STANDARD_PARALLEL
            showFullStackTraces = true
            showPassed = false
        }
    }
}
