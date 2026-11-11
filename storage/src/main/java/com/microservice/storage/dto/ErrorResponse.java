package com.microservice.storage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class ErrorResponse {

  public ErrorResponse(String errorMessage, String errorCode) {
    this.errorMessage = errorMessage;
    this.errorCode = errorCode;
  }

  private String errorMessage;

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private Map<String, String> details;

  private String errorCode;
}
