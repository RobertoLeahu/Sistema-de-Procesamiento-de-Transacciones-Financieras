package com.banco.transacciones.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.banco.transacciones.dto.response.CuentaResumenDTO;
import com.banco.transacciones.service.impl.CuentaServiceImpl;

import lombok.RequiredArgsConstructor;

/**
 * Controlador para la consulta de información y métricas de cuentas bancarias.
 * Expone servicios de agregación de datos para generar resúmenes estadísticos,
 * incluyendo desviaciones estándar de movimientos y puntuaciones de riesgo
 * acumuladas.
 */
@RestController
@RequestMapping("/api/cuentas")
@RequiredArgsConstructor
public class CuentaController {

	private final CuentaServiceImpl cuentaService;

	/**
	 * Retorna estadísticas detalladas de la cuenta.
	 */
	@GetMapping("/{numeroCuenta}/resumen")
	public ResponseEntity<CuentaResumenDTO> obtenerResumenCuenta(@PathVariable("numeroCuenta") String numeroCuenta) {
		CuentaResumenDTO resumen = cuentaService.obtenerResumen(numeroCuenta);
		return ResponseEntity.ok(resumen);
	}
}