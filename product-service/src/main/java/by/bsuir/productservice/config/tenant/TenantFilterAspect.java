package by.bsuir.productservice.config.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("execution(* by.bsuir.productservice.service..*(..)) "
            + "&& @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object enableOrgFilter(ProceedingJoinPoint pjp) throws Throwable {
        UUID orgId = TenantContext.get();
        if (orgId != null) {
            try {
                Session session = entityManager.unwrap(Session.class);
                if (session.getEnabledFilter("orgFilter") == null) {
                    session.enableFilter("orgFilter").setParameter("orgId", orgId);
                }
            } catch (Exception e) {
                log.warn("Не удалось включить orgFilter: {}", e.getMessage());
            }

        }
        return pjp.proceed();
    }
}
