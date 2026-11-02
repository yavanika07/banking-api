package com.yavanika.banking.service;

import com.yavanika.banking.dto.AuthDtos.AuthResponse;
import com.yavanika.banking.dto.AuthDtos.LoginRequest;
import com.yavanika.banking.dto.AuthDtos.RegisterRequest;
import com.yavanika.banking.exception.BankingException;
import com.yavanika.banking.model.Customer;
import com.yavanika.banking.repository.CustomerRepository;
import com.yavanika.banking.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (customerRepository.existsByEmail(email)) {
            throw BankingException.conflict("An account with this email already exists");
        }
        Customer customer = customerRepository.save(
                new Customer(req.fullName().trim(), email, passwordEncoder.encode(req.password())));
        return tokenFor(customer);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        Customer customer = customerRepository.findByEmail(email)
                .filter(c -> passwordEncoder.matches(req.password(), c.getPasswordHash()))
                // Same message for wrong email and wrong password, so emails can't be probed.
                .orElseThrow(() -> BankingException.unauthorized("Invalid email or password"));
        return tokenFor(customer);
    }

    private AuthResponse tokenFor(Customer c) {
        String token = jwtService.generateToken(c.getId(), c.getEmail());
        return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(),
                c.getId(), c.getFullName(), c.getEmail());
    }
}
