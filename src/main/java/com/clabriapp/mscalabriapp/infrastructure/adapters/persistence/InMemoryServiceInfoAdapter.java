package com.clabriapp.mscalabriapp.infrastructure.adapters.persistence;

import com.clabriapp.mscalabriapp.application.ports.out.ServiceInfoProvider;
import com.clabriapp.mscalabriapp.domain.model.ServiceInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Adaptador de persistencia en memoria para el bootstrap.
 *
 * <p>No hay base de datos todavía; la información se toma de configuración.
 * En una tarea posterior este adaptador podrá sustituirse sin tocar dominio ni
 * aplicación.</p>
 */
@ApplicationScoped
public class InMemoryServiceInfoAdapter implements ServiceInfoProvider {

    private final ServiceInfo serviceInfo;

    @Inject
    public InMemoryServiceInfoAdapter(
            @ConfigProperty(name = "clabriapp.service.name", defaultValue = "msCalabriapp") String name,
            @ConfigProperty(name = "clabriapp.service.display-name", defaultValue = "ClabriApp") String displayName,
            @ConfigProperty(name = "clabriapp.service.version", defaultValue = "0.1.0-SNAPSHOT") String version) {
        this.serviceInfo = new ServiceInfo(name, displayName, version);
    }

    @Override
    public ServiceInfo getServiceInfo() {
        return serviceInfo;
    }
}
