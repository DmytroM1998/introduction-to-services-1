package com.epam.mf.api_gateway;

import jakarta.servlet.http.HttpServletRequest;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GatewayExceptionHandler {

  public static final String MSG_SERVICE_UNAVAILABLE = "The requested service is temporarily unavailable. Please try again later.";
  public static final String MSG_EP_NOT_FOUND = "The requested endpoint was not found.";

  @ExceptionHandler(Exception.class)
  public ResponseEntity<GatewayErrorResponse> handleException(
      Exception exception, HttpServletRequest request) {
    HttpStatus status = isServiceUnavailable(exception)
        ? HttpStatus.SERVICE_UNAVAILABLE
        : statusFor(exception);

    return ResponseEntity.status(status)
        .body(new GatewayErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
            getResponseMessage(status), request.getRequestURI()));
  }

  private HttpStatus statusFor(Exception exception) {
    HttpStatus resolvedStatus = null;
    if (exception instanceof ErrorResponse errorResponse) {
      resolvedStatus = HttpStatus.resolve(errorResponse.getStatusCode().value());
    }
    return ObjectUtils.getIfNull(resolvedStatus, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private String getResponseMessage(HttpStatus status) {
    return switch (status) {
      case NOT_FOUND -> 
          MSG_EP_NOT_FOUND;
      case BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT ->
          MSG_SERVICE_UNAVAILABLE;
      default -> status.is5xxServerError()
          ? "The request could not be completed. Please try again later."
          : "The request could not be completed.";
    };
  }

  private boolean isServiceUnavailable(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConnectException
          || cause instanceof SocketTimeoutException
          || cause instanceof UnknownHostException) {
        return true;
      }
      String className = cause.getClass().getName();
      if (className.contains("loadbalancer")
          && (className.endsWith("NotFoundException")
              || className.endsWith("NoAvailableServiceInstanceException"))) {
        return true;
      }
    }
    return false;
  }

  public record GatewayErrorResponse(
      Instant timestamp,
      int status,
      String error,
      String message,
      String path) {
  }
}
