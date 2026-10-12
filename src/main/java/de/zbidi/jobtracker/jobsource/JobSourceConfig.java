package de.zbidi.jobtracker.jobsource;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * One RestClient per portal, with timeouts: a slow portal must not hang the daily fetch.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JobSourceProperties.class)
public class JobSourceConfig {

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
	static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

	@Bean
	ArbeitnowJobSource arbeitnowJobSource(JobSourceProperties properties) {
		return new ArbeitnowJobSource(restClient(properties.arbeitnow().baseUrl()), properties);
	}

	@Bean
	BundesagenturJobSource bundesagenturJobSource(JobSourceProperties properties) {
		return new BundesagenturJobSource(restClient(properties.bundesagentur().baseUrl()), properties);
	}

	private static RestClient restClient(String baseUrl) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}

}
