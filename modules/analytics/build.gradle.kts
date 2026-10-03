// Аналитика: собственных таблиц нет, данные — через публичный query-порт модуля entry
// (см. docs/domain-model.md, раздел «Аналитика и доступ к данным»).
plugins {
    id("aura.spring.module")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.bundles.spring.module)
    implementation(project(":modules:shared"))

    testImplementation(libs.bundles.spring.tests)
    testImplementation(libs.bundles.mockk)
}
