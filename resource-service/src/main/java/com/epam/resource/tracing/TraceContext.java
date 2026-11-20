package com.epam.resource.tracing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.util.UUID;

@Component
public class TraceContext {

  public static final String HTTP_HEADER_TRACE_ID = "X-Trace-Id";
  public static final String AMQP_HEADER_TRACE_ID = "x-trace-id";
  public static final String MDC_TRACE_ID_KEY = "traceId";
  public static final String MDC_SERVICE_KEY = "service";
  private final String serviceName;

  public TraceContext(@Value("${spring.application.name}") String serviceName) {
    this.serviceName = serviceName;
  }

  public String initTraceId(String incomingId) {
    String traceId = incomingId == null || incomingId.isBlank()
        ? UUID.randomUUID().toString()
        : incomingId.trim();
    MDC.put(MDC_TRACE_ID_KEY, traceId);
    MDC.put(MDC_SERVICE_KEY, serviceName);
    return traceId;
  }

  public String getTraceId() {
    return MDC.get(MDC_TRACE_ID_KEY);
  }

  public void clear() {
    MDC.remove(MDC_TRACE_ID_KEY);
    MDC.remove(MDC_SERVICE_KEY);
  }
}
