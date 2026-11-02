package com.yavanika.banking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(@Value("${app.jwt.secret}") String base64Secret,
                      @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(Long customerId, String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("cid", customerId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /** Returns the email (subject) if the token is valid and not expired. */
    public Optional<String> validateAndGetEmail(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
