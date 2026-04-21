package com.banco.transacciones.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.banco.transacciones.dto.request.TransferenciaDTO;
import com.banco.transacciones.dto.response.ResumenLoteDTO;
import com.banco.transacciones.dto.response.TransaccionDTO;
import com.banco.transacciones.service.impl.TransaccionServiceImpl;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Controlador de entrada para el procesamiento de movimientos financieros.
 * Gestiona el ciclo de vida de transferencias individuales y operaciones
 * masivas en lote. Implementa el patrón asíncrono para operaciones de
 * escritura, retornando estados iniciales de forma inmediata para optimizar la
 * experiencia del usuario.
 */
@RestController
@RequestMapping("/api/transacciones")
@RequiredArgsConstructor
@Validated
public class TransaccionController {

	private final TransaccionServiceImpl transaccionService;

	/**
	 * Procesa una transferencia individual. Retorna 202 Accepted indicando que el
	 * procesamiento es asíncrono.
	 */
	@PostMapping("/transferencia")
	public ResponseEntity<TransaccionDTO> iniciarTransferencia(@Valid @RequestBody TransferenciaDTO request) {
		TransaccionDTO response = transaccionService.iniciarTransferencia(request);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
	}

	/**
	 * Consulta el estado actual de una transacción en proceso. Retorna 200 OK.
	 */
	@GetMapping("/{id}/estado")
	public ResponseEntity<TransaccionDTO> obtenerEstado(@PathVariable Long id) {
		TransaccionDTO response = transaccionService.obtenerEstadoTransaccion(id);
		return ResponseEntity.ok(response);
	}

	/**
	 * Procesa un lote de transacciones en paralelo. Retorna 202 Accepted. La
	 * validación restringe envíos vacíos.
	 */
	@PostMapping("/lote")
	public ResponseEntity<ResumenLoteDTO> procesarLote(
			@Valid @NotEmpty @Size(max = 500) @RequestBody List<TransferenciaDTO> lote) {
		ResumenLoteDTO resumen = transaccionService.procesarLote(lote);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(resumen);
	}
}
