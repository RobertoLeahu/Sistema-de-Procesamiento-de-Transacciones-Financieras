package com.banco.transacciones.util;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.banco.transacciones.domain.models.Cuenta;
import com.banco.transacciones.dto.request.TransferenciaDTO;
import com.banco.transacciones.dto.response.ResultadoFraude;
import com.banco.transacciones.repository.CuentaRepository;
import com.banco.transacciones.repository.TransaccionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FraudeScoreCalculator {

	@Value("${fraude.reglas.peso.monto:0.30}")
	private double pesoMonto;

	@Value("${fraude.reglas.peso.hora:0.20}")
	private double pesoHora;

	@Value("${fraude.reglas.peso.frecuencia:0.25}")
	private double pesoFrecuencia;

	@Value("${fraude.reglas.peso.antiguedad:0.15}")
	private double pesoAntiguedad;

	@Value("${fraude.reglas.peso.pais:0.10}")
	private double pesoPais;

	@Value("${fraude.reglas.umbral.monto:10000.00}")
	private BigDecimal umbralMonto;

	private final TransaccionRepository transaccionRepository;
	private final CuentaRepository cuentaRepository;
	private final Clock clock;

	/**
	 * Calcula el score de fraude asíncronamente y recopila los motivos.
	 * Complejidad: O(1) cálculos base, O(n) consulta a BD.
	 */
	public ResultadoFraude calcularScore(TransferenciaDTO request) {

		double score = 0.0;
		List<String> motivos = new ArrayList<>();
		Instant ahora = clock.instant();

		if (request.monto().compareTo(umbralMonto) > 0) {
			score += pesoMonto;
			motivos.add("Monto elevado (>" + umbralMonto + ")");
		}

		// Horario: entre 00:00 - 05:00
		int hora = ahora.atZone(ZoneId.systemDefault()).getHour();
		if (hora >= 0 && hora < 5) {
			score += pesoHora;
			motivos.add("Horario inusual (00:00-05:00)");
		}

		// Frecuencia: > 3 transacciones en los últimos 5 minutos.
		Instant cincoMinutosAtras = ahora.minus(5, ChronoUnit.MINUTES);
		long txRecientes = transaccionRepository.countByCuentaOrigenAndFechaHoraAfter(request.cuentaOrigen(),
				cincoMinutosAtras);

		if (txRecientes > 3) {
			score += pesoFrecuencia;
			motivos.add("Alta frecuencia (>3 transacciones en 5 min)");
		}

		// Cuenta Nueva: cuenta destino creada hace < 7 días
		Optional<Cuenta> cuentaOpt = cuentaRepository.findByNumeroCuenta(request.cuentaDestino());

		if (cuentaOpt.isPresent()) {
			Cuenta cuenta = cuentaOpt.get();
			if (cuenta.getCliente() != null && cuenta.getCliente().getFechaAlta() != null) {
				long diasActiva = ChronoUnit.DAYS.between(
						cuenta.getCliente().getFechaAlta().atStartOfDay(ZoneId.systemDefault()).toInstant(), ahora);
				if (diasActiva < 7) {
					score += pesoAntiguedad;
					motivos.add("Cuenta destino reciente (<7 días)");
				}
			}
		}

		// País fuera del patrón habitual del cliente
		if (esPaisInusual(request)) {
			score += pesoPais;
			motivos.add("País destino inusual (" + request.codigoPais() + ")");
		}

		// Garantiza límite entre 0.0 y 1.0
		return new ResultadoFraude(Math.min(score, 1.0), motivos);
	}

	private boolean esPaisInusual(TransferenciaDTO request) {
		String paisHabitual = transaccionRepository.findPaisHabitual(request.cuentaOrigen())
				.orElseGet(() -> cuentaRepository.findByNumeroCuenta(request.cuentaOrigen())
						.map(c -> c.getCliente().getPaisResidencia()).orElse("XX"));

		return !request.codigoPais().equalsIgnoreCase(paisHabitual);
	}
}