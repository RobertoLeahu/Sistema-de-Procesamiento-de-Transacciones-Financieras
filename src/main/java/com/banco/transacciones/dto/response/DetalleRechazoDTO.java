package com.banco.transacciones.dto.response;

/**
 * DTO para encapsular el motivo por el cual una transacción en lote falló.
 */
public record DetalleRechazoDTO(
        int indice,
        String motivo
) {}