package com.clabriapp.mscalabriapp.domain.model;

import java.util.Objects;

/**
 * Información técnica e inmutable del servicio.
 *
 * <p>Este modelo pertenece al dominio y no debe depender de frameworks ni de
 * infraestructura.</p>
 */
public record ServiceInfo(String name, String displayName, String version) {

    public ServiceInfo {
        name = requireText(name, "name");
        displayName = requireText(displayName, "displayName");
        version = requireText(version, "version");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " no puede ser null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " no puede estar vacío");
        }
        return normalized;
    }
}
