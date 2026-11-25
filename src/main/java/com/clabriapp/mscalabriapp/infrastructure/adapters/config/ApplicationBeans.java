package com.clabriapp.mscalabriapp.infrastructure.adapters.config;

import com.clabriapp.mscalabriapp.application.ports.in.GetServiceInfoUseCase;
import com.clabriapp.mscalabriapp.application.ports.out.ServiceInfoProvider;
import com.clabriapp.mscalabriapp.application.usecases.GetServiceInfoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Composition root: la infraestructura construye los casos de uso y los expone
 * como beans CDI. Así {@code application} y {@code domain} permanecen libres de
 * anotaciones y dependencias de framework.
 */
@ApplicationScoped
public class ApplicationBeans {

    @Produces
    @ApplicationScoped
    public GetServiceInfoUseCase getServiceInfoUseCase(ServiceInfoProvider serviceInfoProvider) {
        return new GetServiceInfoService(serviceInfoProvider);
    }
}
