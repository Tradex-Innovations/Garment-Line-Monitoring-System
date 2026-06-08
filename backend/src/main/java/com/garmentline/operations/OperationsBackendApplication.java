package com.garmentline.operations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OperationsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(OperationsBackendApplication.class, args);
	}

}
