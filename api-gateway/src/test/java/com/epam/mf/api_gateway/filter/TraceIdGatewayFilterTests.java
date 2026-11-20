package com.epam.mf.api_gateway.filter;

import com.epam.mf.api_gateway.tracing.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TraceIdGatewayFilterTests {

  private final TraceIdGatewayFilter filter = new TraceIdGatewayFilter();

  @AfterEach
  void clearTraceContext() {
    TraceContext.clear();
  }

  @Test
  void doFilter_incomingTraceIdProvided_propagatesTraceIdAndClearsContext() throws Exception {
    String traceId = "trace-from-client";
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/songs");
    request.addHeader(TraceContext.HTTP_HEADER_TRACE_ID, traceId);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (wrappedRequest, wrappedResponse) -> {
      assertEquals(traceId, ((HttpServletRequest) wrappedRequest).getHeader(TraceContext.HTTP_HEADER_TRACE_ID));
      assertEquals(traceId, TraceContext.getTraceId());
      assertEquals("api-gateway", MDC.get(TraceContext.MDC_SERVICE_KEY));
    };

    filter.doFilter(request, response, chain);

    assertEquals(traceId, response.getHeader(TraceContext.HTTP_HEADER_TRACE_ID));
    assertNull(TraceContext.getTraceId());
    assertNull(MDC.get(TraceContext.MDC_SERVICE_KEY));
  }

  @Test
  void doFilter_traceIdMissingAndChainFails_generatesTraceIdAndClearsContext() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/songs");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (wrappedRequest, wrappedResponse) -> {
      String traceId = ((HttpServletRequest) wrappedRequest).getHeader(TraceContext.HTTP_HEADER_TRACE_ID);
      assertNotNull(traceId);
      UUID.fromString(traceId);
      assertEquals(traceId, MDC.get(TraceContext.MDC_TRACE_ID_KEY));
      assertEquals("api-gateway", MDC.get(TraceContext.MDC_SERVICE_KEY));
      throw new jakarta.servlet.ServletException("downstream failure");
    };

    assertThrows(jakarta.servlet.ServletException.class, () -> filter.doFilter(request, response, chain));

    String responseTraceId = response.getHeader(TraceContext.HTTP_HEADER_TRACE_ID);
    assertNotNull(responseTraceId);
    UUID.fromString(responseTraceId);
    assertNull(TraceContext.getTraceId());
    assertNull(MDC.get(TraceContext.MDC_SERVICE_KEY));
  }
}
