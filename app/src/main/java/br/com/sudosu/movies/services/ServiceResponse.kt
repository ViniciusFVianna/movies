package br.com.sudosu.movies.services

enum class HttpErrorCode(val code: Int) {
    INVALID_REQUEST(400),
    UNAUTHORIZED(401),
    NOT_FOUND(404),
    INTERNAL_SERVER_ERROR(500);

    companion object {
        fun fromCode(code: Int?): HttpErrorCode? = values().firstOrNull { it.code == code }

    }
}

sealed class RequestError(val code: Int?) {
    class HttpError(code: Int?) : RequestError(code)
    class MoneddVendasError(
        code: Int?,
        val message: String? = null,
    ): RequestError(code)
}

data class ServiceResponse<T>(
    val data: T? = null,
    val error: RequestError? = null
) {

    companion object {

        fun <T> httpError(code: Int?): ServiceResponse<T> =
            ServiceResponse(error = RequestError.HttpError(code))

        fun <T> moneddVendasError(
            code: Int?,
            message: String? = null
        ): ServiceResponse<T> =
            ServiceResponse(error = RequestError.MoneddVendasError(code, message))

        fun <T> success(data: T?): ServiceResponse<T> = ServiceResponse(data = data)

    }

}