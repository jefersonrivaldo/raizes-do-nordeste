package br.com.raizes;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.raizes.infrastructure.AuthRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:raizes-rate-limit-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
      "app.jwt-secret=rate-limit-tests-secret-at-least-32-characters-long",
      "app.seed-enabled=false",
      "app.auth-rate-limit-max-requests=2",
      "app.auth-rate-limit-window-seconds=60",
      "app.order-expiration-scan-ms=3600000"
    })
@AutoConfigureMockMvc
@Import(AuthRateLimitIntegrationTest.ClockConfiguration.class)
class AuthRateLimitIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired MutableClock clock;

  @BeforeEach
  void advanceWindow() {
    clock.advance(Duration.ofHours(1));
  }

  @Test
  void tentativasDeLoginSaoLimitadasComEnvelopeECabecalhos() throws Exception {
    String body = "{\"email\":\"inexistente@example.com\",\"senha\":\"SenhaIncorreta123\"}";
    for (int i = 0; i < 2; i++) {
      mvc.perform(authPost("/auth/login", body, "192.0.2.1")).andExpect(status().isUnauthorized());
    }

    var response =
        mvc.perform(authPost("/auth/login", body, "192.0.2.1"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn()
            .getResponse();

    assertRateLimitError(response, "/auth/login");
  }

  @Test
  void cadastroELoginCompartilhamLimiteSemBloquearGet() throws Exception {
    mvc.perform(authPost("/usuarios", "{}", "192.0.2.1"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error").value("VALIDACAO"));
    mvc.perform(authPost("/auth/login", "{}", "192.0.2.1"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error").value("VALIDACAO"));

    var response =
        mvc.perform(authPost("/usuarios", "{}", "192.0.2.1"))
            .andExpect(status().isTooManyRequests())
            .andReturn()
            .getResponse();
    assertRateLimitError(response, "/usuarios");
    for (int i = 0; i < 4; i++) {
      mvc.perform(
              get("/health")
                  .servletPath("/health")
                  .with(
                      r -> {
                        r.setRemoteAddr("192.0.2.1");
                        return r;
                      }))
          .andExpect(status().isOk())
          .andExpect(header().doesNotExist("Retry-After"));
    }
  }

  @Test
  void enderecoDaConexaoDefineLimiteSemConfiarNosCabecalhosDeProxy() throws Exception {
    for (int i = 0; i < 2; i++) {
      mvc.perform(
              authPost("/auth/login", "{}", "192.0.2.1")
                  .header("X-Forwarded-For", "198.51.100." + i)
                  .header("Forwarded", "for=198.51.100." + i))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.error").value("VALIDACAO"));
    }
    mvc.perform(
            authPost("/auth/login", "{}", "192.0.2.1")
                .header("X-Forwarded-For", "203.0.113.1")
                .header("Forwarded", "for=203.0.113.1"))
        .andExpect(status().isTooManyRequests());
    mvc.perform(authPost("/auth/login", "{}", "192.0.2.2"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error").value("VALIDACAO"))
        .andExpect(header().doesNotExist("Retry-After"));
  }

  @Test
  void janelaExpiraEArredondaRetryAfterSemEsperarTempoReal() throws Exception {
    for (int i = 0; i < 2; i++) {
      mvc.perform(authPost("/auth/login", "{}", "192.0.2.1"))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.error").value("VALIDACAO"));
    }
    clock.advance(Duration.ofMillis(1500));
    mvc.perform(authPost("/auth/login", "{}", "192.0.2.1"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "59"));

    clock.advance(Duration.ofMillis(58_500));
    for (int i = 0; i < 2; i++) {
      mvc.perform(authPost("/auth/login", "{}", "192.0.2.1"))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.error").value("VALIDACAO"));
    }
    mvc.perform(authPost("/auth/login", "{}", "192.0.2.1"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"));
  }

  @Test
  void configuracaoInvalidaFalhaAntesDeAtenderRequisicoes() {
    assertThrows(IllegalArgumentException.class, () -> new AuthRateLimiter(0, 60, clock));
    assertThrows(IllegalArgumentException.class, () -> new AuthRateLimiter(-1, 60, clock));
    assertThrows(IllegalArgumentException.class, () -> new AuthRateLimiter(2, 0, clock));
    assertThrows(IllegalArgumentException.class, () -> new AuthRateLimiter(2, -1, clock));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AuthRateLimiter(2, Long.MAX_VALUE / 1000 + 1, clock));
  }

  @Test
  void armazenamentoTemLimiteSemDescartarOrcamentosAtivos() {
    var localClock = new MutableClock();
    var limiter = new AuthRateLimiter(2, 60, localClock);
    for (int i = 0; i < 10_000; i++) assertTrue(limiter.check("client-" + i).allowed());

    var full = limiter.check("new-client");
    assertFalse(full.allowed());
    assertEquals(60, full.retryAfterSeconds());
    assertTrue(limiter.check("client-0").allowed());
    assertFalse(limiter.check("client-0").allowed());
    localClock.advance(Duration.ofMinutes(1));
    assertTrue(limiter.check("new-client").allowed());
    assertTrue(limiter.check("client-0").allowed());
  }

  @Test
  void requisicoesConcorrentesNaoUltrapassamOrcamento() throws Exception {
    var limiter = new AuthRateLimiter(3, 60, new MutableClock());
    List<Callable<Boolean>> attempts =
        IntStream.range(0, 100)
            .mapToObj(i -> (Callable<Boolean>) () -> limiter.check("192.0.2.1").allowed())
            .toList();
    try (var executor = Executors.newFixedThreadPool(8)) {
      int accepted = 0;
      for (var result : executor.invokeAll(attempts)) if (result.get()) accepted++;
      assertEquals(3, accepted);
    }
  }

  private MockHttpServletRequestBuilder authPost(String path, String body, String address) {
    return post(path)
        .servletPath(path)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .with(
            r -> {
              r.setRemoteAddr(address);
              return r;
            });
  }

  private void assertRateLimitError(MockHttpServletResponse response, String path) throws Exception {
    assertTrue(response.getContentType().startsWith("application/json"));
    var body = json.readTree(response.getContentAsString());
    assertEquals(6, body.size());
    assertEquals("LIMITE_REQUISICOES", body.get("error").asText());
    assertFalse(body.get("message").asText().isBlank());
    assertTrue(body.get("details").isArray());
    assertTrue(body.get("details").isEmpty());
    assertEquals(path, body.get("path").asText());
    assertDoesNotThrow(() -> Instant.parse(body.get("timestamp").asText()));
    assertDoesNotThrow(() -> UUID.fromString(body.get("requestId").asText()));
    assertEquals(response.getHeader("X-Request-ID"), body.get("requestId").asText());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ClockConfiguration {
    @Bean
    MutableClock authRateLimitTestClock() {
      return new MutableClock();
    }
  }

  static final class MutableClock extends Clock {
    private final AtomicLong epochMillis;
    private final ZoneId zone;

    MutableClock() {
      this(new AtomicLong(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()), ZoneOffset.UTC);
    }

    private MutableClock(AtomicLong epochMillis, ZoneId zone) {
      this.epochMillis = epochMillis;
      this.zone = zone;
    }

    void advance(Duration duration) {
      epochMillis.addAndGet(duration.toMillis());
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return new MutableClock(epochMillis, zone);
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis());
    }

    @Override
    public long millis() {
      return epochMillis.get();
    }
  }
}
