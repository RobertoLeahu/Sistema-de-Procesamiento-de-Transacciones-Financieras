package com.banco.transacciones.dto.response;

import java.util.List;

public record CicloReporte(boolean existeCiclo, List<String> cuentasInvolucradas) {
}
