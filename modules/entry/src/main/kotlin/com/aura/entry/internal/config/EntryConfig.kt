package com.aura.entry.internal.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** Конфигурация модуля entry: параметры (суточный лимит записей). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EntryProperties::class)
class EntryConfig
