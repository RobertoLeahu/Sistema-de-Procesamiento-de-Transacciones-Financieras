package com.banco.transacciones.exception;

/**
 * Excepción lanzada cuando se encuentra un error de conflictos o concurrencia.
 */

public class ConcurrencyFailureException extends RuntimeException {

	public ConcurrencyFailureException(String mensaje) {
		super(mensaje);
	}

}
