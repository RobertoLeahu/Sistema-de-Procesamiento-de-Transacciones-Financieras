package com.banco.transacciones.util;

import java.util.Comparator;

import com.banco.transacciones.dto.response.CuentaResumenDTO;

/**
 * Ordena las cuentas por nivel de exposición al riesgo para el panel de
 * control.
 */
public class CuentaRiesgoComparator implements Comparator<CuentaResumenDTO> {

	@Override
	public int compare(CuentaResumenDTO c1, CuentaResumenDTO c2) {

		// Criterio 1: Score de fraude (descendente - mayor riesgo primero)
		double score1 = c1.puntuacionRiesgoAcumulada() != null ? c1.puntuacionRiesgoAcumulada() : 0.0;
		double score2 = c2.puntuacionRiesgoAcumulada() != null ? c2.puntuacionRiesgoAcumulada() : 0.0;
		int comparacionScore = Double.compare(score2, score1);

		if (comparacionScore != 0) {
			return comparacionScore;
		}

		// Criterio 2: Número de alertas CRITICAS (descendente - más alertas primero)
		int comparacionAlertas = Long.compare(c2.alertasCriticas(), c1.alertasCriticas());

		if (comparacionAlertas != 0) {
			return comparacionAlertas;
		}

		// Criterio 3: Antigüedad de la cuenta (ascendente - cuentas nuevas primero)
		// Cuentas nuevas = Fecha de alta más reciente (mayor). Por tanto, ordenamos
		// descendentemente por fecha.
		if (c1.fechaAlta() != null && c2.fechaAlta() != null) {
			return c2.fechaAlta().compareTo(c1.fechaAlta());
		}

		return 0;
	}
}
