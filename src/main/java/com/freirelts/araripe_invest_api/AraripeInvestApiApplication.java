package com.freirelts.araripe_invest_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AraripeInvestApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AraripeInvestApiApplication.class, args);
	}

}
