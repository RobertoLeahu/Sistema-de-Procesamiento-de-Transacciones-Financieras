package com.banco.transacciones.exception;

/**
 * Excepción lanzada cuando el saldo de una cuenta es inferior al monto
 * solicitado.
 */

public class SaldoInsuficienteException extends RuntimeException {
	public SaldoInsuficienteException(String mensaje) {
		super(mensaje);
	}
}