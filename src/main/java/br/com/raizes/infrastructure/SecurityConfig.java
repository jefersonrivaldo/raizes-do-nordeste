package br.com.raizes.infrastructure;

import static br.com.raizes.infrastructure.Store.*;

import br.com.raizes.api.Contracts.ErrorResponse;
import br.com.raizes.domain.Actor;
import br.com.raizes.domain.Rules.Perfil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  AuthRateLimiter authRateLimiter(
      @Value("${app.auth-rate-limit-max-requests:20}") int maxRequests,
      @Value("${app.auth-rate-limit-window-seconds:60}") long windowSeconds,
      ObjectProvider<Clock> clock) {
    return new AuthRateLimiter(maxRequests, windowSeconds, clock.getIfAvailable(Clock::systemUTC));
  }

  @Bean
  PasswordEncoder encoder() {
    return new BCryptPasswordEncoder(12);
  }

  static void error(
      ObjectMapper mapper,
      HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    mapper.writeValue(
        response.getOutputStream(),
        new ErrorResponse(
            code,
            message,
            List.of(),
            Instant.now().toString(),
            request.getRequestURI(),
            (String) request.getAttribute("requestId")));
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      JwtService jwt,
      Store store,
      ObjectMapper mapper,
      AuthRateLimiter authRateLimiter)
      throws Exception {
    var filter =
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest request, HttpServletResponse response, FilterChain chain)
              throws ServletException, IOException {
            var id = UUID.randomUUID().toString();
            request.setAttribute("requestId", id);
            response.setHeader("X-Request-ID", id);
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            String path = request.getServletPath();
            if ("POST".equals(request.getMethod())
                && ("/auth/login".equals(path) || "/usuarios".equals(path))) {
              var decision = authRateLimiter.check(request.getRemoteAddr());
              if (!decision.allowed()) {
                response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
                error(
                    mapper,
                    request,
                    response,
                    429,
                    "LIMITE_REQUISICOES",
                    "Limite de tentativas atingido. Tente novamente após o prazo informado.");
                return;
              }
            }
            String header = request.getHeader("Authorization");
            if (header != null) {
              try {
                if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
                var row = store.get("users", jwt.verify(header.substring(7)));
                if (!bool(row, "active")) throw new IllegalArgumentException();
                var actor =
                    new Actor(
                        number(row, "id"),
                        text(row, "nome"),
                        Perfil.valueOf(text(row, "perfil")),
                        nullableNumber(row, "unidade_id"));
                SecurityContextHolder.getContext()
                    .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                            actor,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + actor.perfil().name()))));
              } catch (io.jsonwebtoken.JwtException
                  | IllegalArgumentException
                  | br.com.raizes.domain.Rules.BusinessException ex) {
                error(
                    mapper,
                    request,
                    response,
                    401,
                    "TOKEN_INVALIDO",
                    "Token inválido ou expirado.");
                return;
              }
            }
            chain.doFilter(request, response);
          }
        };
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/health",
                        "/docs",
                        "/docs/**",
                        "/swagger-ui/**",
                        "/openapi.json",
                        "/openapi.json/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/usuarios", "/auth/login")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/unidades", "/produtos", "/unidades/*/cardapio")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (rq, rs, ex) ->
                            error(mapper, rq, rs, 401, "NAO_AUTENTICADO", "Informe Bearer token."))
                    .accessDeniedHandler(
                        (rq, rs, ex) ->
                            error(mapper, rq, rs, 403, "SEM_PERMISSAO", "Perfil sem permissão.")))
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
