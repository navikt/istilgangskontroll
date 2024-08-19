import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = "no.nav.syfo"
version = "0.0.1"

val jacksonDataType = "2.17.2"
val kluent = "1.73"
val ktor = "2.3.12"
val logback = "1.5.7"
val logbackSyslog4jVersion = "1.0.0"
val logstashEncoder = "7.4"
val micrometerRegistry = "1.13.3"
val mockk = "1.13.12"
val nimbusJoseJwt = "9.40"
val spek = "2.0.19"
val jedis = "5.1.4"

plugins {
    kotlin("jvm") version "1.9.23"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.4.1"
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
    testImplementation("io.ktor:ktor-server-tests:$ktor")
    testImplementation("io.mockk:mockk:$mockk")
    testImplementation("io.ktor:ktor-client-mock:$ktor")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwt")
    testImplementation("org.amshove.kluent:kluent:$kluent")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spek")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spek")
}

kotlin {
    jvmToolchain(17)
}

tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.AppKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    withType<ShadowJar> {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
