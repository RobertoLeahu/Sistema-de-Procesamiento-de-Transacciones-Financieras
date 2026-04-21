package com.banco.transacciones.dto.response;

import java.util.List;

/**
 * Record inmutable para representar el reporte de ciclos.
 */
public record CicloReporte(boolean existeCiclo, List<String> cuentasInvolucradas) {
}
