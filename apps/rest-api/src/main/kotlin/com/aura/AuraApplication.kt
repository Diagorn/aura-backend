package com.aura

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AuraApplication

fun main(args: Array<String>) {
    runApplication<AuraApplication>(*args)
}
