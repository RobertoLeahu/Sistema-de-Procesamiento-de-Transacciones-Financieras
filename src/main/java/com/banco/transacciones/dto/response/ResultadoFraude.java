package com.banco.transacciones.dto.response;

import java.util.List;

/**
 * Record inmutable para transportar el resultado del análisis junto con sus
 * razones.
 */
public record ResultadoFraude(
		double score,
		List<String> motivos){}
