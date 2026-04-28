package com.banco.transacciones.dto.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banco.transacciones.dto.request.TransferenciaDTO;

import jakarta.validation.ConstraintValidatorContext;

/**
 * Clase de pruebas unitarias para la validacion cruzada de cuentas en transferencias.
 * Verifica la logica de negocio estricta que impide que los fondos sean transferidos
 * a la misma cuenta de origen, previniendo transacciones fantasma.
 * * Flujos y ramas validadas:
 * - Tolerancia a nulos (Delegacion en @NotNull / @NotBlank de Bean Validation).
 * - Identificacion de cuentas identicas (Sensibilidad a Case-Insensitive).
 * - Validacion exitosa de pares de cuentas legitimos.
 */
@ExtendWith(MockitoExtension.class)
class CuentasDistintasValidatorTest {

	private CuentasDistintasValidator validator;

	@Mock
	private ConstraintValidatorContext context;

	private static final String CUENTA_ORIGEN = "ES1234567890123456789012";
	private static final String CUENTA_DESTINO = "ES9876543210987654321098";

	@BeforeEach
	void setUp() {
		validator = new CuentasDistintasValidator();
	}

	/**
	 * Valida que el interceptor no bloquee la evaluacion si el DTO completo es
	 * nulo, delegando esta responsabilidad a las validaciones estructurales de
	 * Spring.
	 */
	@Test
	@DisplayName("Debe retornar true (validación exitosa) si el DTO request es null")
	void isValid_RequestNull_RetornaTrue() {
		assertTrue(validator.isValid(null, context));
	}

	/**
	 * Comprueba la tolerancia a nulos parciales. Si la cuenta origen es nula, el
	 * validador asume un estado valido para no solapar errores con @NotBlank.
	 */
	@Test
	@DisplayName("Debe retornar true si la cuenta origen es null")
	void isValid_CuentaOrigenNull_RetornaTrue() {
		TransferenciaDTO request = new TransferenciaDTO(null, CUENTA_DESTINO, BigDecimal.TEN, "ES", "Test");
		assertTrue(validator.isValid(request, context));
	}

	/**
	 * Comprueba la tolerancia a nulos parciales en el destino.
	 */
	@Test
	@DisplayName("Debe retornar true si la cuenta destino es null")
	void isValid_CuentaDestinoNull_RetornaTrue() {
		TransferenciaDTO request = new TransferenciaDTO(CUENTA_ORIGEN, null, BigDecimal.TEN, "ES", "Test");
		assertTrue(validator.isValid(request, context));
	}

	/**
	 * Verifica el "Happy Path" del negocio. Dos identificadores bancarios
	 * diferentes deben pasar la validacion de integridad.
	 */
	@Test
	@DisplayName("Debe retornar true si las cuentas origen y destino son distintas")
	void isValid_CuentasDistintas_RetornaTrue() {
		TransferenciaDTO request = new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_DESTINO, BigDecimal.TEN, "ES", "Test");
		assertTrue(validator.isValid(request, context));
	}

	/**
	 * Valida la regla anti-fraude primaria. Impide la ejecucion de la logica cuando
	 * se intenta operar contra el mismo origen, lanzando un constraint violation.
	 */
	@Test
	@DisplayName("Debe retornar false si las cuentas son exactamente iguales")
	void isValid_CuentasIguales_RetornaFalse() {
		TransferenciaDTO request = new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_ORIGEN, BigDecimal.TEN, "ES", "Test");
		assertFalse(validator.isValid(request, context));
	}

	/**
	 * Garantiza que la validacion no sea evadida mediante manipulacion de formato
	 * (por ejemplo, enviando el codigo de pais en minusculas en el destino).
	 */
	@Test
	@DisplayName("Debe retornar false si las cuentas son iguales ignorando mayúsculas y minúsculas")
	void isValid_CuentasIgualesIgnorandoCase_RetornaFalse() {
		TransferenciaDTO request = new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_ORIGEN.toLowerCase(), BigDecimal.TEN, "ES", "Test");
		assertFalse(validator.isValid(request, context));
	}
}