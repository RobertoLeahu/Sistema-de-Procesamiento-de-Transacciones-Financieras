package com.banco.transacciones.util;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.banco.transacciones.domain.models.Cuenta;
import com.banco.transacciones.dto.request.TransferenciaDTO;
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
	 * Calcula el score de fraude asíncronamente. Complejidad: O(1) cálculos base,
	 * O(n) consulta a BD.
	 */
	public double calcularScore(TransferenciaDTO request) {

		double score = 0.0;
		Instant ahora = clock.instant();

		// Monto > 10.000
		if (request.monto().compareTo(UMBRAL_MONTO) > 0) {
			score += PESO_MONTO;
		}

		// Horario: entre 00:00 - 05:00
		int hora = ahora.atZone(ZoneId.systemDefault()).getHour();
		if (hora >= 0 && hora < 5) {
			score += PESO_HORA;
		}

		// Frecuencia: > 3 transacciones en los últimos 5 minuto.
		Instant cincuMinutosAtras = ahora.minus(5, ChronoUnit.MINUTES);
		long txRecientes = transaccionRepository.countByCuentaOrigenAndFechaHoraAfter(request.cuentaOrigen(),
				cincuMinutosAtras);

		if (txRecientes > 3) {
			score += PESO_FRECUENCIA;
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
				}
			}
		}

		// País fuera del patrón habitual del cliente
	    if (esPaisInusual(request)) {
	        score += PESO_PAIS;
	    }

		// Garantiza límite entre 0.0 y 1.0
		return Math.min(score, 1.0);
	}
	
	private boolean esPaisInusual(TransferenciaDTO request) {
	    String paisHabitual = transaccionRepository.findPaisHabitual(request.cuentaOrigen())
	            .orElseGet(() -> {
	                return cuentaRepository.findByNumeroCuenta(request.cuentaOrigen())
	                        .map(c -> c.getCliente().getPaisResidencia())
	                        .orElse("XX");
	            });

	    return !request.codigoPais().equalsIgnoreCase(paisHabitual);
	}
}
