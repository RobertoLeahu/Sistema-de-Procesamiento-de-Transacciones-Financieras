package com.banco.transacciones.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Controlador especializado en la gestión y auditoría de alertas de fraude.
 * Proporciona mecanismos para la revisión de transacciones sospechosas
 * detectadas por el motor analítico del sistema.
 */
@RestController
@RequestMapping("/api/fraude")
@RequiredArgsConstructor
public class FraudeController {
}
