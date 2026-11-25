package com.clabriapp.mscalabriapp.application.ports.out;

import com.clabriapp.mscalabriapp.domain.model.ServiceInfo;

/**
 * Puerto de salida: fuente de la información del servicio.
 *
 * <p>La implementación concreta vive en infraestructura.</p>
 */
public interface ServiceInfoProvider {

    ServiceInfo getServiceInfo();
}
