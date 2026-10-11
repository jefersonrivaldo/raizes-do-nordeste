package br.com.raizes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ApiIntegrationTest extends BaseApiTest {
  @Test
  void cadastroLoginESemSegredos() throws Exception {
    var j = call("GET", "/usuarios/me", null, client, 200);
    assertEquals("CLIENTE", j.get("perfil").asText());
    assertFalse(j.has("password_hash"));
    assertFalse(j.has("email"));
  }

  @Test
  void senhaUtf8LongaRejeitada() throws Exception {
    call(
        "POST",
        "/usuarios",
        Map.of("nome", "Pessoa", "email", "utf8@example.com", "senha", "漢".repeat(30) + "123"),
        null,
        422);
  }

  @Test
  void loginInvalido() throws Exception {
    assertEquals(
        "CREDENCIAIS_INVALIDAS",
        call(
                "POST",
                "/auth/login",
                Map.of("email", "cliente@example.com", "senha", "errada"),
                null,
                401)
            .get("error")
            .asText());
  }

  @Test
  void semToken() throws Exception {
    var j = call("GET", "/pedidos", null, null, 401);
    assertEquals(6, j.size());
    assertTrue(j.has("requestId"));
  }

  @Test
  void perfilSemPermissao() throws Exception {
    call(
        "POST",
        "/unidades",
        Map.of("nome", "Nova unidade", "endereco", "Rua nova, 100"),
        client,
        403);
  }

  @Test
  void cadastroNaoPermiteAdmin() throws Exception {
    call(
        "POST",
        "/usuarios",
        Map.of(
            "nome",
            "Pessoa",
            "email",
            "p@example.com",
            "senha",
            "SenhaTeste123",
            "perfil",
            "ADMIN"),
        null,
        422);
  }

  @Test
  void canalObrigatorio() throws Exception {
    var b = body(1, "APP");
    b.remove("canalPedido");
    call("POST", "/pedidos", b, client, 422, "missing-channel");
  }

  @Test
  void canalInvalido() throws Exception {
    call("POST", "/pedidos", body(1, "OUTRO"), client, 422, "invalid-channel");
    call("GET", "/pedidos?canalPedido=OUTRO", null, client, 422);
  }

  @Test
  void quantidadeFracionariaRejeitada() throws Exception {
    var b = body(1, "APP");
    b.put("itens", List.of(Map.of("produtoId", 1, "quantidade", 1.5)));
    call("POST", "/pedidos", b, client, 422, "fractional-qty");
  }

  @Test
  void estoqueInsuficienteAtomico() throws Exception {
    call("POST", "/pedidos", body(101, "APP"), client, 409, "insufficient-stock");
    assertEquals(100, stock());
    assertEquals(0, store.jdbc().queryForObject("SELECT COUNT(*) FROM orders", Long.class));
  }

  @Test
  void itensRepetidos() throws Exception {
    var b = body(1, "APP");
    b.put(
        "itens",
        List.of(Map.of("produtoId", 1, "quantidade", 1), Map.of("produtoId", 1, "quantidade", 1)));
    call("POST", "/pedidos", b, client, 422, "duplicate-items");
  }

  @Test
  void idempotenciaPedido() throws Exception {
    var first = order(2, "order-key-1");
    assertEquals(first.get("id"), order(2, "order-key-1").get("id"));
    assertEquals(5000, first.get("totalCentavos").asLong());
    assertEquals(98, stock());
    call("POST", "/pedidos", body(3, "APP"), client, 409, "order-key-1");
  }

  @Test
  void filtroCanalEPaginacao() throws Exception {
    call("POST", "/pedidos", body(2, "TOTEM"), client, 201, "totem-order");
    assertEquals(
        1,
        call("GET", "/pedidos?canalPedido=TOTEM&limit=1", null, client, 200).get("items").size());
    assertEquals(0, call("GET", "/pedidos?canalPedido=APP", null, client, 200).get("items").size());
    call("GET", "/pedidos?limit=101", null, client, 422);
  }

  @Test
  void pagamentoNegadoETentativaNova() throws Exception {
    long id = order(2, "order-key-1").get("id").asLong();
    assertEquals("NEGADO", payment(id, "NEGAR", "payment-1", 201).get("status").asText());
    assertEquals(
        "AGUARDANDO_PAGAMENTO",
        call("GET", "/pedidos/" + id, null, client, 200).get("status").asText());
    payment(id, "APROVAR", "payment-1", 409);
    assertEquals("APROVADO", payment(id, "APROVAR", "payment-2", 201).get("status").asText());
  }

  @Test
  void gatewayIndisponivel() throws Exception {
    long id = order(2, "order-key-1").get("id").asLong();
    assertEquals("ERRO", payment(id, "INDISPONIVEL", "payment-1", 201).get("status").asText());
    payment(id, "APROVAR", "payment-2", 201);
  }

  @Test
  void fluxoCompletoFidelidadeResgate() throws Exception {
    consent(true);
    long id = order(4, "order-key-1").get("id").asLong();
    var first = payment(id, "APROVAR", "payment-1", 201);
    assertEquals(first.get("id"), payment(id, "APROVAR", "payment-1", 201).get("id"));
    status(id, "PRONTO", admin, 200);
    status(id, "ENTREGUE", admin, 200);
    assertEquals(100, balance());
    status(id, "ENTREGUE", admin, 409);
    assertEquals(100, balance());
    var b = body(2, "APP");
    b.put("pontosResgate", 100);
    var second = call("POST", "/pedidos", b, client, 201, "redeem-order");
    assertEquals(4000, second.get("totalCentavos").asLong());
    status(second.get("id").asLong(), "CANCELADO", client, 200);
    assertEquals(100, balance());
    consent(false);
    assertEquals(0, balance());
  }

  @Test
  void semConsentimentoSemPontos() throws Exception {
    var b = body(2, "APP");
    b.put("pontosResgate", 100);
    call("POST", "/pedidos", b, client, 409, "no-consent");
    long id = order(2, "order-key-1").get("id").asLong();
    payment(id, "APROVAR", "payment-1", 201);
    status(id, "PRONTO", admin, 200);
    status(id, "ENTREGUE", admin, 200);
    assertEquals(0, balance());
  }

  @Test
  void cancelamentoDevolveUmaVez() throws Exception {
    long id = order(2, "order-key-1").get("id").asLong();
    status(id, "CANCELADO", client, 200);
    status(id, "CANCELADO", admin, 409);
    assertEquals(100, stock());
    payment(id, "APROVAR", "payment-1", 409);
  }

  @Test
  void cancelamentoPagoEstornaMock() throws Exception {
    long id = order(2, "order-key-1").get("id").asLong();
    payment(id, "APROVAR", "payment-1", 201);
    status(id, "CANCELADO", client, 403);
    status(id, "CANCELADO", admin, 200);
    assertEquals(
        "ESTORNADO",
        call("GET", "/pedidos/" + id + "/pagamentos", null, client, 200)
            .get("items")
            .get(0)
            .get("status")
            .asText());
  }

  @Test
  void transicaoInvalida() throws Exception {
    long id = order(2, "order-key-1").get("id").asLong();
    status(id, "ENTREGUE", admin, 409);
  }

  @Test
  void isolamentoClientes() throws Exception {
    long id = order(2, "order-key-1").get("id").asLong();
    register("outro@example.com");
    String other = login("outro@example.com", "SenhaTeste123");
    call("GET", "/pedidos/" + id, null, other, 403);
    assertEquals(0, call("GET", "/pedidos", null, other, 200).get("items").size());
  }

  @Test
  void cozinhaSoDaUnidade() throws Exception {
    long id = order(2, "order-key-1").get("id").asLong();
    long worker = register("cozinha@example.com");
    call(
        "PUT",
        "/usuarios/" + worker + "/perfil",
        Map.of("perfil", "COZINHA", "unidadeId", 2),
        admin,
        200);
    String kitchen = login("cozinha@example.com", "SenhaTeste123");
    call("GET", "/pedidos/" + id, null, kitchen, 403);
    call(
        "PUT",
        "/usuarios/" + worker + "/perfil",
        Map.of("perfil", "COZINHA", "unidadeId", 1),
        admin,
        200);
    payment(id, "APROVAR", "payment-1", 201);
    status(id, "PRONTO", kitchen, 200);
    status(id, "ENTREGUE", kitchen, 403);
  }

  @Test
  void gerenteEstoqueDaUnidade() throws Exception {
    long worker = register("gerente@example.com");
    call(
        "PUT",
        "/usuarios/" + worker + "/perfil",
        Map.of("perfil", "GERENTE", "unidadeId", 1),
        admin,
        200);
    String manager = login("gerente@example.com", "SenhaTeste123");
    var b = Map.of("produtoId", 1, "quantidade", 10, "motivo", "Reposição");
    call("POST", "/unidades/2/estoque/movimentos", b, manager, 403);
    call("POST", "/unidades/1/estoque/movimentos", b, manager, 201);
    assertEquals(110, stock());
    call(
        "POST",
        "/unidades/1/estoque/movimentos",
        Map.of("produtoId", 1, "quantidade", -111, "motivo", "Saída"),
        manager,
        409);
  }

  @Test
  void cupomERecursoAusente() throws Exception {
    var b = body(2, "APP");
    b.put("cupom", "NORDESTE10");
    assertEquals(
        4500,
        call("POST", "/pedidos", b, client, 201, "coupon-order").get("totalCentavos").asLong());
    b.put("unidadeId", 999);
    call("POST", "/pedidos", b, client, 404, "missing-unit");
  }

  @Test
  void desidentificacaoInvalidaToken() throws Exception {
    long id = order(2, "order-key-1").get("id").asLong();
    call("DELETE", "/usuarios/me", null, client, 409);
    status(id, "CANCELADO", client, 200);
    call("DELETE", "/usuarios/me", null, client, 204);
    call("GET", "/usuarios/me", null, client, 401);
    assertEquals("Anonimizado", store.get("users", clientId).get("nome"));
  }

  @Test
  void tokenAdulterado() throws Exception {
    call("GET", "/usuarios/me", null, "not-a-token", 401);
  }

  @Test
  void concorrenciaNaoVendeAlemDoEstoque() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var jobs =
          List.<Callable<Integer>>of(
              () -> postStatus(body(60, "APP"), "concurrent-1"),
              () -> postStatus(body(60, "APP"), "concurrent-2"));
      var statuses =
          pool.invokeAll(jobs).stream()
              .map(
                  f -> {
                    try {
                      return f.get();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  })
              .sorted()
              .toList();
      assertEquals(List.of(201, 409), statuses);
    }
    assertEquals(40, stock());
  }

  private int postStatus(Object body, String key) throws Exception {
    var request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://127.0.0.1:" + port + "/pedidos"))
            .header("Authorization", "Bearer " + client)
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", key)
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
    return http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode();
  }

  @Test
  void idempotenciaConcorrente() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures =
          pool.invokeAll(
              List.<Callable<Long>>of(
                  () -> order(2, "concurrent-key").get("id").asLong(),
                  () -> order(2, "concurrent-key").get("id").asLong()));
      assertEquals(futures.get(0).get(), futures.get(1).get());
    }
    assertEquals(98, stock());
  }

  @Test
  void seedRepetidoNaoRedefineSaldo() throws Exception {
    order(2, "order-key-1");
    seed.run(null);
    seed.run(null);
    assertEquals(98, stock());
    assertEquals(
        1,
        store.jdbc().queryForObject("SELECT COUNT(*) FROM users WHERE perfil='ADMIN'", Long.class));
  }

  @Test
  void catalogoCriaIdentidadeNova() throws Exception {
    assertEquals(
        3,
        call(
                "POST",
                "/unidades",
                Map.of("nome", "Nova unidade", "endereco", "Rua nova, 100"),
                admin,
                201)
            .get("id")
            .asLong());
    assertEquals(
        4,
        call("POST", "/produtos", Map.of("nome", "Produto novo"), admin, 201).get("id").asLong());
  }

  @Test
  void auditoriaEOpenApi() throws Exception {
    order(2, "order-key-1");
    assertTrue(call("GET", "/auditoria", null, admin, 200).toString().contains("PEDIDO_CRIADO"));
    var schema = call("GET", "/openapi.json", null, null, 200);
    assertTrue(
        schema
            .get("components")
            .get("schemas")
            .get("Pedido")
            .get("required")
            .toString()
            .contains("canalPedido"));
    assertEquals("ok", call("GET", "/health", null, null, 200).get("database").asText());
  }
}
