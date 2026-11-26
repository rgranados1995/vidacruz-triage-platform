package com.vidacruz.assistant.api.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.vidacruz.assistant.model.exception.BusinessException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import java.util.List;

/**
 * Builds the error body of every failure. Unexpected failures get a generic message: their detail goes to the log,
 * never to the client. An unavailable model is a 503 with a clear message, never a 500 (degraded mode).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiErrors {

    static final String QUESTION_FIELD = "pregunta";
    private static final String MALFORMED_BODY = "El cuerpo debe ser un JSON válido";
    private static final String UNAVAILABLE_DETAIL = "Intente de nuevo en unos minutos; mientras tanto, consulte los "
            + "protocolos clínicos directamente";
    private static final String STATE_UNAVAILABLE_DETAIL = "Intente de nuevo en unos segundos; el registro de "
            + "pacientes del triaje no se ve afectado";

    /** Maps a failure to its error body; the most specific types come first. */
    public static ApiError from(Throwable error, String path) {
        return switch (error) {
            case BusinessException business -> business(business, path);
            case InvalidRequestException invalid -> invalidRequest(invalid.getField(), invalid.getReason(), path);
            case ServerWebInputException input -> unreadableInput(input, path);
            case ResponseStatusException status -> new ApiError(ApiError.DEFAULT_TYPE,
                    ApiErrorCode.NOT_SUPPORTED.getTitle(), status.getStatusCode().value(), null, path,
                    ApiErrorCode.NOT_SUPPORTED.getCode(), List.of());
            default -> new ApiError(ApiError.DEFAULT_TYPE, ApiErrorCode.UNEXPECTED.getTitle(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value(), null, path, ApiErrorCode.UNEXPECTED.getCode(), List.of());
        };
    }

    public static ApiError unauthorized(String path) {
        return new ApiError(ApiError.DEFAULT_TYPE, ApiErrorCode.UNAUTHORIZED.getTitle(),
                HttpStatus.UNAUTHORIZED.value(), "Envíe la API key en el header X-API-Key", path,
                ApiErrorCode.UNAUTHORIZED.getCode(), List.of());
    }

    private static ApiError business(BusinessException business, String path) {
        var error = business.getError();
        return switch (error) {
            case INVALID_QUESTION -> new ApiError(ApiError.DEFAULT_TYPE, error.getMessage(),
                    HttpStatus.BAD_REQUEST.value(), business.getDetail(), path, error.getCode(),
                    List.of(new ApiFieldError(QUESTION_FIELD, business.getDetail())));
            case ASSISTANT_UNAVAILABLE -> new ApiError(ApiError.DEFAULT_TYPE, error.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE.value(), UNAVAILABLE_DETAIL, path, error.getCode(), List.of());
            case URGENCY_STATE_UNAVAILABLE -> new ApiError(ApiError.DEFAULT_TYPE, error.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE.value(), STATE_UNAVAILABLE_DETAIL, path, error.getCode(),
                    List.of());
        };
    }

    private static ApiError invalidRequest(String field, String reason, String path) {
        var fields = field == null ? List.<ApiFieldError>of() : List.of(new ApiFieldError(field, reason));
        return new ApiError(ApiError.DEFAULT_TYPE, ApiErrorCode.INVALID_REQUEST.getTitle(),
                HttpStatus.BAD_REQUEST.value(), reason, path, ApiErrorCode.INVALID_REQUEST.getCode(), fields);
    }

    /** A body that is not JSON, or a field of the wrong type; Jackson's own message is never exposed. */
    private static ApiError unreadableInput(ServerWebInputException input, String path) {
        var field = jsonField(input);
        return field == null
                ? invalidRequest(null, MALFORMED_BODY, path)
                : invalidRequest(field, "El campo " + field + " tiene un tipo o formato inválido", path);
    }

    private static String jsonField(Throwable error) {
        var cause = error;
        while (cause != null && !(cause instanceof JsonMappingException)) {
            cause = cause.getCause();
        }
        var path = cause == null ? List.<JsonMappingException.Reference>of() : ((JsonMappingException) cause).getPath();
        return path.isEmpty() ? null : path.getLast().getFieldName();
    }
}
