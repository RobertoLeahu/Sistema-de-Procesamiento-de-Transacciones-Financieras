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
    public ResponseEntity<ErrorResponse> handleSaldoInsuficiente(SaldoInsuficienteException ex, HttpServletRequest request) {
        // Retorna 400 Bad Request
        return buildResponse(HttpStatus.BAD_REQUEST, "Saldo insuficiente", ex.getMessage(), request);
    }
	
	/**
     * Método auxiliar para construir la respuesta JSON estandarizada.
     */
	private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String detalle, HttpServletRequest request){
		ErrorResponse response = new ErrorResponse(
				Instant.now(),
				status.value(),
				error,
				detalle,
				request.getRequestURI()
				);
		
		return new ResponseEntity<ErrorResponse>(response, status);
	}
}
