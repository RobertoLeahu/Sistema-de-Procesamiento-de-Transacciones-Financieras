package com.banco.transacciones.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Controlador de entrada para el procesamiento de movimientos financieros.
 * Gestiona el ciclo de vida de transferencias individuales y operaciones
 * masivas en lote. Implementa el patrón asíncrono para
 * operaciones de escritura, retornando estados iniciales de forma inmediata
 * para optimizar la experiencia del usuario.
 */
@RestController
@RequestMapping("/api/transacciones")
@RequiredArgsConstructor
@Validated
public class TransaccionController {

}
