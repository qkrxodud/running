plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.runningcrew"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        // Java 25 (LTS). 로컬에 JDK 25가 없으면 foojay resolver(settings.gradle.kts)가 자동 다운로드.
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Flyway (MySQL). V1__init.sql 자동 적용.
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // R-003 재발 방지: Flyway 마이그레이션을 실 MySQL 8(Testcontainers)에 적용하는 라이브 테스트.
    // DB 미접촉 슬라이스 테스트는 예약어 구문 오류 유형을 구조적으로 검출 못 한다.
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
