plugins {
    id("aura.spring.module")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.bundles.spring.module)
    implementation(project(":modules:shared"))
    // entry_date по таймзоне пользователя; валидация элементов справочников в записях
    implementation(project(":modules:user"))
    implementation(project(":modules:catalog"))
    // Аннотация @NamedInterface для package-info (в рантайме класс даёт приложение)
    compileOnly(libs.spring.modulith.core)

    testImplementation(libs.bundles.spring.tests)
    testImplementation(libs.bundles.mockk)
}
