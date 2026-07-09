package com.freirelts.araripe_invest_api.adapters.outbound.brapi;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(BrapiProperties.class)
class BrapiConfig {

	@Bean
	RestClient brapiRestClient(RestClient.Builder builder, BrapiProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
		requestFactory.setConnectTimeout(timeout);
		requestFactory.setReadTimeout(timeout);

		return builder
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				.build();
	}
}
