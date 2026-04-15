package com.banco.transacciones.dto.validation;

import com.banco.transacciones.dto.request.TransferenciaDTO;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Lógica de la anotación personalizada @CuentasDistintas
 */
public class CuentasDistintasValidator implements ConstraintValidator<CuentasDistintas, TransferenciaDTO> {
	@Override
	public boolean isValid(TransferenciaDTO request, ConstraintValidatorContext context) {
		if (request == null || request.cuentaOrigen() == null || request.cuentaDestino() == null) {
			return true;
		}
		return !request.cuentaOrigen().equalsIgnoreCase(request.cuentaDestino());
	}
}
