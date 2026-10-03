plugins {
    id("aura.spring.module")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.bundles.spring.module)
    // Выпуск и проверка JWT (HS256) на Nimbus из Spring Security
    implementation(libs.spring.security.oauth2.jose)
    implementation(project(":modules:shared"))
    // Аннотация @NamedInterface для package-info (в рантайме класс даёт приложение)
    compileOnly(libs.spring.modulith.core)

    testImplementation(libs.bundles.spring.tests)
    testImplementation(libs.bundles.mockk)
}
