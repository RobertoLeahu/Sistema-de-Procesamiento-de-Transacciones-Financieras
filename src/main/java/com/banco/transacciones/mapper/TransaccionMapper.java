package com.banco.transacciones.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.response.TransaccionDTO;

/**
 * Mapper generado automáticamente en tiempo de compilación para aislar el
 * modelo de dominio.
 */
@Mapper
public interface TransaccionMapper {

	// Mapea de Entidad a DTO de salida
	TransaccionDTO toResponse(Transaccion transaccion);

	// Mapea de DTO de entrada a Entidad (Ignorando campos autogenerados o
	// calculados)
	@Mapping(target = "id", ignore = true)
	@Mapping(target = "estado", ignore = true)
	@Mapping(target = "tipo", ignore = true)
	@Mapping(target = "fechaHora", ignore = true)
	@Mapping(target = "riesgoFraude", ignore = true)
	@Mapping(target = "alertas", ignore = true)
	Transaccion toEntity(TransaccionDTO request);
}
