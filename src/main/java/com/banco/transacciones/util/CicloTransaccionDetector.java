package com.banco.transacciones.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.response.CicloReporte;

@Component
public class CicloTransaccionDetector {

	/**
	 * Detecta ciclos en un grafo dirigido usando DFS iterativo. Complejidad: O(V +
	 * E)
	 */
	public CicloReporte detectar(List<Transaccion> transaccionesDia) {
		Map<String, List<String>> grafo = construirGrafo(transaccionesDia);
		Map<String, Integer> estados = new HashMap<>(); // 0: no visitado, 1: visitando, 2: visitado
		Map<String, String> padres = new HashMap<>();

		for (String nodo : grafo.keySet()) {
			estados.put(nodo, 0);
		}

		for (String nodoInicial : grafo.keySet()) {
			if (estados.get(nodoInicial) == 0) {
				CicloReporte reporte = dfsIterativo(nodoInicial, grafo, estados, padres);
				if (reporte.existeCiclo()) {
					return reporte; // Retorna rápido al primer ciclo
				}
			}
		}
		return new CicloReporte(false, Collections.emptyList());
	}

	private Map<String, List<String>> construirGrafo(List<Transaccion> transacciones) {
		Map<String, List<String>> grafo = new HashMap<>();
		for (Transaccion tx : transacciones) {
			grafo.computeIfAbsent(tx.getCuentaOrigen(), k -> new ArrayList<>()).add(tx.getCuentaDestino());
			grafo.putIfAbsent(tx.getCuentaDestino(), new ArrayList<>()); // Asegurar que el destino exista en el grafo
		}
		return grafo;
	}

	private CicloReporte dfsIterativo(String inicio, Map<String, List<String>> grafo, Map<String, Integer> estados,
			Map<String, String> padres) {
		Deque<String> pila = new ArrayDeque<>();
		pila.push(inicio);

		while (!pila.isEmpty()) {
			String actual = pila.peek();

			if (estados.get(actual) == 0) {
				estados.put(actual, 1); // Visitando
			} else if (estados.get(actual) == 1) {
				estados.put(actual, 2); // Completamente visitado
				pila.pop();
				continue;
			} else {
				pila.pop();
				continue;
			}

			for (String vecino : grafo.getOrDefault(actual, Collections.emptyList())) {
				if (estados.get(vecino) == 0) {
					padres.put(vecino, actual);
					pila.push(vecino);
				} else if (estados.get(vecino) == 1) { // Back-edge encontrado = Ciclo
					return reconstruirCiclo(actual, vecino, padres);
				}
			}
		}
		return new CicloReporte(false, Collections.emptyList());
	}

	private CicloReporte reconstruirCiclo(String inicio, String fin, Map<String, String> padres) {
		List<String> ciclo = new ArrayList<>();
		String actual = inicio;
		ciclo.add(fin);
		while (actual != null && !actual.equals(fin)) {
			ciclo.add(actual);
			actual = padres.get(actual);
		}
		ciclo.add(fin);
		Collections.reverse(ciclo);
		return new CicloReporte(true, ciclo);
	}
}
