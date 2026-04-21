package com.banco.transacciones.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.banco.transacciones.domain.models.AlertaFraude;
import com.banco.transacciones.dto.response.AlertaFraudeDTO;

@Mapper(componentModel = "spring")
public interface AlertaFraudeMapper {

	@Mapping(target = "transaccionId", source = "transaccion.id")
	AlertaFraudeDTO toDto(AlertaFraude entity);
}
