package com.banco.transacciones.dto.request;

import java.math.BigDecimal;

import com.banco.transacciones.dto.validation.CuentasDistintas;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Record inmutable que transporta los datos de entrada para una transferencia.
 */
@CuentasDistintas
public record TransferenciaDTO(

		@NotBlank(message = "La cuenta de origen es obligatoria")
		@Pattern(regexp = "^[A-Z]{2}\\d{2}[A-Z0-9]{10,30}$", message = "Formato IBAN inválido")
		String cuentaOrigen,

		@NotBlank(message = "La cuenta de destino es obligatoria")
		@Pattern(regexp = "^[A-Z]{2}\\d{2}[A-Z0-9]{10,30}$", message = "Formato IBAN inválido")
		String cuentaDestino,

		@NotNull(message = "El monto es obligatorio")
		@Positive(message = "El monto debe ser mayor a cero")
		@DecimalMax(value = "50000.00", message = "El monto máximo por transacción es 50000.00")
		BigDecimal monto,

		@Size(max = 255, message = "La descripción es demasiado larga")
		String descripcion) {
}
