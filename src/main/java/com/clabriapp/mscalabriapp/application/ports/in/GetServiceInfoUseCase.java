package com.clabriapp.mscalabriapp.application.ports.in;

import com.clabriapp.mscalabriapp.domain.model.ServiceInfo;

/**
 * Puerto de entrada: caso de uso para consultar la información del servicio.
 */
public interface GetServiceInfoUseCase {

    ServiceInfo getServiceInfo();
}
