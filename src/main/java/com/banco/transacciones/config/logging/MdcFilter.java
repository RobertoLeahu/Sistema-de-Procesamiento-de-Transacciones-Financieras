package com.banco.transacciones.config.logging;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * Filtro que asigna un correlationId único a cada petición HTTP.
 */

@Component
public class MdcFilter implements Filter {

	private static final String CORRELATION_ID_KEY = "correlationId";

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		try {
			// Generar el ID de correlación requerido
			String correlationId = UUID.randomUUID().toString();
			MDC.put(CORRELATION_ID_KEY, correlationId);

			chain.doFilter(request, response);
		} finally {
			/**
			 * Limpieza del MDC para evitar memory leaks y contaminación de logs entre
			 * hilos.
			 */
			MDC.remove(CORRELATION_ID_KEY);
		}
	}

}
