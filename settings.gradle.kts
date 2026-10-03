plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "aura-backend"

includeBuild("build-logic")

include(
    ":modules:shared",
    ":modules:auth",
    ":modules:user",
    ":modules:catalog",
    ":modules:entry",
    ":modules:note",
    ":modules:analytics",
    ":modules:notification",
    ":apps:rest-api",
)
