package com.yavanika.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Users are authenticated by JWT, so Spring's default in-memory user is not needed.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BankingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }
}
