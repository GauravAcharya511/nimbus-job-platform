package com.gauravacharya.nimbus;

import org.springframework.boot.SpringApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class NimbusApplication {

	public static void main(String[] args) {
		SpringApplication.run(NimbusApplication.class, args);
	}

}
