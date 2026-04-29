package com.banco.transacciones.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.banco.transacciones.domain.models.Cuenta;
import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.response.CuentaResumenDTO;
import com.banco.transacciones.exception.CuentaNotFoundException;
import com.banco.transacciones.repository.CuentaRepository;
import com.banco.transacciones.repository.TransaccionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementación de consulta y generación de métricas sobre las cuentas
 * bancarias, es responsable de calcular indicadores estadísticos como la
 * desviación estándar de movimientos y la puntuación de riesgo acumulada, datos
 * fundamentales para el Ranking de Cuentas por Riesgo solicitado en los
 * algoritmos del sistema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CuentaServiceImpl {

	private final CuentaRepository cuentaRepository;
	private final TransaccionRepository transaccionRepository;

	/**
	 * Genera un resumen estadístico detallado de una cuenta bancaria. Realiza un
	 * análisis exhaustivo del historial de transacciones para obtener métricas de
	 * comportamiento financiero y niveles de riesgo acumulados. Utiliza
	 * {@code @Transactional(readOnly = true)} para optimizar las consultas y el
	 * rendimiento en la base de datos.
	 *
	 * @param id El identificador único de la cuenta.
	 * @return Un objeto {@link CuentaResumenDTO} con las estadísticas calculadas.
	 * @throws CuentaNotFoundException Si no existe una cuenta con el ID
	 *                                 proporcionado.
	 */
	@Transactional(readOnly = true)
	public CuentaResumenDTO obtenerResumen(String numeroCuenta) {
		Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
				.orElseThrow(() -> new CuentaNotFoundException("Cuenta no encontrada, ID:" + numeroCuenta));

		List<Transaccion> historial = transaccionRepository.findByCuentaOrigenOrCuentaDestino(numeroCuenta,
				numeroCuenta);

		long totalMovimientos = historial.size();
		BigDecimal sumaMontos = BigDecimal.ZERO;
		double puntuacionRiesgoAcumulada = 0.0;
		long alertasCriticas = 0;

		for (Transaccion tx : historial) {
			sumaMontos = sumaMontos.add(tx.getMonto());
			if (tx.getRiesgoFraude() != null) {
				puntuacionRiesgoAcumulada += tx.getRiesgoFraude();
			}
		}

		BigDecimal montoPromedio = BigDecimal.ZERO;
		double desviacionEstandar = 0.0;

		if (totalMovimientos > 0) {
			montoPromedio = sumaMontos.divide(BigDecimal.valueOf(totalMovimientos), 2, RoundingMode.HALF_UP);

			// Cálculo de Varianza y Desviación Estándar
			double sumatoriaDiferenciasCuadrado = 0.0;
			double promedioDouble = montoPromedio.doubleValue();
			for (Transaccion tx : historial) {
				sumatoriaDiferenciasCuadrado += Math.pow(tx.getMonto().doubleValue() - promedioDouble, 2);
			}
			double varianza = sumatoriaDiferenciasCuadrado / totalMovimientos;
			desviacionEstandar = Math.sqrt(varianza);
		}

		return new CuentaResumenDTO(cuenta.getNumeroCuenta(), cuenta.getSaldo(), totalMovimientos, montoPromedio,
				desviacionEstandar, puntuacionRiesgoAcumulada, alertasCriticas,
				cuenta.getCliente() != null ? cuenta.getCliente().getFechaAlta() : null);
	}
}