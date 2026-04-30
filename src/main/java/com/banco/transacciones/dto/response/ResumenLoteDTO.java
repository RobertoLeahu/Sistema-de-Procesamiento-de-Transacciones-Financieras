package com.banco.transacciones.dto.response;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrega los resultados del procesamiento asíncrono de un lote de transacciones.
 */
public record ResumenLoteDTO(
    int totalRecibidas,
    int totalExitosas,
    int totalFallidas,
    List<DetalleRechazoDTO> detallesRechazo
) {
    // Método utilitario para sumar resultados entre diferentes hilos
    public static ResumenLoteDTO sumar(ResumenLoteDTO a, ResumenLoteDTO b) {
        List<DetalleRechazoDTO> detallesCombinados = new ArrayList<>();
        
        if (a.detallesRechazo() != null) {
            detallesCombinados.addAll(a.detallesRechazo());
        }
        if (b.detallesRechazo() != null) {
            detallesCombinados.addAll(b.detallesRechazo());
        }

        return new ResumenLoteDTO(
            a.totalRecibidas() + b.totalRecibidas(),
            a.totalExitosas() + b.totalExitosas(),
            a.totalFallidas() + b.totalFallidas(),
            detallesCombinados
        );
    }
}