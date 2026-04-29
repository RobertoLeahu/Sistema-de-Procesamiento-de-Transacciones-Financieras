package com.banco.transacciones.controller;

import java.util.Collections;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.banco.transacciones.exception.AlertaNotFoundException;
import com.banco.transacciones.exception.ConcurrencyFailureException;
import com.banco.transacciones.exception.CuentaBloqueadaException;
import com.banco.transacciones.exception.CuentaNotFoundException;
import com.banco.transacciones.exception.GeneralException;
import com.banco.transacciones.exception.SaldoInsuficienteException;
import com.banco.transacciones.exception.TransaccionNotFoundException;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Controller dummy temporal y exclusivo para testing de GlobalExceptionHandler.
 * Contiene endpoints para forzar CADA UNA de las excepciones manejadas.
 */
@RestController
@Validated
public class TestExceptionController {

	@GetMapping("/test/saldo-insuficiente")
	public void throwSaldo() {
		throw new SaldoInsuficienteException("La cuenta 0012-3456 no tiene saldo suficiente");
	}

	@GetMapping("/test/cuenta-bloqueada")
	public void throwCuenta() {
		throw new CuentaBloqueadaException("La cuenta se encuentra en estado BLOQUEADA");
	}

	@GetMapping("/test/not-found")
	public void throwNotFound() {
		throw new TransaccionNotFoundException("No se encontró la transacción con ID 99");
	}

	@GetMapping("/test/concurrency")
	public void throwConcurrency() {
		throw new ConcurrencyFailureException("Hay un conflicto de concurrencia");
	}

	@GetMapping("/test/cuenta-not-found")
	public void throwCuentaNotFound() {
		throw new CuentaNotFoundException("Cuenta no encontrada en el sistema");
	}

	@GetMapping("/test/alerta-not-found")
	public void throwAlertaNotFound() {
		throw new AlertaNotFoundException("Alerta de fraude no encontrada");
	}

	@GetMapping("/test/general-exception")
	public void throwGeneral() {
		throw new GeneralException("Error de negocio genérico");
	}

	@GetMapping("/test/exception")
	public void throwException() throws Exception {
		throw new Exception("Error interno no controlado");
	}

	@GetMapping("/test/constraint-violation")
	public void throwConstraint() {
		throw new ConstraintViolationException("Restricción de validación fallida", Collections.emptySet());
	}

	@PostMapping("/test/method-argument")
	public void throwMethodArgument(@Valid @RequestBody DummyDto dto) {
		// La validación @Valid lanzará MethodArgumentNotValidException si el DTO es
		// inválido
	}

	@GetMapping("/test/handler-method")
	public void throwHandlerMethod(@RequestParam @Size(min = 5) String param) {
		// En Spring Boot 3.x, fallar esta validación lanza
		// HandlerMethodValidationException
	}

	// DTO interno para pruebas de validación
	public record DummyDto(@NotBlank String name) {
	}
}