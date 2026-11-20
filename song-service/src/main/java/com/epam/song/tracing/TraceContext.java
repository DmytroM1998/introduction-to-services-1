package com.epam.song.tracing;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceContext {

  public static final String HTTP_HEADER_TRACE_ID = "X-Trace-Id";
  public static final String AMQP_HEADER_TRACE_ID = "x-trace-id";
  public static final String MDC_TRACE_ID_KEY = "traceId";
  public static final String MDC_SERVICE_KEY = "service";
  private static final String SERVICE_NAME = "song-service";

  private TraceContext() {}

  public static String initTraceId(String incomingId) {
    String traceId = incomingId == null || incomingId.isBlank()
        ? UUID.randomUUID().toString()
        : incomingId.trim();
    MDC.put(MDC_TRACE_ID_KEY, traceId);
    MDC.put(MDC_SERVICE_KEY, SERVICE_NAME);
    return traceId;
  }

  public static String getTraceId() {
    return MDC.get(MDC_TRACE_ID_KEY);
  }

  public static void clear() {
    MDC.remove(MDC_TRACE_ID_KEY);
    MDC.remove(MDC_SERVICE_KEY);
  }
}
