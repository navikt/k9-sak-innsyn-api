import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateClientTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.expediagroup.graphql") version "8.8.1"
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    kotlin("plugin.jpa") version "2.1.21"
}

group = "no.nav"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
    testCompileOnly {
        exclude(group="junit", module = "junit")
    }
}

val graphQLKotlinVersion = "8.8.1"
val springCloudVersion = "4.3.0"
val springdocVersion = "2.8.9"
val logstashLogbackEncoderVersion = "8.1"
val tokenSupportVersion = "5.0.29"
val k9FormatVersion = "9.5.2"
val retryVersion = "2.0.12"
val zalandoVersion = "0.27.0"
val postgresqlVersion = "42.7.7"
val hibernateTypes52Version = "2.20.0"
val awailitilityKotlinVersion = "4.3.0"
val assertkJvmVersion = "0.28.1"
val springMockkVersion = "4.0.2"
val mockkVersion = "1.14.2"
val guavaVersion = "33.4.8-jre"
val orgJsonVersion = "20250517"
val k9FellesVersion = "2.0.8"
val k9FormidlingVersion = "1.0.7"
val k9SakVersion = "4.1.8"
val openhtmltopdfVersion = "1.0.10"
val handlebarsVersion = "4.4.0"

val testcontainersVersion ="1.21.1"

repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/navikt/dusseldorf-ktor")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: "k9-sak-innsyn-api"
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
dependencies {
    implementation("org.yaml:snakeyaml:2.4") {
        because("https://github.com/navikt/k9-sak-innsyn-api/security/dependabot/2")
    }

    // NAV
    implementation("no.nav.k9:soknad:$k9FormatVersion")
    implementation("no.nav.k9:innsyn:$k9FormatVersion")
    implementation("no.nav.k9:konstant:$k9FormatVersion")

    implementation("no.nav.k9.formidling:eksterne-kontrakter:$k9FormidlingVersion")
    implementation("no.nav.k9.sak:kodeverk:$k9SakVersion")

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

    // Spring Cloud
    // https://mvnrepository.com/artifact/org.springframework.cloud/spring-cloud-starter-contract-stub-runner
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner:$springCloudVersion")

    //graphql
    implementation("com.expediagroup:graphql-kotlin-spring-client:$graphQLKotlinVersion")

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
    implementation("org.flywaydb:flyway-database-postgresql")
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

    // PDF
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:$openhtmltopdfVersion")
    implementation("com.openhtmltopdf:openhtmltopdf-slf4j:$openhtmltopdfVersion")
    implementation("com.github.jknack:handlebars:$handlebarsVersion")

    // Diverse
    implementation("org.json:json:$orgJsonVersion")

    implementation("com.google.guava:guava:$guavaVersion")
    testImplementation("org.awaitility:awaitility-kotlin:$awailitilityKotlinVersion")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:$assertkJvmVersion")
    testImplementation("com.ninja-squad:springmockk:$springMockkVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")

    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }

    withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "21"
        }
    }

    getByName<Jar>("jar") {
        enabled = false
    }

    withType<Wrapper> {
        gradleVersion = "8.2.1"
    }
}

val graphqlGenerateOtherClient by tasks.creating(GraphQLGenerateClientTask::class) {
    queryFileDirectory.set(file("${project.projectDir}/src/main/resources/safselvbetjening"))
    schemaFile.set(file("${project.projectDir}/src/main/resources/safselvbetjening/saf-selvbetjening-sdl.graphqls"))
    packageName.set("no.nav.sifinnsynapi.safselvbetjening.generated")
}
