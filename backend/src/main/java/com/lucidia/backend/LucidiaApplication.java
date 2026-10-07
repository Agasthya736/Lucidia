package com.lucidia.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LucidiaApplication {

	public static void main(String[] args) {
		SpringApplication.run(LucidiaApplication.class, args);
	}

}
