package com.DartsCounter.DartsCounter_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DartsCounterBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(DartsCounterBackendApplication.class, args);
	}

}
