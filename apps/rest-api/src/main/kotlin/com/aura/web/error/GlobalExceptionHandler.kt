package com.aura.web.error

import com.aura.security.ProblemWriter
import com.aura.shared.ApiException
import com.aura.shared.FieldError
import jakarta.servlet.http.HttpServletRequest
import java.net.URI
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.slf4j.LoggerFactory

/**
 * Единый контракт ошибок — RFC 9457 application/problem+json с расширениями
 * `code` (машинный код) и `errors` (детали валидации), см. docs/api-design.md.
 *
 * Ошибки безопасности (401/403) отдаются security-хендлерами (см. com.aura.security),
 * так как фильтр-цепочка работает до DispatcherServlet.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /** Доменные ошибки модулей (подклассы [ApiException] из `*.api`-пакетов). */
    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException, request: HttpServletRequest): ResponseEntity<Any> =
        problem(ex.status, ex.code, ex.message, request.requestURI, ex.fieldErrors)

    /** Бизнес-валидация тела запроса (Bean Validation на @RequestBody) — 422. */
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: org.springframework.http.HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val errors = ex.bindingResult.fieldErrors.map {
            FieldError(field = it.field, message = it.defaultMessage ?: "некорректное значение")
        }
        return problem(422, "VALIDATION_FAILED", "Некоторые поля некорректны", currentPath(request), errors)
    }

    /** Некорректный JSON / типы — 400. */
    override fun handleHttpMessageNotReadable(
        ex: org.springframework.http.converter.HttpMessageNotReadableException,
        headers: org.springframework.http.HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> =
        problem(400, "BAD_REQUEST", "Тело запроса некорректно", currentPath(request))

    /** Отсутствующий или некорректный параметр запроса — 400. */
    override fun handleTypeMismatch(
        ex: org.springframework.beans.TypeMismatchException,
        headers: org.springframework.http.HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> =
        problem(400, "BAD_REQUEST", "Параметр запроса некорректен", currentPath(request))

    /** Отдача problem+json для остальных стандартных ситуаций (404, 405, 415…). */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: org.springframework.http.HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val code = codeFor(status)
        val detail = (body as? ProblemDetail)?.detail ?: detailFor(status)
        return problem(status.value(), code, detail, currentPath(request))
    }

    /** Непредвиденная ошибка — 500 без утечки внутренностей (стектрейс только в логе). */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<Any> {
        log.error("Unhandled exception on {} {}", request.method, request.requestURI, ex)
        return problem(500, "INTERNAL_ERROR", "Внутренняя ошибка сервера", request.requestURI)
    }

    private fun problem(
        status: Int,
        code: String,
        detail: String,
        instance: String,
        errors: List<FieldError> = emptyList(),
    ): ResponseEntity<Any> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail)
        problem.type = URI.create("${ProblemWriter.TYPE_BASE}${code.lowercase().replace('_', '-')}")
        problem.instance = URI.create(instance)
        problem.setProperty("code", code)
        if (errors.isNotEmpty()) {
            problem.setProperty("errors", errors)
        }
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }

    private fun codeFor(status: HttpStatusCode): String = when (status.value()) {
        400 -> "BAD_REQUEST"
        401 -> "UNAUTHORIZED"
        403 -> "FORBIDDEN"
        404 -> "NOT_FOUND"
        405 -> "METHOD_NOT_ALLOWED"
        415 -> "UNSUPPORTED_MEDIA_TYPE"
        else -> "REQUEST_ERROR"
    }

    private fun detailFor(status: HttpStatusCode): String = when (status.value()) {
        404 -> "Ресурс не найден"
        405 -> "Метод не поддерживается для этого ресурса"
        else -> status.toString()
    }

    private fun currentPath(request: WebRequest): String =
        (request as? org.springframework.web.context.request.ServletRequestAttributes)?.request?.requestURI
            ?: request.getDescription(false).removePrefix("uri=")
}
