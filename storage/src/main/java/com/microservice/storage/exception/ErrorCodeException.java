package com.microservice.storage.exception;

import lombok.Getter;

@Getter
public class ErrorCodeException extends RuntimeException {

  private final int errorCode;

  public ErrorCodeException(int errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
