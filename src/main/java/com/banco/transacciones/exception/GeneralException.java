package com.banco.transacciones.exception;

/**
 * Excepción lanzada para fallos generales/críticos no controlados.
 */

public class GeneralException extends RuntimeException {

	public GeneralException(String mensaje) {
		super(mensaje);
	}

}
