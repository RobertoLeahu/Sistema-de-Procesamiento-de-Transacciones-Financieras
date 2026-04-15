package com.banco.transacciones.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record TransaccionDTO(
		Long id,
		String cuentaOrigen,
		String cuentaDestino,
		BigDecimal monto,
		String tipo,
		String estado,
		Instant hora,
		String descripcion
		) {}
