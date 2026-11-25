package com.clabriapp.mscalabriapp.application;

import com.clabriapp.mscalabriapp.application.ports.in.GetServiceInfoUseCase;
import com.clabriapp.mscalabriapp.application.ports.out.ServiceInfoProvider;
import com.clabriapp.mscalabriapp.application.usecases.GetServiceInfoService;
import com.clabriapp.mscalabriapp.domain.model.ServiceInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetServiceInfoServiceTest {

    @Test
    void delegaEnElPuertoDeSalida() {
        ServiceInfo esperado = new ServiceInfo("msCalabriapp", "ClabriApp", "0.1.0-SNAPSHOT");
        ServiceInfoProvider provider = () -> esperado;

        GetServiceInfoUseCase useCase = new GetServiceInfoService(provider);

        assertSame(esperado, useCase.getServiceInfo());
    }

    @Test
    void rechazaProviderNulo() {
        assertThrows(NullPointerException.class, () -> new GetServiceInfoService(null));
    }
}
