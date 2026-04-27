package com.banco.transacciones.exception;

/**
 * Excepción lanzada cuando no se encuentra una cuenta bancaria en el sistema.
 */
public class CuentaNotFoundException extends RuntimeException {

	public CuentaNotFoundException(String mensaje) {
		super(mensaje);
	}
}