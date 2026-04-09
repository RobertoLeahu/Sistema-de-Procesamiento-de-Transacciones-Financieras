package com.banco.transacciones.exception;

/**
 * Excepción lanzada cuando se intenta realizar una operación sobre una cuenta
 * bloqueada o cerrada.
 */

public class CuentaBloqueadaException extends RuntimeException {
	public CuentaBloqueadaException(String mensaje) {
		super(mensaje);
	}
}
