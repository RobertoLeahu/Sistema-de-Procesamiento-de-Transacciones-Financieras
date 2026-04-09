package com.banco.transacciones.exception;

/**
 * Excepción lanzada cuando no se encuentra una transacción con el ID
 * proporcionado.
 */

public class TransaccionNotFoundException extends RuntimeException {

	public TransaccionNotFoundException(String mensaje) {
		super(mensaje);
	}

}
