package com.banco.transacciones.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.banco.transacciones.dto.response.AlertaFraudeDTO;
import com.banco.transacciones.service.impl.FraudeServiceImpl;

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

	private final FraudeServiceImpl fraudeService;

	/**
	 * Retorna todas las alertas no revisadas con paginación. Spring mapeará
	 * automáticamente los query params (ej. ?page=0&size=20&sort=fecha,desc).
	 */
	@GetMapping("/alertas")
	public ResponseEntity<Page<AlertaFraudeDTO>> listarAlertasPendientes(
			@PageableDefault(size = 20) Pageable pageable) {
		Page<AlertaFraudeDTO> alertas = fraudeService.obtenerAlertasNoRevisadas(pageable);
		return ResponseEntity.ok(alertas);
	}

	/**
	 * Marca una alerta como revisada. Retorna 204 No Content porque la acción fue
	 * exitosa y no requiere cuerpo en la respuesta.
	 */
	@PutMapping("/alertas/{id}/revisar")
	public ResponseEntity<Void> revisarAlerta(@PathVariable Long id) {
		fraudeService.revisarAlerta(id);
		return ResponseEntity.noContent().build();
	}
}
