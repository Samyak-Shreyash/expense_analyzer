package com.expenseanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@SpringBootApplication
@EnableWebSecurity
// @EnableWebSecurity removed for Spring Security 7.x compatibility
public class ExpenseanalyzerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExpenseanalyzerApplication.class, args);
	}

}
