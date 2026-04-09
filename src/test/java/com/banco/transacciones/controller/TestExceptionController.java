package com.banco.transacciones.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.banco.transacciones.exception.ConcurrencyFailureException;
import com.banco.transacciones.exception.CuentaBloqueadaException;
import com.banco.transacciones.exception.SaldoInsuficienteException;
import com.banco.transacciones.exception.TransaccionNotFoundException;

/**
 * Controller dummy temporal y exclusivo para testing de GlobalExceptionHandler.
 */
@RestController
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
}