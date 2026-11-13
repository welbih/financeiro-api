package com.datadynecode.financeiro.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class FinanceiroApiApplication implements CommandLineRunner {

	@Autowired
	private PasswordEncoder passwordEncoder;

	public static void main(String[] args) {
		SpringApplication.run(FinanceiroApiApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		IO.println("ENCODER: " + passwordEncoder.encode("123456"));

		boolean result = passwordEncoder.matches("123456", "$2a$10$SWuiIvwxmwRGRvJpuI81Z.dnxxQo.A/RLWXGXQoV4c3cxUt2yPQb.");

		IO.println("Senha válida: " + result);
	}
}
