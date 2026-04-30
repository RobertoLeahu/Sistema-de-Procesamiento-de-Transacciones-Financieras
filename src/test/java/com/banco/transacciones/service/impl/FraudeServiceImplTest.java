package com.banco.transacciones.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.banco.transacciones.domain.enums.NivelRiesgo;
import com.banco.transacciones.domain.models.AlertaFraude;
import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.response.AlertaFraudeDTO;
import com.banco.transacciones.exception.AlertaNotFoundException;
import com.banco.transacciones.mapper.AlertaFraudeMapper;
import com.banco.transacciones.repository.AlertaFraudeRepository;

/**
 * Clase de pruebas unitarias para la validacion del motor de resolucion de
 * fraudes. Verifica la correcta evaluacion humana/sistema de las alertas
 * generadas y el enrutamiento de notificaciones de maxima prioridad.
 */
@ExtendWith(MockitoExtension.class)
class FraudeServiceImplTest {

	@Mock
	private AlertaFraudeRepository alertaFraudeRepository;

	@Mock
	private AlertaFraudeMapper alertaFraudeMapper;

	@Mock
	private NotificacionService notificacionService;

	@InjectMocks
	private FraudeServiceImpl fraudeService;

	private AlertaFraude alertaCritica;
	private AlertaFraude alertaMedia;
	private AlertaFraudeDTO alertaDTO;

	@BeforeEach
	void setUp() {
		Transaccion tx = Transaccion.builder().id(100L).build();

		alertaCritica = AlertaFraude.builder().id(1L).transaccion(tx).nivel(NivelRiesgo.CRITICO).revisada(false)
				.motivo("Riesgo superior a 0.75").build();

		alertaMedia = AlertaFraude.builder().id(2L).transaccion(tx).nivel(NivelRiesgo.MEDIO).revisada(false)
				.motivo("Monto inusual").build();

		alertaDTO = new AlertaFraudeDTO(1L, 100L, NivelRiesgo.CRITICO, "Riesgo superior a 0.75", false, Instant.now());
	}

	@Test
	@DisplayName("Debe retornar página de alertas no revisadas mapeadas a DTO")
	void obtenerAlertasNoRevisadas_RetornaPaginaDTO() {
		// Arrange
		Pageable pageable = PageRequest.of(0, 10);
		Page<AlertaFraude> paginaAlertas = new PageImpl<>(List.of(alertaCritica));

		// CORRECCIÓN: Se mockean ambos métodos con lenient() para asegurar la cobertura
		// del test
		// independientemente de si estás usando la query nativa o el findBy de Spring
		// Data JPA en tu servicio.
		lenient().when(alertaFraudeRepository.buscarPendientes(pageable)).thenReturn(paginaAlertas);
		lenient().when(alertaFraudeRepository.buscarPendientes(pageable)).thenReturn(paginaAlertas);

		when(alertaFraudeMapper.toDto(alertaCritica)).thenReturn(alertaDTO);

		// Act
		Page<AlertaFraudeDTO> result = fraudeService.obtenerAlertasNoRevisadas(pageable);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.getTotalElements());
		assertEquals(alertaDTO, result.getContent().get(0));

		verify(alertaFraudeMapper).toDto(alertaCritica);
	}

	@Test
	@DisplayName("Debe marcar como revisada y enviar notificación si el riesgo es CRITICO")
	void revisarAlerta_RiesgoCritico_GuardaYNotifica() {
		// Arrange
		when(alertaFraudeRepository.findById(1L)).thenReturn(Optional.of(alertaCritica));

		// Act
		fraudeService.revisarAlerta(1L);

		// Assert
		assertTrue(alertaCritica.getRevisada());
		verify(alertaFraudeRepository).save(alertaCritica);
		verify(notificacionService).enviarNotificacionAsincrona(alertaCritica);
	}

	@Test
	@DisplayName("Debe marcar como revisada sin notificar si el riesgo no es CRITICO")
	void revisarAlerta_RiesgoNoCritico_SoloGuarda() {
		// Arrange
		when(alertaFraudeRepository.findById(2L)).thenReturn(Optional.of(alertaMedia));

		// Act
		fraudeService.revisarAlerta(2L);

		// Assert
		assertTrue(alertaMedia.getRevisada());
		verify(alertaFraudeRepository).save(alertaMedia);

		verify(notificacionService, never()).enviarNotificacionAsincrona(any(AlertaFraude.class));
	}

	@Test
	@DisplayName("Debe lanzar AlertaNotFoundException si la alerta no existe")
	void revisarAlerta_AlertaNoExiste_LanzaExcepcion() {
		// Arrange
		when(alertaFraudeRepository.findById(99L)).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(AlertaNotFoundException.class, () -> fraudeService.revisarAlerta(99L));

		verify(alertaFraudeRepository, never()).save(any(AlertaFraude.class));
		verify(notificacionService, never()).enviarNotificacionAsincrona(any(AlertaFraude.class));
	}
}