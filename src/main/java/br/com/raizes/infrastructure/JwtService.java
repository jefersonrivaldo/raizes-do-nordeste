package br.com.raizes.infrastructure;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey key;
  public final long ttl;

  public JwtService(
      @Value("${app.jwt-secret}") String secret, @Value("${app.token-ttl}") long ttl) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalStateException("Configure JWT_SECRET com pelo menos 32 bytes.");
    if (ttl < 1 || ttl > 86400)
      throw new IllegalStateException("TOKEN_TTL_SECONDS deve estar entre 1 e 86400.");
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.ttl = ttl;
  }

  public String issue(long id) {
    var now = Instant.now();
    return Jwts.builder()
        .subject(Long.toString(id))
        .issuer("raizes-do-nordeste")
        .audience()
        .add("raizes-api")
        .and()
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(ttl)))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  public long verify(String token) {
    var jwt =
        Jwts.parser()
            .verifyWith(key)
            .requireIssuer("raizes-do-nordeste")
            .requireAudience("raizes-api")
            .build()
            .parseSignedClaims(token);
    if (!"HS256".equals(jwt.getHeader().getAlgorithm())
        || jwt.getPayload().getExpiration() == null
        || jwt.getPayload().getIssuedAt() == null)
      throw new IllegalArgumentException("Token inválido");
    return Long.parseLong(jwt.getPayload().getSubject());
  }
}
