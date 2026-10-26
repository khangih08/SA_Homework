package com.example.userservice.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.example.userservice.model.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final String secret;
    private final long expirationSeconds;
    private final String issuer;

    public JwtService(
            @Value("${jwt.secret:MicroservicesAppSuperSecretKeyForJwtAuthenticationMustBeAtLeast256BitsLong!}") String secret,
            @Value("${jwt.expiration-seconds:3600}") long expirationSeconds,
            @Value("${jwt.issuer:microservices-app}") String issuer
    ) {
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
        this.issuer = issuer;
    }

    public String generateToken(Long userId, Role role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);
        Algorithm algorithm = Algorithm.HMAC256(secret);

        return JWT.create()
                .withIssuer(issuer)
                .withSubject(String.valueOf(userId))
                .withClaim("role", role.name())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .sign(algorithm);
    }

    public DecodedJWT verifyToken(String token) throws JWTVerificationException {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .build();
        return verifier.verify(token);
    }

    public Long extractUserId(DecodedJWT jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public Role extractRole(DecodedJWT jwt) {
        String roleStr = jwt.getClaim("role").asString();
        return Role.valueOf(roleStr);
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
