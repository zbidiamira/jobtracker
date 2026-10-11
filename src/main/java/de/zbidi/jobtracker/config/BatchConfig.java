package de.zbidi.jobtracker.config;

import org.springframework.boot.batch.autoconfigure.BatchTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * Jobs run asynchronously: Boot hands the {@link BatchTaskExecutor} to the JobOperator, so {@code start} and
 * {@code restart} return at once (status STARTING) and the job continues on a virtual thread.
 * <p>
 * The job repository is JDBC (spring-boot-starter-batch-jdbc); its tables come from Flyway V10.
 */
@Configuration(proxyBeanMethods = false)
public class BatchConfig {

	/** Not a default candidate, so Boot's own applicationTaskExecutor is still created for everything else. */
	@Bean(defaultCandidate = false)
	@BatchTaskExecutor
	TaskExecutor batchTaskExecutor() {
		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("batch-");
		executor.setVirtualThreads(true);
		return executor;
	}

}
