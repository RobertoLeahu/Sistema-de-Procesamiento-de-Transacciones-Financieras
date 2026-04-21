package com.banco.transacciones.dto.response;

import java.time.Instant;
import com.banco.transacciones.domain.enums.NivelRiesgo;

/**
 * Record inmutable para representar una alerta de fraude.
 */
public record AlertaFraudeDTO(
    Long id,
    Long transaccionId,
    NivelRiesgo nivel,
    String motivo,
    boolean revisada,
    Instant fechaCreacion
) {}