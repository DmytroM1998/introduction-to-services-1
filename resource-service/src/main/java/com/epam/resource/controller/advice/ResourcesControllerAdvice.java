package com.epam.resource.controller.advice;

import com.epam.resource.dto.ErrorResponse;
import com.epam.resource.exception.ErrorCodeException;
import com.epam.resource.util.MessageConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class ResourcesControllerAdvice {

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
    String contentType = ex.getContentType() == null
        ? "unknown"
        : ex.getContentType().getType() + "/" + ex.getContentType().getSubtype();
    ErrorResponse errorResponse = new ErrorResponse(
        String.format("Invalid file format: %s. Only MP3 files are allowed", contentType),
        String.valueOf(HttpStatus.BAD_REQUEST.value())
    );
    return ResponseEntity.badRequest().body(errorResponse);
  }
  
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
    ErrorResponse errorResponse = new ErrorResponse(
        String.format("Parameter '%s' is invalid", ex.getParameter().getParameter()), 
        String.valueOf(HttpStatus.BAD_REQUEST.value())
    );
    return ResponseEntity.badRequest().body(errorResponse);
  }

  @ExceptionHandler(ErrorCodeException.class)
  public ResponseEntity<ErrorResponse> handleErrorCodeException(ErrorCodeException e) {
    ErrorResponse errorResponse = new ErrorResponse(e.getMessage(), String.valueOf(e.getErrorCode()));
    return ResponseEntity.status(HttpStatus.valueOf(e.getErrorCode())).body(errorResponse);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleNullPointerException(Exception e) {
    log.error(e.getMessage(), e);
    ErrorResponse errorResponse = new ErrorResponse(MessageConstants.SOMETHING_WENT_WRONG, 
        String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
  }
}
