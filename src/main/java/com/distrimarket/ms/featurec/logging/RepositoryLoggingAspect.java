package com.distrimarket.ms.featurec.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RepositoryLoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(RepositoryLoggingAspect.class);

    @Around("execution(* com.distrimarket.ms.featurec.repository..*(..))")
    public Object logRepositoryCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        String operation = joinPoint.getSignature().toShortString();
        log.debug("Ejecutando consulta de repositorio: {}", operation);
        try {
            Object result = joinPoint.proceed();
            log.debug("Consulta de repositorio completada: {} duración={}ms",
                    operation, (System.nanoTime() - startedAt) / 1_000_000);
            return result;
        } catch (Throwable exception) {
            log.error("Falló la operación de repositorio: {} duración={}ms",
                    operation, (System.nanoTime() - startedAt) / 1_000_000, exception);
            throw exception;
        }
    }
}
