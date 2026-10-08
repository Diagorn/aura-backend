package com.aura.web.catalog

import com.aura.api.CatalogApi
import com.aura.api.model.CreateEmotionRequest
import com.aura.api.model.CreateEventRequest
import com.aura.api.model.CreateFactorRequest
import com.aura.api.model.CreateMetricRequest
import com.aura.api.model.EmotionResponse
import com.aura.api.model.EventResponse
import com.aura.api.model.FactorResponse
import com.aura.api.model.MetricResponse
import com.aura.api.model.UpdateEmotionRequest
import com.aura.api.model.UpdateEventRequest
import com.aura.api.model.UpdateFactorRequest
import com.aura.api.model.UpdateMetricRequest
import com.aura.catalog.api.EmotionsPort
import com.aura.catalog.api.EventsPort
import com.aura.catalog.api.FactorsPort
import com.aura.catalog.api.MetricsPort
import com.aura.security.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * Справочники чек-ина (/api/v1/catalog/...).
 * Контракт — сгенерированный [CatalogApi]; данные — через публичные порты модуля catalog.
 */
@RestController
class CatalogController(
    private val emotions: EmotionsPort,
    private val factors: FactorsPort,
    private val events: EventsPort,
    private val metrics: MetricsPort,
) : CatalogApi {

    override fun listEmotions(includeInactive: Boolean): ResponseEntity<List<EmotionResponse>> =
        ResponseEntity.ok(emotions.list(CurrentUser.requireUserId(), includeInactive).map { it.toResponse() })

    override fun createEmotion(createEmotionRequest: CreateEmotionRequest): ResponseEntity<EmotionResponse> =
        created(emotions.create(CurrentUser.requireUserId(), createEmotionRequest.toCommand()).toResponse())

    override fun updateEmotion(id: Long, updateEmotionRequest: UpdateEmotionRequest): ResponseEntity<EmotionResponse> =
        ResponseEntity.ok(
            emotions.update(CurrentUser.requireUserId(), id, updateEmotionRequest.toCommand(), CurrentUser.isAdmin())
                .toResponse(),
        )

    override fun deleteEmotion(id: Long): ResponseEntity<Unit> {
        emotions.delete(CurrentUser.requireUserId(), id)
        return ResponseEntity.noContent().build()
    }

    override fun listFactors(includeInactive: Boolean): ResponseEntity<List<FactorResponse>> =
        ResponseEntity.ok(factors.list(CurrentUser.requireUserId(), includeInactive).map { it.toResponse() })

    override fun createFactor(createFactorRequest: CreateFactorRequest): ResponseEntity<FactorResponse> =
        created(factors.create(CurrentUser.requireUserId(), createFactorRequest.toCommand()).toResponse())

    override fun updateFactor(id: Long, updateFactorRequest: UpdateFactorRequest): ResponseEntity<FactorResponse> =
        ResponseEntity.ok(
            factors.update(CurrentUser.requireUserId(), id, updateFactorRequest.toCommand(), CurrentUser.isAdmin())
                .toResponse(),
        )

    override fun deleteFactor(id: Long): ResponseEntity<Unit> {
        factors.delete(CurrentUser.requireUserId(), id)
        return ResponseEntity.noContent().build()
    }

    override fun listEvents(includeInactive: Boolean): ResponseEntity<List<EventResponse>> =
        ResponseEntity.ok(events.list(CurrentUser.requireUserId(), includeInactive).map { it.toResponse() })

    override fun createEvent(createEventRequest: CreateEventRequest): ResponseEntity<EventResponse> =
        created(events.create(CurrentUser.requireUserId(), createEventRequest.toCommand()).toResponse())

    override fun updateEvent(id: Long, updateEventRequest: UpdateEventRequest): ResponseEntity<EventResponse> =
        ResponseEntity.ok(
            events.update(CurrentUser.requireUserId(), id, updateEventRequest.toCommand(), CurrentUser.isAdmin())
                .toResponse(),
        )

    override fun deleteEvent(id: Long): ResponseEntity<Unit> {
        events.delete(CurrentUser.requireUserId(), id)
        return ResponseEntity.noContent().build()
    }

    override fun listMetrics(includeInactive: Boolean): ResponseEntity<List<MetricResponse>> =
        ResponseEntity.ok(metrics.list(CurrentUser.requireUserId(), includeInactive).map { it.toResponse() })

    override fun createMetric(createMetricRequest: CreateMetricRequest): ResponseEntity<MetricResponse> =
        created(metrics.create(CurrentUser.requireUserId(), createMetricRequest.toCommand()).toResponse())

    override fun updateMetric(id: Long, updateMetricRequest: UpdateMetricRequest): ResponseEntity<MetricResponse> =
        ResponseEntity.ok(
            metrics.update(CurrentUser.requireUserId(), id, updateMetricRequest.toCommand(), CurrentUser.isAdmin())
                .toResponse(),
        )

    override fun deleteMetric(id: Long): ResponseEntity<Unit> {
        metrics.delete(CurrentUser.requireUserId(), id)
        return ResponseEntity.noContent().build()
    }

    private fun <T : Any> created(body: T): ResponseEntity<T> = ResponseEntity.status(HttpStatus.CREATED).body(body)
}
