package com.banco.transacciones.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Record inmutable para representar el resumen de una cuenta.
 */
public record CuentaResumenDTO(
	    String numeroCuenta,
	    BigDecimal saldoActual,
	    long totalMovimientos,
	    BigDecimal montoPromedio,
	    Double desviacionEstandar,
	    Double puntuacionRiesgoAcumulada,
	    long alertasCriticas,
	    LocalDate fechaAlta
	) {}
