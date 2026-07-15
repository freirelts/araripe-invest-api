package com.freirelts.araripe_invest_api.adapters.outbound.openai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
class OpenAiConfig {

	@Bean
	RestClient openAiRestClient(OpenAiProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
		requestFactory.setConnectTimeout(timeout);
		requestFactory.setReadTimeout(timeout);

		return RestClient.builder()
				.baseUrl(trimTrailingSlash(properties.baseUrl()))
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.requestFactory(requestFactory)
				.build();
	}

	private static String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			return "https://api.openai.com/v1";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
