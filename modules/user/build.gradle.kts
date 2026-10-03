plugins {
    id("aura.spring.module")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.bundles.spring.module)
    implementation(project(":modules:shared"))
    // Публичный контракт auth (профиль хранится в схеме auth, своего хранилища у user нет)
    implementation(project(":modules:auth"))
    // Аннотация @NamedInterface для package-info (в рантайме класс даёт приложение)
    compileOnly(libs.spring.modulith.core)

    testImplementation(libs.bundles.spring.tests)
    testImplementation(libs.bundles.mockk)
}
