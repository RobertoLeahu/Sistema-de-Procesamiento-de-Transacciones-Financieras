package com.banco.transacciones.dto.response;

import java.time.Instant;

/*
 * Representa la estructura estándar de respuesta para cualquier error del sistema.
 */

public record ErrorResponse(Instant timestamp, int status, String error, String detalle, String path) {
}
