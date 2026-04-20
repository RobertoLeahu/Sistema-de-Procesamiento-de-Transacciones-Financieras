package com.banco.transacciones.exception;

/**
 * Excepción lanzada cuando una alerta de fraude no es encontrada en el sistema.
 * Se mapea a un error 404 Not Found en el GlobalExceptionHandler.
 */
public class AlertaNotFoundException extends RuntimeException {
	public AlertaNotFoundException(String mensaje) {
		super(mensaje);
	}
}
