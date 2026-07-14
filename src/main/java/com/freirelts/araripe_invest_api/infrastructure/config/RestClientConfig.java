package com.freirelts.araripe_invest_api.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class RestClientConfig {

	@Bean
	RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}
}
