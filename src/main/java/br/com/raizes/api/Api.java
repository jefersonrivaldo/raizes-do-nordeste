package br.com.raizes.api;

import br.com.raizes.api.Contracts.*;
import br.com.raizes.application.Services;
import br.com.raizes.domain.Actor;
import br.com.raizes.domain.Rules.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class Api {
  private final Services s;

  public Api(Services s) {
    this.s = s;
  }

  @GetMapping("/health")
  @Operation(summary = "Verificar API e banco (público)")
  public Map<String, String> health() {
    s.store().jdbc().queryForObject("SELECT COUNT(*) FROM units", Long.class);
    return Map.of("status", "ok", "database", "ok");
  }

  @PostMapping("/usuarios")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Cadastrar cliente (público; não aceita perfil)")
  public UserResponse register(@Valid @RequestBody Cadastro data) {
    return s.register(data);
  }

  @PostMapping("/auth/login")
  @Operation(summary = "Login público e emissão de JWT")
  public LoginResponse login(@Valid @RequestBody Login data) {
    return s.login(data);
  }

  @GetMapping("/usuarios/me")
  @Operation(summary = "Consultar perfil próprio (JWT)")
  public UserResponse me(@AuthenticationPrincipal Actor actor) {
    return s.me(actor);
  }

  @DeleteMapping("/usuarios/me")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('CLIENTE')")
  @Operation(summary = "Desidentificar conta própria (CLIENTE, sem pedidos abertos)")
  public void anonymize(@AuthenticationPrincipal Actor actor) {
    s.anonymize(actor);
  }

  @PutMapping("/usuarios/{id}/perfil")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Atribuir perfil e unidade (ADMIN)")
  public UserResponse profile(
      @PathVariable long id,
      @Valid @RequestBody Vinculo data,
      @AuthenticationPrincipal Actor actor) {
    return s.profile(actor, id, data);
  }

  @GetMapping("/unidades")
  @Operation(summary = "Listar unidades (público)")
  public PageResponse<UnitResponse> units(
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return s.units(page, limit);
  }

  @PostMapping("/unidades")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Cadastrar unidade (ADMIN)")
  public UnitResponse newUnit(
      @Valid @RequestBody Unidade data, @AuthenticationPrincipal Actor actor) {
    return s.saveUnit(actor, null, data);
  }

  @PutMapping("/unidades/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  @Operation(summary = "Atualizar unidade (ADMIN ou GERENTE da unidade)")
  public UnitResponse updateUnit(
      @PathVariable long id,
      @Valid @RequestBody Unidade data,
      @AuthenticationPrincipal Actor actor) {
    return s.saveUnit(actor, id, data);
  }

  @GetMapping("/produtos")
  @Operation(summary = "Listar catálogo (público)")
  public PageResponse<ProductResponse> products(
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return s.products(page, limit);
  }

  @PostMapping("/produtos")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Cadastrar produto global (ADMIN)")
  public ProductResponse newProduct(
      @Valid @RequestBody Produto data, @AuthenticationPrincipal Actor actor) {
    return s.saveProduct(actor, null, data);
  }

  @PutMapping("/produtos/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Atualizar produto global (ADMIN)")
  public ProductResponse updateProduct(
      @PathVariable long id,
      @Valid @RequestBody Produto data,
      @AuthenticationPrincipal Actor actor) {
    return s.saveProduct(actor, id, data);
  }

  @GetMapping("/unidades/{id}/cardapio")
  @Operation(summary = "Consultar cardápio por unidade (público)")
  public PageResponse<MenuResponse> menu(
      @PathVariable long id,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return s.menu(id, page, limit);
  }

  @GetMapping("/unidades/{id}/estoque")
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  @Operation(summary = "Consultar estoque (ADMIN ou GERENTE da unidade)")
  public PageResponse<StockResponse> stocks(
      @PathVariable long id,
      @AuthenticationPrincipal Actor actor,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return s.stocks(actor, id, page, limit);
  }

  @PostMapping("/unidades/{id}/estoque/movimentos")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
  @Operation(summary = "Movimentar estoque e definir preço (ADMIN ou GERENTE da unidade)")
  public StockResponse move(
      @PathVariable long id,
      @Valid @RequestBody Estoque data,
      @AuthenticationPrincipal Actor actor) {
    return s.moveStock(actor, id, data);
  }

  @PostMapping("/pedidos")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('CLIENTE','ATENDENTE','GERENTE','ADMIN')")
  @Operation(
      summary = "Criar pedido multicanal (cliente próprio ou atendimento na unidade)",
      description =
          "Idempotency-Key obrigatório. Equipe informa clienteId. Estoque e pontos são debitados"
              + " atomicamente; reenvio da mesma chave/corpo devolve o recurso atual.")
  public OrderResponse create(
      @Valid @RequestBody Pedido data,
      @AuthenticationPrincipal Actor actor,
      @RequestHeader("Idempotency-Key")
          @Size(min = 8, max = 100)
          @Pattern(regexp = "[A-Za-z0-9_.-]+")
          String key) {
    return s.createOrder(actor, data, key);
  }

  @GetMapping("/pedidos")
  @Operation(summary = "Consultar pedidos autorizados com filtro por canal/status (JWT)")
  public PageResponse<OrderResponse> orders(
      @AuthenticationPrincipal Actor actor,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestParam(required = false) Canal canalPedido,
      @RequestParam(required = false) Status status) {
    return s.orders(actor, page, limit, canalPedido, status);
  }

  @GetMapping("/pedidos/{id}")
  @Operation(summary = "Consultar pedido próprio ou da unidade autorizada (JWT)")
  public OrderResponse order(@PathVariable long id, @AuthenticationPrincipal Actor actor) {
    return s.readOrder(actor, id);
  }

  @PatchMapping("/pedidos/{id}/status")
  @Operation(
      summary = "Avançar status ou cancelar conforme perfil e unidade",
      description =
          "Cliente cancela apenas antes de pagar; cozinha apenas marca PRONTO; atendimento avança"
              + " PRONTO/ENTREGUE ou cancela antes da entrega.")
  public OrderResponse status(
      @PathVariable long id,
      @Valid @RequestBody MudancaStatus data,
      @AuthenticationPrincipal Actor actor) {
    return s.status(actor, id, data.status());
  }

  @PostMapping("/pedidos/{id}/pagamentos")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('CLIENTE','ATENDENTE','GERENTE','ADMIN')")
  @Operation(
      summary = "Registrar tentativa de pagamento MOCK (cliente próprio ou atendimento autorizado)",
      description =
          "HTTP 201 significa tentativa criada. Examine APROVADO/NEGADO/ERRO no corpo; nova"
              + " tentativa exige nova chave. Apenas aprovação permite EM_PREPARO.")
  public PaymentResponse pay(
      @PathVariable long id,
      @Valid @RequestBody Pagamento data,
      @AuthenticationPrincipal Actor actor,
      @RequestHeader("Idempotency-Key")
          @Size(min = 8, max = 100)
          @Pattern(regexp = "[A-Za-z0-9_.-]+")
          String key) {
    return s.pay(actor, id, data.cenario(), key);
  }

  @GetMapping("/pedidos/{id}/pagamentos")
  @Operation(summary = "Listar pagamentos próprios ou da unidade autorizada (JWT)")
  public PageResponse<PaymentResponse> payments(
      @PathVariable long id,
      @AuthenticationPrincipal Actor actor,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return s.payments(actor, id, page, limit);
  }

  @PutMapping("/fidelidade/consentimento")
  @PreAuthorize("hasRole('CLIENTE')")
  @Operation(summary = "Registrar ou revogar consentimento próprio (CLIENTE)")
  public ConsentResponse consent(
      @Valid @RequestBody Consentimento data, @AuthenticationPrincipal Actor actor) {
    return s.consent(actor, data.consentimento());
  }

  @GetMapping("/fidelidade")
  @PreAuthorize("hasRole('CLIENTE')")
  @Operation(summary = "Consultar saldo e histórico próprio (CLIENTE)")
  public LoyaltyResponse loyalty(
      @AuthenticationPrincipal Actor actor,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return s.loyalty(actor, page, limit);
  }

  @GetMapping("/auditoria")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Consultar auditoria (ADMIN)")
  public PageResponse<AuditResponse> audits(
      @AuthenticationPrincipal Actor actor,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
    return s.audits(actor, page, limit);
  }
}
