package com.banco.transacciones.config.async;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Copia el contexto MDC del hilo principal al hilo asíncrono.
 */

public class MdcTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable runnable) {

		Map<String, String> contextMap = MDC.getCopyOfContextMap();

		return () -> {
			try {
				if (contextMap != null) {
					MDC.setContextMap(contextMap);
				}
				runnable.run();
			} finally {
				MDC.clear();
			}
		};
	}

}
