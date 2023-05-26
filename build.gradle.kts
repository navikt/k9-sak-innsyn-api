import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.0.6"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.spring") version "1.8.21"
    kotlin("plugin.jpa") version "1.8.21"
}

group = "no.nav"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

val springCloudVersion = "4.0.3"
val springdocVersion = "2.1.0"
val logstashLogbackEncoderVersion = "7.3"
val tokenSupportVersion = "3.0.12"
val k9FormatVersion = "8.0.9"
val retryVersion = "2.0.1"
val zalandoVersion = "0.27.0"
val postgresqlVersion = "42.6.0"
val hibernateTypes52Version = "2.20.0"
val awailitilityKotlinVersion = "4.2.0"
val assertkJvmVersion = "0.26"
val springMockkVersion = "4.0.2"
val mockkVersion = "1.13.5"
val guavaVersion = "31.1-jre"
val orgJsonVersion = "20230227"
val k9FellesVersion = "2.0.0"
val testcontainersVersion ="1.18.1"

repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/navikt/dusseldorf-ktor")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }

    maven {
        name = "k9Felles"
        url = uri("https://maven.pkg.github.com/navikt/k9-felles")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("READER_TOKEN") ?: System.getenv("GITHUB_TOKEN")
        }
    }

    maven {
        name = "confluent"
        url = uri("https://packages.confluent.io/maven/")
    }

    maven("https://jitpack.io")
}
dependencies {
    implementation("org.yaml:snakeyaml:2.0") {
        because("https://github.com/navikt/k9-sak-innsyn-api/security/dependabot/2")
    }

    // NAV
    implementation("no.nav.k9:soknad:$k9FormatVersion")
    implementation("no.nav.k9:innsyn:$k9FormatVersion")

    implementation("no.nav.security:token-validation-spring:$tokenSupportVersion")
    implementation("no.nav.security:token-client-spring:$tokenSupportVersion")

    testImplementation("no.nav.security:token-validation-spring-test:$tokenSupportVersion")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web") {
        //exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    //implementation("org.springframework.boot:spring-boot-starter-jetty") /// TODO: Til jetty får støtte fro Servlet 6.0
    implementation("org.springframework.retry:spring-retry:$retryVersion")
    implementation("org.springframework:spring-aspects")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
    }

    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.9.3")

    // Spring Cloud
    // https://mvnrepository.com/artifact/org.springframework.cloud/spring-cloud-starter-contract-stub-runner
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner:$springCloudVersion")

    // Swagger (openapi 3)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // Metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Logging
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation("no.nav.k9.felles:k9-felles-log:$k9FellesVersion")
    runtimeOnly("com.papertrailapp:logback-syslog4j:1.0.0")

    // Database
    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    //Kafka
    implementation("org.springframework.kafka:spring-kafka")
    constraints {
        implementation("org.scala-lang:scala-library") {
            because("org.apache.kafka:kafka_2.13:3.3.2 -> https://www.cve.org/CVERecord?id=CVE-2022-36944")
            version {
                require("2.13.9")
            }
        }
    }
    testImplementation("org.springframework.kafka:spring-kafka-test")

    // Diverse
    implementation("org.json:json:$orgJsonVersion")

    implementation("com.google.guava:guava:$guavaVersion")
    testImplementation("org.awaitility:awaitility-kotlin:$awailitilityKotlinVersion")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:$assertkJvmVersion")
    testImplementation("com.ninja-squad:springmockk:$springMockkVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")

    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.getByName<Jar>("jar") {
    enabled = false
}
