package com.banco.transacciones.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Controlador para la consulta de información y métricas de cuentas bancarias.
 * Expone servicios de agregación de datos para generar resúmenes estadísticos,
 * incluyendo desviaciones estándar de movimientos y puntuaciones de riesgo acumuladas.
 */
@RestController
@RequestMapping("/api/cuentas")
@RequiredArgsConstructor
public class CuentaController {
}