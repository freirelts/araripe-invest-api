package com.freirelts.araripe_invest_api.adapters.outbound.brapi;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.openfeign.EnableFeignClients;

import feign.Request;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(BrapiProperties.class)
@EnableFeignClients(basePackageClasses = BrapiFeignClient.class)
class BrapiConfig {

	@Bean
	Request.Options brapiFeignOptions(BrapiProperties properties) {
		Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
		return new Request.Options(timeout.toMillis(), TimeUnit.MILLISECONDS, timeout.toMillis(), TimeUnit.MILLISECONDS,
				true);
	}
}
