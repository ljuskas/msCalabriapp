package com.clabriapp.mscalabriapp.application.usecases;

import com.clabriapp.mscalabriapp.application.ports.in.GetServiceInfoUseCase;
import com.clabriapp.mscalabriapp.application.ports.out.ServiceInfoProvider;
import com.clabriapp.mscalabriapp.domain.model.ServiceInfo;

import java.util.Objects;

/**
 * Implementación del caso de uso. Sin anotaciones ni dependencias de Quarkus.
 */
public class GetServiceInfoService implements GetServiceInfoUseCase {

    private final ServiceInfoProvider serviceInfoProvider;

    public GetServiceInfoService(ServiceInfoProvider serviceInfoProvider) {
        this.serviceInfoProvider = Objects.requireNonNull(
                serviceInfoProvider, "serviceInfoProvider no puede ser null");
    }

    @Override
    public ServiceInfo getServiceInfo() {
        return serviceInfoProvider.getServiceInfo();
    }
}
