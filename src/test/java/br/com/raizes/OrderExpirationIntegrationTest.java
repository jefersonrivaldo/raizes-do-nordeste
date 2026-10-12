package br.com.raizes;

import static org.junit.jupiter.api.Assertions.*;

import br.com.raizes.application.Services;
import br.com.raizes.domain.Actor;
import br.com.raizes.domain.Rules.BusinessException;
import br.com.raizes.domain.Rules.Perfil;
import br.com.raizes.domain.Rules.Status;
import br.com.raizes.infrastructure.OrderExpiration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.order-reservation-ttl-seconds=120")
class OrderExpirationIntegrationTest extends BaseApiTest {
  private static final long TTL_SECONDS = 120;

  @Autowired Services services;
  @Autowired OrderExpiration expiration;

  private void aged(long id, long seconds) {
    store.jdbc().update(
        "UPDATE orders SET created_at=? WHERE id=?",
        Instant.now().minusSeconds(seconds).truncatedTo(ChronoUnit.SECONDS).toString(), id);
  }

  private long redeemedOrder(String key) throws Exception {
    consent(true);
    store.jdbc().update("UPDATE users SET points=100 WHERE id=?", clientId);
    var request = body(2, "APP");
    request.put("pontosResgate", 100);
    return call("POST", "/pedidos", request, client, 201, key).get("id").asLong();
  }

  private long expirationMovements(long id) {
    return store.jdbc().queryForObject(
        "SELECT COUNT(*) FROM stock_movements WHERE pedido_id=?"
            + " AND motivo='CANCELAMENTO_EXPIRACAO'", Long.class, id);
  }

  private String orderStatus(long id) {
    return (String) store.get("orders", id).get("status");
  }

  @Test
  void prazoConfiguradoDevolveEstoquePontosEAuditaUmaVez() throws Exception {
    long id = redeemedOrder("expiring-redemption");
    assertEquals(98, stock());
    assertEquals(0, balance());
    aged(id, TTL_SECONDS + 1);

    assertTrue(services.expireOrder(id));
    assertFalse(services.expireOrder(id));
    expiration.expireReservations();
    assertEquals("CANCELADO", orderStatus(id));
    assertEquals(100, stock());
    assertEquals(100, balance());
    assertEquals(1, expirationMovements(id));
    assertEquals(1L, store.jdbc().queryForObject(
        "SELECT COUNT(*) FROM loyalty_movements WHERE pedido_id=? AND motivo='DEVOLUCAO'",
        Long.class, id));
    assertEquals(1L, store.jdbc().queryForObject(
        "SELECT COUNT(*) FROM audit WHERE recurso=? AND acao='PEDIDO_EXPIRADO'",
        Long.class, Long.toString(id)));
    status(id, "CANCELADO", admin, 409);
    assertEquals(100, stock());
    assertEquals(100, balance());
  }

  @Test
  void scanSelecionaApenasReservaVencidaEIgnoraPedidosPagos() throws Exception {
    long overdue = order(2, "overdue-order").get("id").asLong();
    long fresh = order(2, "fresh-order").get("id").asLong();
    long paid = order(2, "paid-order").get("id").asLong();
    payment(paid, "APROVAR", "paid-payment", 201);
    aged(overdue, TTL_SECONDS + 1);
    aged(fresh, TTL_SECONDS - 60);
    aged(paid, TTL_SECONDS + 1);

    assertEquals(List.of(overdue), services.expiredOrderIds());
    expiration.expireReservations();
    assertEquals("CANCELADO", orderStatus(overdue));
    assertEquals("AGUARDANDO_PAGAMENTO", orderStatus(fresh));
    assertEquals("EM_PREPARO", orderStatus(paid));
    assertEquals(96, stock());
  }

  @Test
  void pagamentoTardioConfirmaDevolucaoAntesDeResponderConflito() throws Exception {
    long id = redeemedOrder("expired-before-payment");
    aged(id, TTL_SECONDS + 1);

    assertEquals("PEDIDO_EXPIRADO",
        payment(id, "APROVAR", "late-payment", 409).get("error").asText());
    assertEquals("CANCELADO", orderStatus(id));
    assertEquals(100, stock());
    assertEquals(100, balance());
    assertEquals(0L, store.jdbc().queryForObject(
        "SELECT COUNT(*) FROM payments WHERE pedido_id=?", Long.class, id));
    payment(id, "APROVAR", "late-payment", 409);
    assertFalse(services.expireOrder(id));
    assertEquals(1, expirationMovements(id));
    assertEquals(100, stock());
  }

