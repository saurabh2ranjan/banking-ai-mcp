package com.banking.gateway.audit;

import com.banking.events.EventMetadata;
import com.banking.events.audit.AuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Cross-cutting audit log aspect.
 * Logs all controller method calls with caller identity, timing, and outcome.
 * When Kafka is enabled, also publishes structured AuditEvents to the audit topic.
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    private static final String TOPIC_AUDIT_TRAIL = "banking.audit.trail";

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Around("execution(* com.banking.gateway.controller.*.*(..))")
    public Object auditController(ProceedingJoinPoint pjp) throws Throwable {
        String caller = getCaller();
        String method = pjp.getSignature().toShortString();
        long   start  = System.currentTimeMillis();

        log.info("AUDIT | {} | START | caller={} | method={}",
                 LocalDateTime.now(), caller, method);
        try {
            Object result = pjp.proceed();
            long elapsed  = System.currentTimeMillis() - start;
            log.info("AUDIT | {} | SUCCESS | caller={} | method={} | elapsed={}ms",
                     LocalDateTime.now(), caller, method, elapsed);
            publishAuditEvent(caller, method, "SUCCESS", elapsed, null);
            return result;
        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("AUDIT | {} | FAILURE | caller={} | method={} | elapsed={}ms | error={}",
                      LocalDateTime.now(), caller, method, elapsed, ex.getMessage());
            publishAuditEvent(caller, method, "FAILURE", elapsed, ex.getMessage());
            throw ex;
        }
    }

    private void publishAuditEvent(String caller, String method, String outcome,
                                   long elapsedMs, String errorMessage) {
        if (kafkaTemplate == null) return;
        try {
            var event = new AuditEvent(caller, method, outcome, elapsedMs, errorMessage,
                    EventMetadata.now("banking-ai-gateway", MDC.get("traceId")));
            kafkaTemplate.send(TOPIC_AUDIT_TRAIL, caller, event);
        } catch (Exception e) {
            log.warn("Failed to publish audit event to Kafka: {}", e.getMessage());
        }
    }

    private String getCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
