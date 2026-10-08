plugins {
    id("aura.spring.app")
    alias(libs.plugins.openapi.generator)
}

dependencies {
    implementation(platform(libs.spring.boot.bom))

    implementation(project(":modules:shared"))
    implementation(project(":modules:auth"))
    implementation(project(":modules:user"))
    implementation(project(":modules:catalog"))
    implementation(project(":modules:entry"))
    implementation(project(":modules:note"))
    implementation(project(":modules:analytics"))
    implementation(project(":modules:notification"))

    implementation(libs.bundles.spring.app)
    implementation(libs.spring.boot.starter.validation)
    // Доменные события Spring Modulith (in-process) + модульная структура
    implementation(libs.spring.modulith.starter.core)

    runtimeOnly(libs.liquibase.core)
    // Boot 4: авто-конфигурация Liquibase вынесена в отдельный модуль (см. docs/liquibase-migrations.md)
    runtimeOnly("org.springframework.boot:spring-boot-liquibase")
    runtimeOnly(libs.postgresql)
    // Spring Data + Kotlin: интроспекция сущностей требует kotlin-reflect в runtime
    runtimeOnly(libs.kotlin.reflect)
    // Swagger UI (статическая страница docs/index.html читает спеку из /openapi/api.yaml)
    runtimeOnly(libs.swagger.ui)

    testImplementation(libs.bundles.spring.tests)
    testImplementation(libs.bundles.mockk)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.spring.boot.security.test)
    // Slice-тесты контроллеров: @WebMvcTest + MockMvc (Boot 4 — модульный стартер)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    // Проверка архитектурных правил (слои, границы модулей)
    testImplementation(libs.archunit.junit5)
    // Интеграционные тесты на Testcontainers (настоящий Postgres, см. docs/local-dev.md)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.boot.resttestclient)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

// Генерация серверных интерфейсов + DTO из OpenAPI-спеки (см. docs/spec-first-workflow.md).
// Сгенерированный код живёт в build/ и не коммитится.
val generateApi = tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generateApi") {
    input = rootDir.resolve("api/openapi/api.yaml").absolutePath
    outputDir = layout.buildDirectory.dir("generated/api-openapi").get().asFile.absolutePath
    generatorName = "kotlin-spring"
    apiPackage = "com.aura.api"
    modelPackage = "com.aura.api.model"
    cleanupOutput = true
    // $ref-файлы (paths/, components/) генератор не видит: без этого задача
    // считает себя up-to-date и восстанавливает из кэша генерацию по старой спеке
    inputs.dir(rootDir.resolve("api/openapi")).withPathSensitivity(PathSensitivity.RELATIVE)
    configOptions = mapOf(
        "interfaceOnly" to "true",
        "skipDefaultInterface" to "true",
        "serviceImplementation" to "false",
        "useSpringBoot3" to "true",
        "useJakartaEe" to "true",
        "serializationLibrary" to "jackson",
        "documentationProvider" to "none",
        "useBeanValidation" to "true",
        "enumPropertyNaming" to "UPPERCASE",
        // Отдельный интерфейс на каждый тег спеки: AuthApi, MeApi, InternalApi
        "useTags" to "true",
    )
}

val generatedSourcesDir = layout.buildDirectory.dir("generated/api-openapi/src/main/kotlin")

sourceSets {
    main {
        kotlin {
            srcDir(generatedSourcesDir)
        }
    }
}

// Спека — источник истины: кладём её в jar как статику, чтобы Swagger UI
// на /docs/index.html читал именно её (см. docs/spec-first-workflow.md).
tasks.named<ProcessResources>("processResources") {
    from(rootDir.resolve("api/openapi")) {
        into("static/openapi")
    }
    // Версия webjar'а подставляется из каталога libs — единого источника версий.
    filesMatching("static/docs/index.html") {
        expand("swaggerUiVersion" to libs.versions.swagger.ui.get())
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateApi)
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // Локальный профиль по умолчанию для bootRun; прод задаёт профиль явно.
    args("--spring.profiles.active=local")
}
