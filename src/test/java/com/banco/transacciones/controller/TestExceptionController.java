package com.banco.transacciones.controller;

import com.banco.transacciones.exception.SaldoInsuficienteException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller dummy temporal y exclusivo para testing de GlobalExceptionHandler.
 */
@RestController
public class TestExceptionController {

    @GetMapping("/test/saldo-insuficiente")
    public void throwSaldo() {
        throw new SaldoInsuficienteException("La cuenta 0012-3456 no tiene saldo suficiente");
    }
}