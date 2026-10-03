package com.aura.auth.internal.repository

import com.aura.auth.internal.entity.ServiceClientEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ServiceClientRepository : JpaRepository<ServiceClientEntity, Long> {

    fun findByClientId(clientId: String): ServiceClientEntity?
}
