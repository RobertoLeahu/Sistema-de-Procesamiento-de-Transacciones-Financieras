package com.banco.transacciones.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.banco.transacciones.config.async.MdcTaskDecorator;

@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean("transaccionExecutor")
	public Executor transaccionExecutor() {

		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

		int coreCount = Runtime.getRuntime().availableProcessors();

		executor.setCorePoolSize(coreCount);
		executor.setMaxPoolSize(coreCount * 2);

		executor.setQueueCapacity(200);

		executor.setThreadNamePrefix("trans-exec-");

		// Inyectamos el decorador para no perder el correlationId
		executor.setTaskDecorator(new MdcTaskDecorator());
		
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

		executor.initialize();

		return executor;
	}

}