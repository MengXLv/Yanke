package com.yangke.forum.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

/**
 * 读写分离 AOP 切面：检测 @Transactional(readOnly = true) → 路由到从库
 *
 * 面试要点：
 * - ThreadLocal 数据源上下文，线程隔离
 * - @Order(HIGHEST_PRECEDENCE) 确保在事务拦截器之前执行，连接获取时已选好数据源
 * - 方法级注解优先于类级注解
 */
@Aspect
@Component
@ConditionalOnProperty(name = "forum.read-write-split", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class DataSourceAspect {

    @Around("@within(org.springframework.transaction.annotation.Transactional) " +
            "|| @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Transactional tx = method.getAnnotation(Transactional.class);
        if (tx == null) {
            tx = pjp.getTarget().getClass().getAnnotation(Transactional.class);
        }
        if (tx != null && tx.readOnly()) {
            DataSourceContext.setSlave();
        }
        try {
            return pjp.proceed();
        } finally {
            DataSourceContext.clear();
        }
    }
}
