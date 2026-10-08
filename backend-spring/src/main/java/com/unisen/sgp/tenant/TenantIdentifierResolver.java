package com.unisen.sgp.tenant;

import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Indica a Hibernate el tenant de cada sesión, leído de {@link TenantContextHolder} al
 * abrirla (al empezar cada transacción).
 *
 * <p>Sin empresa en el contexto devuelve {@link #GLOBAL}, el tenant "raíz": Hibernate no
 * filtra por {@code empresa_id}. Así funcionan el login, el registro y el modo global del
 * SUPER_ADMIN. Para GERENTE y USUARIO, {@link TenantFilter} garantiza que siempre hay empresa.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    /** Tenant raíz: ve todas las empresas. Ninguna empresa real tiene id 0. */
    public static final Long GLOBAL = 0L;

    @Override
    public Long resolveCurrentTenantIdentifier() {
        return TenantContextHolder.obtener().orElse(GLOBAL);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public boolean isRoot(Long tenantId) {
        return GLOBAL.equals(tenantId);
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
