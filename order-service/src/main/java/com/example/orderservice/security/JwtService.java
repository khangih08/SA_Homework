package com.example.orderservice.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final String secret;
    private final String issuer;

    public JwtService(
            @Value("${jwt.secret:MicroservicesAppSuperSecretKeyForJwtAuthenticationMustBeAtLeast256BitsLong!}") String secret,
            @Value("${jwt.issuer:microservices-app}") String issuer
    ) {
        this.secret = secret;
        this.issuer = issuer;
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

    public String extractRole(DecodedJWT jwt) {
        return jwt.getClaim("role").asString();
    }
}