  @Test
  void pagamentoAindaDentroDoPrazoPodeSerAprovado() throws Exception {
    long id = order(2, "valid-reservation").get("id").asLong();
    aged(id, TTL_SECONDS - 60);
    assertFalse(services.expireOrder(id));
    assertEquals("APROVADO", payment(id, "APROVAR", "valid-payment", 201).get("status").asText());
    assertEquals("EM_PREPARO", orderStatus(id));
    assertEquals(98, stock());
  }

  @Test
  void reenvioPedidoExpiradoMantemRecursoENaoReservaNovamente() throws Exception {
    long id = order(2, "replayed-expired-order").get("id").asLong();
    aged(id, TTL_SECONDS + 1);
    assertTrue(services.expireOrder(id));
    var replay = order(2, "replayed-expired-order");
    assertEquals(id, replay.get("id").asLong());
    assertEquals("CANCELADO", replay.get("status").asText());
    assertEquals(100, stock());
    assertEquals(1L, store.jdbc().queryForObject("SELECT COUNT(*) FROM orders", Long.class));
  }

  @Test
  void pagamentoAprovadoMantemIdempotenciaDepoisDoPrazo() throws Exception {
    long id = order(2, "approved-replay-order").get("id").asLong();
    var approved = payment(id, "APROVAR", "approved-replay-payment", 201);
    aged(id, TTL_SECONDS + 1);
    assertFalse(services.expireOrder(id));
    assertEquals(approved.get("id"),
        payment(id, "APROVAR", "approved-replay-payment", 201).get("id"));
    assertEquals("EM_PREPARO", orderStatus(id));
    assertEquals(98, stock());
    assertEquals(0, expirationMovements(id));
  }

  @Test
  void pagamentoNegadoReenviadoMantemRespostaEDevolveReservaVencida() throws Exception {
    long id = order(2, "denied-replay-order").get("id").asLong();
    var denied = payment(id, "NEGAR", "denied-replay-payment", 201);
    aged(id, TTL_SECONDS + 1);
    assertEquals(denied.get("id"),
        payment(id, "NEGAR", "denied-replay-payment", 201).get("id"));
    assertEquals("CANCELADO", orderStatus(id));
    assertEquals(100, stock());
    payment(id, "APROVAR", "new-payment-after-denial", 409);
  }

  @Test
  void semConsentimentoAtivoNaoRestauraPontos() throws Exception {
    long id = redeemedOrder("revoked-redemption");
    consent(false);
    aged(id, TTL_SECONDS + 1);
    assertTrue(services.expireOrder(id));
    assertEquals(100, stock());
    assertEquals(0, balance());
    assertEquals(0L, store.jdbc().queryForObject(
        "SELECT COUNT(*) FROM loyalty_movements WHERE pedido_id=? AND motivo='DEVOLUCAO'",
        Long.class, id));
  }

  @Test
  void pagamentoSemAcessoNaoAlteraReservaExpirada() throws Exception {
    long id = order(2, "unauthorized-expiry-order").get("id").asLong();
    aged(id, TTL_SECONDS + 1);
    register("other-expiry@example.com");
    var other = login("other-expiry@example.com", "SenhaTeste123");
    call("POST", "/pedidos/" + id + "/pagamentos",
        Map.of("cenario", "APROVAR"), other, 403, "unauthorized-payment");
    assertEquals("AGUARDANDO_PAGAMENTO", orderStatus(id));
    assertEquals(98, stock());
    assertEquals(0, expirationMovements(id));
  }

  @Test
  void falhaNaDevolucaoDePontosDesfazEstoqueEPermiteProximoPedidoDoLote() throws Exception {
    long failing = redeemedOrder("failing-redemption");
    long valid = order(2, "valid-batch-order").get("id").asLong();
    store.jdbc().update("UPDATE users SET points=? WHERE id=?", Long.MAX_VALUE, clientId);
    aged(failing, TTL_SECONDS + 1);
    aged(valid, TTL_SECONDS + 1);

    expiration.expireReservations();
    assertEquals("AGUARDANDO_PAGAMENTO", orderStatus(failing));
    assertEquals("CANCELADO", orderStatus(valid));
    assertEquals(98, stock());
    assertEquals(0, expirationMovements(failing));
    assertEquals(1, expirationMovements(valid));

    store.jdbc().update("UPDATE users SET points=0 WHERE id=?", clientId);
    assertTrue(services.expireOrder(failing));
    assertEquals(100, stock());
    assertEquals(100, balance());
  }

