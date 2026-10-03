// detekt: дефолтный конфиг + переопределения из config/detekt.yml репозитория.
// Анализируем только рукописный код — сгенерированный (build/generated) исключён.
plugins {
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootDir.resolve("config/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}
