package com.epam.mf.api_gateway;

import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayExceptionHandlerTests {

  private final GatewayExceptionHandler exceptionHandler = new GatewayExceptionHandler();

  @Test
  void returnsNotFoundResponse() {
    MockHttpServletRequest request = createMockRequest("/unknown");

    var response = exceptionHandler.handleException(
        new ResponseStatusException(HttpStatus.NOT_FOUND), request);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("The requested endpoint was not found.", response.getBody().message());
    assertEquals("/unknown", response.getBody().path());
  }

  @Test
  void returnsServiceUnavailableResponse() {
    MockHttpServletRequest request = createMockRequest("/songs");

    var response = exceptionHandler.handleException(
        new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE), request);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    assertEquals(GatewayExceptionHandler.MSG_SERVICE_UNAVAILABLE,
        response.getBody().message());
  }

  @Test
  void mapsConnectionFailuresToServiceUnavailable() {
    MockHttpServletRequest request = createMockRequest("/songs");

    var response = exceptionHandler.handleException(new ConnectException(), request);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    assertEquals(GatewayExceptionHandler.MSG_SERVICE_UNAVAILABLE,
        response.getBody().message());
  }

  private MockHttpServletRequest createMockRequest(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(path);
    return request;
  }
}
