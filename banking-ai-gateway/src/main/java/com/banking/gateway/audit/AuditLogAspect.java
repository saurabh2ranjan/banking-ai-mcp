package com.banking.gateway.audit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Cross-cutting audit log aspect.
 * Logs all controller method calls with caller identity, timing, and outcome.
 * In production, write to an immutable audit store (e.g. AWS CloudTrail or a dedicated audit DB).
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

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
            return result;
        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("AUDIT | {} | FAILURE | caller={} | method={} | elapsed={}ms | error={}",
                      LocalDateTime.now(), caller, method, elapsed, ex.getMessage());
            throw ex;
        }
    }

    private String getCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
