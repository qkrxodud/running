plugins {
    // JDK 25가 로컬에 없을 때 Gradle 툴체인이 자동으로 내려받도록 foojay resolver 사용.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "running-crew-backend"
