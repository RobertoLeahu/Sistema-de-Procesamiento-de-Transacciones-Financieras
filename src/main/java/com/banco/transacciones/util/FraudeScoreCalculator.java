package com.banco.transacciones.util;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

	private static final double PESO_MONTO = 0.30;
	private static final double PESO_HORA = 0.20;
	private static final double PESO_FRECUENCIA = 0.25;
	private static final double PESO_ANTIGUEDAD = 0.15;
	private static final double PESO_PAIS = 0.10;

	private static final BigDecimal UMBRAL_MONTO = new BigDecimal("10000.00");

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

		// Monto > 10.000
		if (request.monto().compareTo(UMBRAL_MONTO) > 0) {
			score += PESO_MONTO;
			motivos.add("Monto elevado (>10.000)");
		}

		// Horario: entre 00:00 - 05:00
		int hora = ahora.atZone(ZoneId.systemDefault()).getHour();
		if (hora >= 0 && hora < 5) {
			score += PESO_HORA;
			motivos.add("Horario inusual (00:00-05:00)");
		}

		// Frecuencia: > 3 transacciones en los últimos 5 minutos.
		Instant cincuMinutosAtras = ahora.minus(5, ChronoUnit.MINUTES);
		long txRecientes = transaccionRepository.countByCuentaOrigenAndFechaHoraAfter(request.cuentaOrigen(),
				cincuMinutosAtras);

		if (txRecientes > 3) {
			score += PESO_FRECUENCIA;
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
					score += PESO_ANTIGUEDAD;
					motivos.add("Cuenta destino reciente (<7 días)");
				}
			}
		}

		// País fuera del patrón habitual del cliente
		if (esPaisInusual(request)) {
			score += PESO_PAIS;
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