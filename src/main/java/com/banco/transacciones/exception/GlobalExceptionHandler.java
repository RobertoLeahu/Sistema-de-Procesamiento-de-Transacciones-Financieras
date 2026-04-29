package com.banco.transacciones.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.banco.transacciones.dto.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador global de excepciones para todas las respuestas de error. Maneja
 * las excepciones lanzadas por la capa de servicio y las transforma en un JSON
 * estandarizado para los clientes de la API.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final String VALIDATION_ERROR = "Validation Error";
	private static final String ERROR_INTERNO = "Error interno";
	
	// ==========================================
	// EXCEPCIONES DE NEGOCIO Y DOMINIO
	// ==========================================

	@ExceptionHandler(CuentaNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleCuentaNotFoundException(CuentaNotFoundException ex,
			HttpServletRequest request) {
		log.warn("Cuenta no encontrada: {} en el path: {}", ex.getMessage(), request.getRequestURI());
		return buildResponse(HttpStatus.NOT_FOUND, "Cuenta no encontrada", ex.getMessage(), request.getRequestURI());
	}

	@ExceptionHandler(TransaccionNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleTransaccionNotFoundException(TransaccionNotFoundException ex,
			HttpServletRequest request) {
		log.warn("Transacción no encontrada: {} en el path: {}", ex.getMessage(), request.getRequestURI());
		return buildResponse(HttpStatus.NOT_FOUND, "Transacción no encontrada", ex.getMessage(),
				request.getRequestURI());
	}

	@ExceptionHandler(AlertaNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleAlertaNotFound(AlertaNotFoundException ex, HttpServletRequest request) {
		log.warn("Alerta no encontrada: {} en el path: {}", ex.getMessage(), request.getRequestURI());
		return buildResponse(HttpStatus.NOT_FOUND, "Alerta no encontrada", ex.getMessage(), request.getRequestURI());
	}

	@ExceptionHandler(SaldoInsuficienteException.class)
	public ResponseEntity<ErrorResponse> handleSaldoInsuficiente(SaldoInsuficienteException ex,
			HttpServletRequest request) {
		log.warn("Saldo insuficiente: {}", ex.getMessage());
		return buildResponse(HttpStatus.BAD_REQUEST, "Saldo insuficiente", ex.getMessage(), request.getRequestURI());
	}

	@ExceptionHandler(CuentaBloqueadaException.class)
	public ResponseEntity<ErrorResponse> handleCuentaBloqueada(CuentaBloqueadaException ex,
			HttpServletRequest request) {
		log.warn("Cuenta bloqueada: {}", ex.getMessage());
		// Ajustado a 403 FORBIDDEN para satisfacer los requisitos del test
		return buildResponse(HttpStatus.FORBIDDEN, "Cuenta bloqueada", ex.getMessage(), request.getRequestURI());
	}

	@ExceptionHandler(ConcurrencyFailureException.class)
	public ResponseEntity<ErrorResponse> handleConcurrencyException(ConcurrencyFailureException ex,
			HttpServletRequest request) {
		log.warn("Fallo de concurrencia detectado: {}", ex.getMessage());
		return buildResponse(HttpStatus.CONFLICT, "Conflicto de concurrencia",
				"La cuenta está siendo procesada por otra transacción. Intente de nuevo.", request.getRequestURI());
	}

	// ==========================================
	// EXCEPCIONES DE VALIDACIÓN
	// ==========================================

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		String errores = ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage)
				.collect(Collectors.joining(", "));
		log.warn("Error de validación de objeto: {}", errores);
		return buildResponse(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, errores, request.getRequestURI());
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		log.warn("Error de restricción de parámetros: {}", ex.getMessage());
		return buildResponse(HttpStatus.BAD_REQUEST, VALIDATION_ERROR,
				"El lote enviado no cumple con las validaciones permitidas.", request.getRequestURI());
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex,
			HttpServletRequest request) {
		log.warn("Error de validación de método (Spring 3.x): {}", ex.getMessage());
		return buildResponse(HttpStatus.BAD_REQUEST, VALIDATION_ERROR,
				"El lote no puede estar vacío y debe tener un máximo de 500 registros.", request.getRequestURI());
	}

	// ==========================================
	// EXCEPCIONES GENERALES (FALLBACK)
	// ==========================================

	@ExceptionHandler(GeneralException.class)
	public ResponseEntity<ErrorResponse> handleGeneralException(GeneralException ex, HttpServletRequest request) {
		log.error("Excepción controlada de la aplicación", ex);
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO,
				"Ocurrió un error inesperado en el servidor", request.getRequestURI());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnknownException(Exception ex, HttpServletRequest request) {
		log.error("Error no controlado detectado: ", ex);
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNO,
				"Ocurrió un error inesperado en el servidor", request.getRequestURI());
	}

	// ==========================================
	// METODO UTILITARIO
	// ==========================================

	private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String detalle, String path) {
		ErrorResponse response = new ErrorResponse(Instant.now(), status.value(), error, detalle, path);
		return ResponseEntity.status(status).body(response);
	}
}