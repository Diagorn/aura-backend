plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.detekt) apply false
    // Kover в корне — агрегация покрытия со всех модулей (koverHtmlReport / koverXmlReport)
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":modules:shared"))
    kover(project(":modules:auth"))
    kover(project(":modules:user"))
    kover(project(":modules:catalog"))
    kover(project(":modules:entry"))
    kover(project(":modules:note"))
    kover(project(":modules:analytics"))
    kover(project(":modules:notification"))
    kover(project(":apps:rest-api"))
}
