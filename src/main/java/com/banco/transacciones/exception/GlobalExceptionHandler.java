package com.banco.transacciones.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.banco.transacciones.dto.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador global de excepciones para todas las respuestas de error.
 */

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(SaldoInsuficienteException.class)
	public ResponseEntity<ErrorResponse> handleSaldoInsuficiente(SaldoInsuficienteException ex,
			HttpServletRequest request) {
		// Retorna 400 Bad Request
		return buildResponse(HttpStatus.BAD_REQUEST, "Saldo insuficiente", ex.getMessage(), request);
	}

	@ExceptionHandler(CuentaNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleCuentaNotFoundException(CuentaNotFoundException ex,
			HttpServletRequest request) {
		log.warn("Cuenta no encontrada: {} en el path: {}", ex.getMessage(), request.getRequestURI());
		// Retorna 404 Not Found
		return buildResponse(HttpStatus.NOT_FOUND, "Cuenta no encontrada", ex.getMessage(), request);
	}

	@ExceptionHandler(CuentaBloqueadaException.class)
	public ResponseEntity<ErrorResponse> handleCuentaBloqueada(CuentaBloqueadaException ex,
			HttpServletRequest request) {
		// Retorna 403 Forbidden
		return buildResponse(HttpStatus.FORBIDDEN, "Cuenta bloqueada", ex.getMessage(), request);
	}

	@ExceptionHandler(TransaccionNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleTransaccionNotFoundException(TransaccionNotFoundException ex,
			HttpServletRequest request) {
		// Retorna 404 Not Found
		return buildResponse(HttpStatus.NOT_FOUND, "Transacción no encontrada", ex.getMessage(), request);
	}

	@ExceptionHandler(ConcurrencyFailureException.class)
	public ResponseEntity<ErrorResponse> handleConcurrencyFailureException(ConcurrencyFailureException ex,
			HttpServletRequest request) {
		// Retorna 409 Conflict
		return buildResponse(HttpStatus.CONFLICT, "Conflicto de concurrencia",
				"La cuenta está siendo procesada por otra transacción. Intente de nuevo.", request);
	}

	@ExceptionHandler(AlertaNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleAlertaNotFound(AlertaNotFoundException ex, HttpServletRequest request) {
		// Retorna 404 Not Found siguiendo el estándar de recursos no encontrados
		log.warn("Alerta no encontrada: {} en el path: {}", ex.getMessage(), request.getRequestURI());
		return buildResponse(HttpStatus.NOT_FOUND, "Alerta no encontrada", ex.getMessage(), request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
		// Nivel error para fallos críticos no controlados
		log.error("Error no controlado detectado: ", ex);
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
				"Ocurrió un error inesperado en el servidor", request);
	}

	/**
	 * Método auxiliar para construir la respuesta JSON estandarizada.
	 */
	private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String detalle,
			HttpServletRequest request) {
		ErrorResponse response = new ErrorResponse(Instant.now(), status.value(), error, detalle,
				request.getRequestURI());

		return new ResponseEntity<ErrorResponse>(response, status);
	}
}
