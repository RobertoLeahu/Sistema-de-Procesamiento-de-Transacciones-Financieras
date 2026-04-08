package com.banco.transacciones.domain.enums;

/*
 * Representa los  posibles estados de una transacción.
 */

public enum EstadoTransaccion {
	PENDIENTE,
	PROCESANDO,
	COMPLETADA,
	RECHAZADA,
	REVERTIDA
}
