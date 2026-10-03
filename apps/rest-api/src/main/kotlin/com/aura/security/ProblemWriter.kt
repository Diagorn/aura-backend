package com.aura.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URI
import java.nio.charset.StandardCharsets
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import tools.jackson.databind.ObjectMapper

/**
 * Запись RFC 9457 problem+json из фильтра и security-хендлеров:
 * @RestControllerAdvice не ловит исключения до DispatcherServlet (см. problem-details-rfc9457).
 */
class ProblemWriter(private val objectMapper: ObjectMapper) {

    fun write(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: Int,
        code: String,
        detail: String,
    ) {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail)
        problem.type = URI.create(TYPE_BASE + code.lowercase().replace('_', '-'))
        problem.instance = URI.create(request.requestURI)
        problem.setProperty("code", code)
        response.status = status
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        objectMapper.writeValue(response.writer, problem)
    }

    companion object {
        const val TYPE_BASE = "https://aura.example.com/problems/"
    }
}
