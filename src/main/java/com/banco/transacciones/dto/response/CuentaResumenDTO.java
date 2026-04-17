package com.banco.transacciones.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

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
