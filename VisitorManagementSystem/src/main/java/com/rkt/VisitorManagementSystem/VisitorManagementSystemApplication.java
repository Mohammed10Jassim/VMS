/*
 *   Copyright (c) 2026 
 *   All rights reserved.
 */
package com.rkt.VisitorManagementSystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VisitorManagementSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(VisitorManagementSystemApplication.class, args);
	}

}
