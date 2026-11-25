package com.clabriapp.mscalabriapp.domain;

import com.clabriapp.mscalabriapp.domain.model.ServiceInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceInfoTest {

    @Test
    void creaUnaInstanciaValida() {
        ServiceInfo info = new ServiceInfo("msCalabriapp", "ClabriApp", "0.1.0-SNAPSHOT");

        assertEquals("msCalabriapp", info.name());
        assertEquals("ClabriApp", info.displayName());
        assertEquals("0.1.0-SNAPSHOT", info.version());
    }

    @Test
    void normalizaEspaciosEnLosTextos() {
        ServiceInfo info = new ServiceInfo("  msCalabriapp  ", " ClabriApp ", " 0.1.0 ");

        assertEquals("msCalabriapp", info.name());
        assertEquals("ClabriApp", info.displayName());
        assertEquals("0.1.0", info.version());
    }

    @Test
    void rechazaNombreNulo() {
        assertThrows(NullPointerException.class,
                () -> new ServiceInfo(null, "ClabriApp", "0.1.0"));
    }

    @Test
    void rechazaVersionEnBlanco() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceInfo("msCalabriapp", "ClabriApp", "   "));
    }
}
