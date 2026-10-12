package br.com.raizes;

import static org.junit.jupiter.api.Assertions.*;

import br.com.raizes.infrastructure.Seed;
import br.com.raizes.infrastructure.Store;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:raizes-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
      "app.jwt-secret=only-tests-secret-at-least-32-characters-long",
      "app.seed-password=AdminTeste12345",
      "app.auth-rate-limit-max-requests=100000",
      "app.order-expiration-scan-ms=3600000"
    })
abstract class BaseApiTest {
  @LocalServerPort int port;
  @Autowired ObjectMapper json;
  @Autowired Store store;
  @Autowired Seed seed;
  HttpClient http = HttpClient.newHttpClient();
  String client, admin;
  long clientId;

  @BeforeEach
  void setup() throws Exception {
    store.jdbc().execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table :
        List.of(
            "idempotency",
            "audit",
            "loyalty_movements",
            "stock_movements",
            "payments",
            "order_items",
            "orders",
            "stocks",
            "products",
            "users",
            "units")) store.jdbc().execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
    store.jdbc().execute("SET REFERENTIAL_INTEGRITY TRUE");
    seed.run(null);
    clientId = register("cliente@example.com");
    client = login("cliente@example.com", "SenhaTeste123");
    admin = login("admin@raizes.local", "AdminTeste12345");
  }

  JsonNode call(String method, String path, Object body, String token, int status, String key)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json");
    if (token != null) builder.header("Authorization", "Bearer " + token);
    if (key != null) builder.header("Idempotency-Key", key);
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(
        status,
        response.statusCode(),
        "Método " + method + " rota " + path + "; resposta: " + response.body());
    return response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
  }

  JsonNode call(String method, String path, Object body, String token, int status)
      throws Exception {
    return call(method, path, body, token, status, null);
  }

  long register(String email) throws Exception {
    return call(
            "POST",
            "/usuarios",
            Map.of("nome", "Pessoa de estudo", "email", email, "senha", "SenhaTeste123"),
            null,
            201)
        .get("id")
        .asLong();
  }

  String login(String email, String password) throws Exception {
    return call("POST", "/auth/login", Map.of("email", email, "senha", password), null, 200)
        .get("accessToken")
        .asText();
  }

  Map<String, Object> body(int quantity, String channel) {
    return new LinkedHashMap<>(
        Map.of(
            "unidadeId",
            1,
            "canalPedido",
            channel,
            "itens",
            List.of(Map.of("produtoId", 1, "quantidade", quantity)),
            "formaPagamento",
            "MOCK"));
  }

  JsonNode order(int quantity, String key) throws Exception {
    return call("POST", "/pedidos", body(quantity, "APP"), client, 201, key);
  }

  JsonNode payment(long order, String scenario, String key, int status) throws Exception {
    return call(
        "POST",
        "/pedidos/" + order + "/pagamentos",
        Map.of("cenario", scenario),
        client,
        status,
        key);
  }

  JsonNode status(long id, String target, String token, int expected) throws Exception {
    return call("PATCH", "/pedidos/" + id + "/status", Map.of("status", target), token, expected);
  }

  long stock() throws Exception {
    return call("GET", "/unidades/1/cardapio", null, null, 200)
        .get("items")
        .get(0)
        .get("disponivel")
        .asLong();
  }

  void consent(boolean enabled) throws Exception {
    call("PUT", "/fidelidade/consentimento", Map.of("consentimento", enabled), client, 200);
  }

  long balance() throws Exception {
    return call("GET", "/fidelidade", null, client, 200).get("saldo").asLong();
  }
}
