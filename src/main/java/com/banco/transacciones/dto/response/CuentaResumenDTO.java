package com.banco.transacciones.dto.response;

import java.math.BigDecimal;

public record CuentaResumenDTO(
		String numeroCuenta,
	    BigDecimal saldoActual,
	    long totalMovimientos,
	    BigDecimal montoPromedio,
	    Double desviacionEstandar,
	    Double puntuacionRiesgoAcumulada
		) {}
