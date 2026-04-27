package com.banco.transacciones.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.response.CicloReporte;

/**
 * Clase de pruebas unitarias para la validación del algoritmo en
 * {@link CicloTransaccionDetector}. * Verifica la correcta detección de ciclos
 * en grafos de transacciones, los cuales son un patrón fuertemente indicativo
 * de esquemas de lavado de dinero. * Se validan diferentes topologías de red
 * (grafos dirigidos): - Ausencia de transacciones (lista vacía). - Cadenas
 * lineales donde el dinero fluye sin retornar. - Grafos Acíclicos Dirigidos
 * (DAG): Múltiples rutas de origen a destino sin ciclos reales. - Ciclos
 * simples y complejos (ej. A -> B -> C -> A). - Autociclos (transacciones hacia
 * la misma cuenta). - Grafos desconectados: Detección de una red fraudulenta
 * oculta entre transacciones legítimas.
 */
class CicloTransaccionDetectorTest {

	private CicloTransaccionDetector detector;

	@BeforeEach
	void setUp() {
		detector = new CicloTransaccionDetector();
	}

	/**
	 * Valida que el motor de grafos gestione correctamente colecciones vacias,
	 * garantizando que la ausencia total de transacciones retorne un reporte limpio
	 * sin provocar excepciones.
	 */
	@Test
	@DisplayName("Debe retornar falso y lista vacía si no hay transacciones")
	void detectar_ListaVacia_SinCiclo() {
		CicloReporte reporte = detector.detectar(List.of());

		assertFalse(reporte.existeCiclo());
		assertTrue(reporte.cuentasInvolucradas().isEmpty());
	}

	/**
	 * Comprueba el comportamiento base unidireccional. Asegura que un flujo de
	 * dinero estrictamente lineal, donde los fondos nunca retornan a un nodo
	 * previo, sea ignorado por el detector de lavado.
	 */
	@Test
	@DisplayName("Debe retornar falso para una cadena lineal (A -> B -> C -> D)")
	void detectar_CadenaLineal_SinCiclo() {
		List<Transaccion> txs = List.of(tx("A", "B"), tx("B", "C"), tx("C", "D"));

		CicloReporte reporte = detector.detectar(txs);

		assertFalse(reporte.existeCiclo());
	}

	/**
	 * Verifica la resiliencia contra falsos positivos en Grafos Aciclicos
	 * Dirigidos. Asegura que los pagos multiples hacia un mismo destino desde una
	 * raiz comun pero por diferentes intermediarios no se confundan con un ciclo
	 * cerrado.
	 */
	@Test
	@DisplayName("Debe ignorar aristas cruzadas simulando un falso ciclo (A->B, B->C, A->C)")
	void detectar_GrafoAciclicoDirigido_SinCiclo() {
		List<Transaccion> txs = List.of(tx("A", "B"), tx("B", "C"), tx("A", "C"));

		CicloReporte reporte = detector.detectar(txs);

		assertFalse(reporte.existeCiclo(), "Un DAG no debe reportarse como ciclo de lavado de dinero");
	}

	/**
	 * Comprueba la eficacia central del algoritmo DFS. Valida la deteccion exitosa
	 * de un patron de triangulacion clasico de blanqueo de capitales, donde el
	 * dinero retorna a la cuenta emisora original.
	 */
	@Test
	@DisplayName("Debe detectar un ciclo de lavado simple (A -> B -> C -> A)")
	void detectar_CicloSimple_RetornaTrue() {
		List<Transaccion> txs = List.of(tx("A", "B"), tx("B", "C"), tx("C", "A"));

		CicloReporte reporte = detector.detectar(txs);

		assertTrue(reporte.existeCiclo());
		assertEquals(4, reporte.cuentasInvolucradas().size());
		assertEquals(reporte.cuentasInvolucradas().get(0),
				reporte.cuentasInvolucradas().get(reporte.cuentasInvolucradas().size() - 1),
				"El nodo de inicio y fin del reporte deben coincidir");
	}

	/**
	 * Demuestra la capacidad del algoritmo para aislar y encontrar sub-redes
	 * ilicitas. Asegura que una gran masa de transacciones legitimas y aisladas no
	 * enmascare un ciclo fraudulento que ocurra en paralelo dentro del mismo lote.
	 */
	@Test
	@DisplayName("Debe detectar un ciclo complejo en un grafo desconectado")
	void detectar_GrafoDesconectadoConCiclo_RetornaTrue() {
		List<Transaccion> txs = List.of(
				// Red A: Movimiento normal sin ciclo
				tx("X", "Y"), tx("Y", "Z"),
				// Red B: Esquema de lavado
				tx("C1", "C2"), tx("C2", "C3"), tx("C3", "C1"));

		CicloReporte reporte = detector.detectar(txs);

		assertTrue(reporte.existeCiclo());
		assertTrue(reporte.cuentasInvolucradas().contains("C1"));
		assertTrue(reporte.cuentasInvolucradas().contains("C2"));
		assertTrue(reporte.cuentasInvolucradas().contains("C3"));
	}

	/**
	 * Verifica la identificacion de los autociclos. Evalua que las transferencias
	 * enviadas directamente a la misma cuenta de origen marquen un ciclo evidente.
	 */
	@Test
	@DisplayName("Debe detectar un auto-ciclo (A -> A)")
	void detectar_AutoCiclo_RetornaTrue() {
		List<Transaccion> txs = List.of(tx("CuentaSospechosa", "CuentaSospechosa"));

		CicloReporte reporte = detector.detectar(txs);

		assertTrue(reporte.existeCiclo());
		assertEquals(2, reporte.cuentasInvolucradas().size());
	}

	/**
	 * Garantiza que la gestion de memoria de la pila DFS y los nodos visitados sea
	 * impecable. Evita que patrones de flujo en diamante (bifurcacion y
	 * unificacion) reporten ciclos inexistentes al visitar un mismo nodo varias
	 * veces.
	 */
	@Test
	@DisplayName("Debe manejar nodos duplicados en la pila de llamadas (cobertura estado 2)")
	void detectar_DiamanteConDuplicadosEnPila_SinCiclo() {
		List<Transaccion> txs = List.of(tx("Raiz", "Rama1"), tx("Raiz", "Rama2"), tx("Rama1", "DestinoFinal"),
				tx("Rama2", "DestinoFinal"));

		CicloReporte reporte = detector.detectar(txs);

		assertFalse(reporte.existeCiclo(), "Un flujo en diamante no es un ciclo");
	}

	/**
	 * Helper pattern para instanciar transacciones rápidamente sin Lombok builder
	 */
	private Transaccion tx(String origen, String destino) {
		Transaccion t = new Transaccion();
		t.setCuentaOrigen(origen);
		t.setCuentaDestino(destino);
		return t;
	}
}