package com.banco.transacciones.dto.response;

/**
 * Agrega los resultados del procesamiento asíncrono de un lote de transacciones.
 */
public record ResumenLoteDTO(
    int totalRecibidas,
    int totalExitosas,
    int totalFallidas
) {
    // Método utilitario para sumar resultados entre diferentes hilos
    public static ResumenLoteDTO sumar(ResumenLoteDTO a, ResumenLoteDTO b) {
        return new ResumenLoteDTO(
            a.totalRecibidas() + b.totalRecibidas(),
            a.totalExitosas() + b.totalExitosas(),
            a.totalFallidas() + b.totalFallidas()
        );
    }
}