package br.com.raizes.application;

import static br.com.raizes.domain.Rules.require;
import static br.com.raizes.infrastructure.Store.*;

import br.com.raizes.api.Contracts.*;
import br.com.raizes.domain.Actor;
import br.com.raizes.domain.Rules;
import br.com.raizes.domain.Rules.*;
import br.com.raizes.infrastructure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class Services {
  private final Store db;

  public Store store() {
    return db;
  }

  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final MockGateway gateway;
  private final ObjectMapper json;
  private final String dummyHash;

  public Services(
      Store db, PasswordEncoder encoder, JwtService jwt, MockGateway gateway, ObjectMapper json) {
    this.db = db;
    this.encoder = encoder;
    this.jwt = jwt;
    this.gateway = gateway;
    this.json = json;
    dummyHash = encoder.encode("not-a-user-password");
  }

  public static String now() {
    return Instant.now().toString();
  }

  public void audit(long actor, String action, Object resource) {
    db.jdbc()
        .update(
            "INSERT INTO audit(actor_id,acao,recurso,created_at) VALUES (?,?,?,?)",
            actor,
            action,
            resource.toString(),
            now());
  }

  public UserResponse user(Map<String, Object> row) {
    return new UserResponse(
        number(row, "id"),
        text(row, "nome"),
        Perfil.valueOf(text(row, "perfil")),
        nullableNumber(row, "unidade_id"));
  }

  public UserResponse register(Cadastro data) {
    require(
        data.senha().getBytes(StandardCharsets.UTF_8).length <= 72,
        "VALIDACAO",
        "Senha deve ter no máximo 72 bytes UTF-8.",
        422);
    require(
        db.jdbc()
            .queryForList(
                "SELECT id FROM users WHERE email=?", data.email().trim().toLowerCase(Locale.ROOT))
            .isEmpty(),
        "EMAIL_EM_USO",
        "E-mail já cadastrado.");
    long id =
        db.insert(
            "INSERT INTO users(nome,email,password_hash,perfil,created_at) VALUES (?,?,?,?,?)",
            data.nome().trim(),
            data.email().trim().toLowerCase(Locale.ROOT),
            encoder.encode(data.senha()),
            "CLIENTE",
            now());
    audit(id, "CADASTRO", id);
    return user(db.get("users", id));
  }

  public LoginResponse login(Login data) {
    var rows =
        db.jdbc()
            .queryForList(
                "SELECT * FROM users WHERE email=? AND active=TRUE",
                data.email().trim().toLowerCase(Locale.ROOT));
    boolean valid =
        encoder.matches(
            data.senha(), rows.isEmpty() ? dummyHash : text(rows.getFirst(), "password_hash"));
    require(!rows.isEmpty() && valid, "CREDENCIAIS_INVALIDAS", "E-mail ou senha inválidos.", 401);
    var user = user(rows.getFirst());
    audit(user.id(), "LOGIN", user.id());
    return new LoginResponse(jwt.issue(user.id()), "Bearer", jwt.ttl, user);
  }

  public void unitAccess(Actor actor, long unit) {
    require(
        actor.perfil() == Perfil.ADMIN || Objects.equals(actor.unidadeId(), unit),
        "SEM_PERMISSAO",
        "Sem acesso à unidade.",
        403);
  }

  public void orderAccess(Actor actor, Map<String, Object> order) {
    if (actor.perfil() == Perfil.CLIENTE)
      require(
          number(order, "cliente_id") == actor.id(), "SEM_PERMISSAO", "Sem acesso ao pedido.", 403);
    else unitAccess(actor, number(order, "unidade_id"));
  }

  public UserResponse me(Actor actor) {
    audit(actor.id(), "PERFIL_CONSULTADO", actor.id());
    return user(db.get("users", actor.id()));
  }

  public UserResponse profile(Actor actor, long id, Vinculo data) {
    require(
        id != actor.id(), "PERFIL_PROPRIO", "Administrador não pode alterar seu próprio perfil.");
    var row = db.lock("users", id);
    require(bool(row, "active"), "USUARIO_INATIVO", "Usuário inativo.");
    boolean operational = data.perfil() != Perfil.ADMIN && data.perfil() != Perfil.CLIENTE;
    require(
        operational == (data.unidadeId() != null),
        "UNIDADE_INVALIDA",
        "Perfil operacional exige unidade; ADMIN/CLIENTE não recebem vínculo.",
        422);
    require(
        data.perfil() == Perfil.CLIENTE
            || db.jdbc().queryForList("SELECT id FROM orders WHERE cliente_id=?", id).isEmpty(),
        "PERFIL_COM_PEDIDOS",
        "Usuário com pedidos deve manter perfil CLIENTE.");
    if (data.unidadeId() != null) db.get("units", data.unidadeId());
    db.jdbc()
        .update(
            "UPDATE users SET perfil=?,unidade_id=? WHERE id=?",
            data.perfil().name(),
            data.unidadeId(),
            id);
    audit(actor.id(), "PERFIL_ALTERADO", id);
    return user(db.get("users", id));
  }

  public UnitResponse unit(Map<String, Object> row) {
    return new UnitResponse(
        number(row, "id"), text(row, "nome"), text(row, "endereco"), bool(row, "active"));
  }

  public ProductResponse product(Map<String, Object> row) {
    return new ProductResponse(
        number(row, "id"), text(row, "nome"), text(row, "descricao"), bool(row, "active"));
  }

  public PageResponse<UnitResponse> units(int page, int limit) {
    return new PageResponse<>(
        db
            .jdbc()
            .queryForList(
                "SELECT * FROM units ORDER BY id LIMIT ? OFFSET ?", limit, (page - 1) * limit)
            .stream()
            .map(this::unit)
            .toList(),
        page,
        limit);
  }

  public PageResponse<ProductResponse> products(int page, int limit) {
    return new PageResponse<>(
        db
            .jdbc()
            .queryForList(
                "SELECT * FROM products ORDER BY id LIMIT ? OFFSET ?", limit, (page - 1) * limit)
            .stream()
            .map(this::product)
            .toList(),
        page,
        limit);
  }

  public UnitResponse saveUnit(Actor actor, Long id, Unidade data) {
    if (id == null) {
      id =
          db.insert(
              "INSERT INTO units(nome,endereco,active) VALUES (?,?,?)",
              data.nome().trim(),
              data.endereco().trim(),
              data.ativo() == null || data.ativo());
    } else {
      unitAccess(actor, id);
      db.get("units", id);
      db.jdbc()
          .update(
              "UPDATE units SET nome=?,endereco=?,active=? WHERE id=?",
              data.nome().trim(),
              data.endereco().trim(),
              data.ativo() == null || data.ativo(),
              id);
    }
    audit(actor.id(), "UNIDADE_SALVA", id);
    return unit(db.get("units", id));
  }

  public ProductResponse saveProduct(Actor actor, Long id, Produto data) {
    if (id == null)
      id =
          db.insert(
              "INSERT INTO products(nome,descricao,active) VALUES (?,?,?)",
              data.nome().trim(),
              data.descricao() == null ? "" : data.descricao(),
              data.ativo() == null || data.ativo());
    else {
      db.get("products", id);
      db.jdbc()
          .update(
              "UPDATE products SET nome=?,descricao=?,active=? WHERE id=?",
              data.nome().trim(),
              data.descricao() == null ? "" : data.descricao(),
              data.ativo() == null || data.ativo(),
              id);
    }
    audit(actor.id(), "PRODUTO_SALVO", id);
    return product(db.get("products", id));
  }

  public PageResponse<MenuResponse> menu(long id, int page, int limit) {
    require(bool(db.get("units", id), "active"), "UNIDADE_INATIVA", "Unidade inativa.");
    var rows =
        db.jdbc()
            .queryForList(
                "SELECT p.id,p.nome,p.descricao,s.preco_centavos,s.quantidade FROM stocks s JOIN"
                    + " products p ON p.id=s.produto_id WHERE s.unidade_id=? AND p.active=TRUE"
                    + " ORDER BY p.id LIMIT ? OFFSET ?",
                id,
                limit,
                (page - 1) * limit);
    return new PageResponse<>(
        rows.stream()
            .map(
                r ->
                    new MenuResponse(
                        number(r, "id"),
                        text(r, "nome"),
                        text(r, "descricao"),
                        number(r, "preco_centavos"),
                        number(r, "quantidade")))
            .toList(),
        page,
        limit);
  }

  public StockResponse stock(Map<String, Object> row) {
    return new StockResponse(
        number(row, "produto_id"), number(row, "quantidade"), number(row, "preco_centavos"));
  }

  public PageResponse<StockResponse> stocks(Actor actor, long id, int page, int limit) {
    unitAccess(actor, id);
    db.get("units", id);
    return new PageResponse<>(
        db
            .jdbc()
            .queryForList(
                "SELECT * FROM stocks WHERE unidade_id=? ORDER BY produto_id LIMIT ? OFFSET ?",
                id,
                limit,
                (page - 1) * limit)
            .stream()
            .map(this::stock)
            .toList(),
        page,
        limit);
  }

  private void stockMove(
      long unit, long product, long amount, String reason, long actor, Long order) {
    int changed =
        db.jdbc()
            .update(
                "UPDATE stocks SET quantidade=quantidade+? WHERE unidade_id=? AND produto_id=? AND"
                    + " quantidade+?>=0",
                amount,
                unit,
                product,
                amount);
    require(
        changed == 1, "ESTOQUE_INSUFICIENTE", "Estoque inexistente ou quantidade insuficiente.");
    db.jdbc()
        .update(
            "INSERT INTO"
                + " stock_movements(unidade_id,produto_id,quantidade,motivo,pedido_id,actor_id,created_at)"
                + " VALUES (?,?,?,?,?,?,?)",
            unit,
            product,
            amount,
            reason,
            order,
            actor,
            now());
  }

  public StockResponse moveStock(Actor actor, long unit, Estoque data) {
    unitAccess(actor, unit);
    db.lock("units", unit);
    db.get("products", data.produtoId());
    var rows =
        db.jdbc()
            .queryForList(
                "SELECT * FROM stocks WHERE unidade_id=? AND produto_id=? FOR UPDATE",
                unit,
                data.produtoId());
    if (rows.isEmpty()) {
      require(
          data.precoCentavos() != null,
          "PRECO_OBRIGATORIO",
          "Primeiro movimento exige preço.",
          422);
      db.jdbc()
          .update(
              "INSERT INTO stocks VALUES (?,?,0,?)", unit, data.produtoId(), data.precoCentavos());
    } else if (data.precoCentavos() != null)
      db.jdbc()
          .update(
              "UPDATE stocks SET preco_centavos=? WHERE unidade_id=? AND produto_id=?",
              data.precoCentavos(),
              unit,
              data.produtoId());
    stockMove(unit, data.produtoId(), data.quantidade(), data.motivo(), actor.id(), null);
    audit(actor.id(), "ESTOQUE_MOVIMENTADO", unit + "/" + data.produtoId());
    return stock(
        db.jdbc()
            .queryForMap(
                "SELECT * FROM stocks WHERE unidade_id=? AND produto_id=?",
                unit,
                data.produtoId()));
  }

  private String fingerprint(Object value) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));
    } catch (Exception e) {
      throw new IllegalStateException("Falha de serialização.", e);
    }
  }

  private Long replay(Actor actor, String op, String key, String digest) {
    var rows =
        db.jdbc()
            .queryForList(
                "SELECT * FROM idempotency WHERE actor_id=? AND operacao=? AND chave=?",
                actor.id(),
                op,
                key);
    if (rows.isEmpty()) return null;
    require(
        text(rows.getFirst(), "fingerprint").equals(digest),
        "IDEMPOTENCIA_CONFLITO",
        "Chave já usada com outro conteúdo.");
    return number(rows.getFirst(), "recurso_id");
  }

  private void remember(Actor actor, String op, String key, String digest, long id) {
    db.jdbc().update("INSERT INTO idempotency VALUES (?,?,?,?,?)", actor.id(), op, key, digest, id);
  }

  private void loyaltyMove(long user, long amount, String reason, Long order) {
    require(
        db.jdbc()
                .update(
                    "UPDATE users SET points=points+? WHERE id=? AND points+?>=0",
                    amount,
                    user,
                    amount)
            == 1,
        "PONTOS_INSUFICIENTES",
        "Saldo insuficiente de pontos.");
    db.jdbc()
        .update(
            "INSERT INTO loyalty_movements(user_id,pedido_id,quantidade,motivo,created_at) VALUES"
                + " (?,?,?,?,?)",
            user,
            order,
            amount,
            reason,
            now());
  }

  public OrderResponse order(long id) {
    var r = db.get("orders", id);
    var items =
        db
            .jdbc()
            .queryForList("SELECT * FROM order_items WHERE pedido_id=? ORDER BY id", id)
            .stream()
            .map(
                i ->
                    new ItemResponse(
                        number(i, "produto_id"),
                        text(i, "nome_produto"),
                        number(i, "quantidade"),
                        number(i, "preco_centavos")))
            .toList();
    return new OrderResponse(
        id,
        number(r, "cliente_id"),
        number(r, "unidade_id"),
        Canal.valueOf(text(r, "canal_pedido")),
        Status.valueOf(text(r, "status")),
        number(r, "subtotal_centavos"),
        number(r, "desconto_centavos"),
        number(r, "total_centavos"),
        number(r, "pontos_resgate"),
        text(r, "created_at"),
        items);
  }

  public OrderResponse createOrder(Actor actor, Pedido data, String key) {
    db.lock("users", actor.id());
    String digest = fingerprint(data);
    Long old = replay(actor, "PEDIDO", key, digest);
    if (old != null) {
      orderAccess(actor, db.get("orders", old));
      return order(old);
    }
    require(
        bool(db.get("units", data.unidadeId()), "active"), "UNIDADE_INATIVA", "Unidade inativa.");
    long customer = data.clienteId() == null ? actor.id() : data.clienteId();
    if (actor.perfil() == Perfil.CLIENTE)
      require(customer == actor.id(), "SEM_PERMISSAO", "Cliente só cria pedidos próprios.", 403);
    else unitAccess(actor, data.unidadeId());
    var user = db.lock("users", customer);
    require(
        text(user, "perfil").equals("CLIENTE") && bool(user, "active"),
        "CLIENTE_INVALIDO",
        "Cliente inválido.");
    require(
        data.itens().stream().map(Item::produtoId).distinct().count() == data.itens().size(),
        "ITEM_DUPLICADO",
        "Agrupe produtos repetidos.",
        422);
    var sorted = data.itens().stream().sorted(Comparator.comparing(Item::produtoId)).toList();
    var lines = new ArrayList<ItemResponse>();
    long subtotal = 0;
    for (var i : sorted) {
      var product = db.get("products", i.produtoId());
      require(bool(product, "active"), "PRODUTO_INATIVO", "Produto inativo.");
      var rows =
          db.jdbc()
              .queryForList(
                  "SELECT * FROM stocks WHERE unidade_id=? AND produto_id=? FOR UPDATE",
                  data.unidadeId(),
                  i.produtoId());
      require(
          !rows.isEmpty() && number(rows.getFirst(), "quantidade") >= i.quantidade(),
          "ESTOQUE_INSUFICIENTE",
          "Estoque insuficiente na unidade.");
      long price = number(rows.getFirst(), "preco_centavos");
      subtotal += price * i.quantidade();
      lines.add(new ItemResponse(i.produtoId(), text(product, "nome"), i.quantidade(), price));
    }
    long points = data.pontosResgate() == null ? 0 : data.pontosResgate();
    if (points > 0) {
      require(
          bool(user, "consent"),
          "CONSENTIMENTO_NECESSARIO",
          "Fidelidade exige consentimento ativo.");
      require(
          number(user, "points") >= points,
          "PONTOS_INSUFICIENTES",
          "Saldo insuficiente de pontos.");
    }
    var totals = Rules.totals(subtotal, data.cupom(), points);
    String date = now();
    long id =
        db.insert(
            "INSERT INTO"
                + " orders(cliente_id,unidade_id,canal_pedido,status,subtotal_centavos,desconto_centavos,total_centavos,pontos_resgate,created_at,updated_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?)",
            customer,
            data.unidadeId(),
            data.canalPedido().name(),
            "AGUARDANDO_PAGAMENTO",
            subtotal,
            totals.discount(),
            totals.total(),
            points,
            date,
            date);
    for (var i : lines) {
      db.insert(
          "INSERT INTO order_items(pedido_id,produto_id,nome_produto,quantidade,preco_centavos)"
              + " VALUES (?,?,?,?,?)",
          id,
          i.produtoId(),
          i.nome(),
          i.quantidade(),
          i.precoUnitarioCentavos());
      stockMove(data.unidadeId(), i.produtoId(), -i.quantidade(), "VENDA", actor.id(), id);
    }
    if (points > 0) loyaltyMove(customer, -points, "RESGATE", id);
    audit(actor.id(), "PEDIDO_CRIADO", id);
    remember(actor, "PEDIDO", key, digest, id);
    return order(id);
  }

  public OrderResponse readOrder(Actor actor, long id) {
    orderAccess(actor, db.get("orders", id));
    audit(actor.id(), "PEDIDO_CONSULTADO", id);
    return order(id);
  }

  public PageResponse<OrderResponse> orders(
      Actor actor, int page, int limit, Canal channel, Status status) {
    var clauses = new ArrayList<String>();
    var args = new ArrayList<Object>();
    if (actor.perfil() == Perfil.CLIENTE) {
      clauses.add("cliente_id=?");
      args.add(actor.id());
    } else if (actor.perfil() != Perfil.ADMIN) {
      clauses.add("unidade_id=?");
      args.add(actor.unidadeId());
    }
    if (channel != null) {
      clauses.add("canal_pedido=?");
      args.add(channel.name());
    }
    if (status != null) {
      clauses.add("status=?");
      args.add(status.name());
    }
    String where = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    args.add(limit);
    args.add((page - 1) * limit);
    var rows =
        db.jdbc()
            .queryForList(
                "SELECT id FROM orders" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                args.toArray());
    audit(actor.id(), "PEDIDOS_CONSULTADOS", actor.id());
    return new PageResponse<>(rows.stream().map(r -> order(number(r, "id"))).toList(), page, limit);
  }

  private PaymentResponse payment(Map<String, Object> row) {
    try {
      return new PaymentResponse(
          number(row, "id"),
          number(row, "pedido_id"),
          text(row, "status"),
          number(row, "valor_centavos"),
          json.readValue(
              text(row, "provider_payload"),
              new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
    } catch (Exception e) {
      throw new IllegalStateException("Pagamento persistido inválido.", e);
    }
  }

  public PaymentResponse pay(Actor actor, long id, Scenario scenario, String key) {
    db.lock("users", actor.id());
    var order = db.lock("orders", id);
    orderAccess(actor, order);
    String digest = fingerprint(Map.of("pedidoId", id, "cenario", scenario));
    Long old = replay(actor, "PAGAMENTO", key, digest);
    if (old != null) return payment(db.get("payments", old));
    require(
        text(order, "status").equals("AGUARDANDO_PAGAMENTO"),
        "PAGAMENTO_INVALIDO",
        "Pedido não aguarda pagamento.");
    var result = gateway.pay(id, number(order, "total_centavos"), scenario);
    String payload;
    try {
      payload = json.writeValueAsString(result);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    long paymentId =
        db.insert(
            "INSERT INTO payments(pedido_id,status,valor_centavos,provider_payload,created_at)"
                + " VALUES (?,?,?,?,?)",
            id,
            result.get("status"),
            number(order, "total_centavos"),
            payload,
            now());
    if (result.get("status").equals("APROVADO"))
      db.jdbc().update("UPDATE orders SET status='EM_PREPARO',updated_at=? WHERE id=?", now(), id);
    remember(actor, "PAGAMENTO", key, digest, paymentId);
    audit(actor.id(), "PAGAMENTO_" + result.get("status"), id);
    return payment(db.get("payments", paymentId));
  }

  public PageResponse<PaymentResponse> payments(Actor actor, long id, int page, int limit) {
    orderAccess(actor, db.get("orders", id));
    audit(actor.id(), "PAGAMENTOS_CONSULTADOS", id);
    return new PageResponse<>(
        db
            .jdbc()
            .queryForList(
                "SELECT * FROM payments WHERE pedido_id=? ORDER BY id LIMIT ? OFFSET ?",
                id,
                limit,
                (page - 1) * limit)
            .stream()
            .map(this::payment)
            .toList(),
        page,
        limit);
  }

  public OrderResponse status(Actor actor, long id, Status target) {
    db.lock("users", actor.id());
    var order = db.lock("orders", id);
    orderAccess(actor, order);
    if (actor.perfil() == Perfil.CLIENTE)
      require(
          target == Status.CANCELADO && text(order, "status").equals("AGUARDANDO_PAGAMENTO"),
          "SEM_PERMISSAO",
          "Cliente cancela apenas pedido ainda não pago.",
          403);
    if (actor.perfil() == Perfil.COZINHA)
      require(
          target == Status.PRONTO, "SEM_PERMISSAO", "Cozinha pode apenas marcar como pronto.", 403);
    Rules.transition(text(order, "status"), target);
    long customer = number(order, "cliente_id");
    var user = db.lock("users", customer);
    if (target == Status.CANCELADO) {
      for (var item :
          db.jdbc()
              .queryForList("SELECT * FROM order_items WHERE pedido_id=? ORDER BY produto_id", id))
        stockMove(
            number(order, "unidade_id"),
            number(item, "produto_id"),
            number(item, "quantidade"),
            "CANCELAMENTO",
            actor.id(),
            id);
      if (number(order, "pontos_resgate") > 0 && bool(user, "consent"))
        loyaltyMove(customer, number(order, "pontos_resgate"), "DEVOLUCAO", id);
      db.jdbc()
          .update(
              "UPDATE payments SET status='ESTORNADO' WHERE pedido_id=? AND status='APROVADO'", id);
    }
    if (target == Status.ENTREGUE) {
      long points = bool(user, "consent") ? number(order, "total_centavos") / 100 : 0;
      if (points > 0) loyaltyMove(customer, points, "COMPRA", id);
      db.jdbc().update("UPDATE orders SET pontos_concedidos=? WHERE id=?", points, id);
    }
    db.jdbc()
        .update("UPDATE orders SET status=?,updated_at=? WHERE id=?", target.name(), now(), id);
    audit(actor.id(), "STATUS_" + target.name(), id);
    return order(id);
  }

  public ConsentResponse consent(Actor actor, boolean enabled) {
    var user = db.lock("users", actor.id());
    if (!enabled && number(user, "points") > 0)
      loyaltyMove(actor.id(), -number(user, "points"), "REVOGACAO", null);
    db.jdbc()
        .update(
            "UPDATE users SET consent=?,consent_at=?,consent_version='1.0' WHERE id=?",
            enabled,
            now(),
            actor.id());
    audit(actor.id(), enabled ? "CONSENTIMENTO_ATIVO" : "CONSENTIMENTO_REVOGADO", actor.id());
    return new ConsentResponse(enabled, "1.0");
  }

  public LoyaltyResponse loyalty(Actor actor, int page, int limit) {
    var user = db.get("users", actor.id());
    audit(actor.id(), "FIDELIDADE_CONSULTADA", actor.id());
    var history =
        db
            .jdbc()
            .queryForList(
                "SELECT * FROM loyalty_movements WHERE user_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
                actor.id(),
                limit,
                (page - 1) * limit)
            .stream()
            .map(
                r ->
                    new LoyaltyMovement(
                        nullableNumber(r, "pedido_id"),
                        number(r, "quantidade"),
                        text(r, "motivo"),
                        text(r, "created_at")))
            .toList();
    return new LoyaltyResponse(
        number(user, "points"),
        bool(user, "consent"),
        text(user, "consent_at"),
        text(user, "consent_version"),
        history,
        page,
        limit);
  }

  public void anonymize(Actor actor) {
    var user = db.lock("users", actor.id());
    require(
        db.jdbc()
            .queryForList(
                "SELECT id FROM orders WHERE cliente_id=? AND status NOT IN"
                    + " ('ENTREGUE','CANCELADO')",
                actor.id())
            .isEmpty(),
        "PEDIDOS_EM_ABERTO",
        "Finalize ou cancele os pedidos.");
    if (number(user, "points") > 0)
      loyaltyMove(actor.id(), -number(user, "points"), "ANONIMIZACAO", null);
    db.jdbc()
        .update(
            "UPDATE users SET"
                + " nome='Anonimizado',email=?,password_hash=?,active=FALSE,consent=FALSE,consent_at=?,consent_version='1.0'"
                + " WHERE id=?",
            "anon-" + actor.id() + "@invalid.local",
            encoder.encode(UUID.randomUUID().toString()),
            now(),
            actor.id());
    audit(actor.id(), "ANONIMIZACAO", actor.id());
  }

  public PageResponse<AuditResponse> audits(Actor actor, int page, int limit) {
    audit(actor.id(), "AUDITORIA_CONSULTADA", actor.id());
    return new PageResponse<>(
        db
            .jdbc()
            .queryForList(
                "SELECT * FROM audit ORDER BY id LIMIT ? OFFSET ?", limit, (page - 1) * limit)
            .stream()
            .map(
                r ->
                    new AuditResponse(
                        number(r, "id"),
                        number(r, "actor_id"),
                        text(r, "acao"),
                        text(r, "recurso"),
                        text(r, "created_at")))
            .toList(),
        page,
        limit);
  }
}
