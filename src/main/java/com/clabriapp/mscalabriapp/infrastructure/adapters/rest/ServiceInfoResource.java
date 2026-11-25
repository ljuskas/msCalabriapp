package com.clabriapp.mscalabriapp.infrastructure.adapters.rest;

import com.clabriapp.mscalabriapp.application.ports.in.GetServiceInfoUseCase;
import com.clabriapp.mscalabriapp.domain.model.ServiceInfo;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Adaptador REST de entrada.
 *
 * <p>El endpoint {@code /health} lo aporta la extensión SmallRye Health, no este
 * recurso. Aquí solo se expone información técnica mínima del servicio para
 * validar el cableado hexagonal.</p>
 */
@Path("/api/v1/service-info")
@Produces(MediaType.APPLICATION_JSON)
public class ServiceInfoResource {

    private final GetServiceInfoUseCase getServiceInfoUseCase;

    @Inject
    public ServiceInfoResource(GetServiceInfoUseCase getServiceInfoUseCase) {
        this.getServiceInfoUseCase = getServiceInfoUseCase;
    }

    @GET
    public ServiceInfoResponse getServiceInfo() {
        ServiceInfo serviceInfo = getServiceInfoUseCase.getServiceInfo();
        return new ServiceInfoResponse(
                serviceInfo.name(),
                serviceInfo.displayName(),
                serviceInfo.version());
    }

    public record ServiceInfoResponse(String name, String displayName, String version) {
    }
}
