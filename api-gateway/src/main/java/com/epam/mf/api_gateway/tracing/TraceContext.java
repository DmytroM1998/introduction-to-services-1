package com.epam.mf.api_gateway.tracing;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceContext {

  public static final String HTTP_HEADER_TRACE_ID = "X-Trace-Id";
  public static final String AMQP_HEADER_TRACE_ID = "x-trace-id";
  public static final String MDC_TRACE_ID_KEY = "traceId";

  private TraceContext() {}

  public static String initTraceId(String incomingId) {
    String traceId = incomingId == null || incomingId.isBlank()
        ? UUID.randomUUID().toString()
        : incomingId.trim();
    MDC.put(MDC_TRACE_ID_KEY, traceId);
    return traceId;
  }

  public static String getTraceId() {
    return MDC.get(MDC_TRACE_ID_KEY);
  }

  public static void clear() {
    MDC.remove(MDC_TRACE_ID_KEY);
  }
}