  @Test
  void expiracaoConcorrenteComPagamentoTardioDevolveApenasUmaVez() throws Exception {
    long id = redeemedOrder("concurrent-expiry-order");
    aged(id, TTL_SECONDS + 1);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var expire = pool.submit(() -> {
        start.await();
        return services.expireOrder(id);
      });
      var pay = pool.submit(() -> {
        start.await();
        return payment(id, "APROVAR", "concurrent-late-payment", 409);
      });
      start.countDown();
      expire.get(15, TimeUnit.SECONDS);
      pay.get(15, TimeUnit.SECONDS);
    }
    assertEquals("CANCELADO", orderStatus(id));
    assertEquals(100, stock());
    assertEquals(100, balance());
    assertEquals(1, expirationMovements(id));
    assertEquals(0L, store.jdbc().queryForObject(
        "SELECT COUNT(*) FROM payments WHERE pedido_id=?", Long.class, id));
  }

  @Test
  void variosScansConcorrentesNaoDuplicamDevolucao() throws Exception {
    long id = redeemedOrder("multiple-scans-order");
    aged(id, TTL_SECONDS + 1);
    try (var pool = Executors.newFixedThreadPool(3)) {
      var futures = pool.invokeAll(List.<Callable<Boolean>>of(
          () -> services.expireOrder(id),
          () -> services.expireOrder(id),
          () -> services.expireOrder(id)));
      long changed = 0;
      for (var future : futures) if (future.get()) changed++;
      assertEquals(1, changed);
    }
    assertEquals(100, stock());
    assertEquals(100, balance());
    assertEquals(1, expirationMovements(id));
  }

  @Test
  void cancelamentoManualConcorrenteComExpiracaoDevolveUmaVez() throws Exception {
    long id = redeemedOrder("cancel-expire-order");
    aged(id, TTL_SECONDS + 1);
    long adminId = store.jdbc().queryForObject(
        "SELECT id FROM users WHERE perfil='ADMIN'", Long.class);
    var administrator = new Actor(adminId, "Administrador", Perfil.ADMIN, null);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var expire = pool.submit(() -> {
        start.await();
        return services.expireOrder(id);
      });
      var cancel = pool.submit(() -> {
        start.await();
        try {
          services.status(administrator, id, Status.CANCELADO);
          return true;
        } catch (BusinessException exception) {
          assertEquals("TRANSICAO_INVALIDA", exception.code);
          return false;
        }
      });
      start.countDown();
      assertNotEquals(expire.get(15, TimeUnit.SECONDS), cancel.get(15, TimeUnit.SECONDS));
    }
    assertEquals("CANCELADO", orderStatus(id));
    assertEquals(100, stock());
    assertEquals(100, balance());
    assertEquals(1L, store.jdbc().queryForObject(
        "SELECT COUNT(*) FROM stock_movements WHERE pedido_id=?"
            + " AND motivo IN ('CANCELAMENTO','CANCELAMENTO_EXPIRACAO')", Long.class, id));
  }

  @Test
  void entregaDePedidoPagoConcorrenteComScanPreservaEstoqueEFidelidade() throws Exception {
    consent(true);
    long id = order(4, "deliver-expire-order").get("id").asLong();
    payment(id, "APROVAR", "deliver-expire-payment", 201);
    status(id, "PRONTO", admin, 200);
    aged(id, TTL_SECONDS + 1);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var expire = pool.submit(() -> {
        start.await();
        return services.expireOrder(id);
      });
      var deliver = pool.submit(() -> {
        start.await();
        return status(id, "ENTREGUE", admin, 200);
      });
      start.countDown();
      assertFalse(expire.get(15, TimeUnit.SECONDS));
      deliver.get(15, TimeUnit.SECONDS);
    }
    assertEquals("ENTREGUE", orderStatus(id));
    assertEquals(96, stock());
    assertEquals(100, balance());
    assertEquals(0, expirationMovements(id));
    status(id, "ENTREGUE", admin, 409);
    assertEquals(100, balance());
  }
}
