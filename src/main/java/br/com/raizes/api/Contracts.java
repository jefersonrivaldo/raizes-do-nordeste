package br.com.raizes.api;

import br.com.raizes.domain.Rules.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public final class Contracts {
  private Contracts() {}

  public record Cadastro(
      @NotBlank @Size(min = 2, max = 120) String nome,
      @NotBlank @Email @Size(max = 254) String email,
      @NotNull @Size(min = 10, max = 72) @Pattern(regexp = "(?s)(?=.*[\\p{L}])(?=.*[0-9]).+")
          String senha) {}

  public record Login(
      @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String senha) {}

  public record Unidade(
      @NotBlank @Size(min = 2, max = 120) String nome,
      @NotBlank @Size(min = 5, max = 250) String endereco,
      Boolean ativo) {}

  public record Produto(
      @NotBlank @Size(min = 2, max = 120) String nome,
      @Size(max = 500) String descricao,
      Boolean ativo) {}

  public record Vinculo(@NotNull Perfil perfil, @Positive Long unidadeId) {}

  public record Estoque(
      @NotNull @Positive Long produtoId,
      @NotNull @Min(-100000) @Max(100000) Long quantidade,
      @Positive @Max(10000000) Long precoCentavos,
      @NotBlank @Size(min = 3, max = 120) String motivo) {}

  public record Item(
      @NotNull @Positive Long produtoId, @NotNull @Min(1) @Max(1000) Long quantidade) {}

  public record Pedido(
      @NotNull @Positive Long unidadeId,
      @NotNull
          @Schema(
              example = "TOTEM",
              description = "Origem obrigatória do pedido: APP, TOTEM, BALCAO, PICKUP ou WEB")
          Canal canalPedido,
      @Positive Long clienteId,
      @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid Item> itens,
      @Pattern(regexp = "MOCK") String formaPagamento,
      @Size(max = 30) String cupom,
      @Min(0) @Max(1000000) Long pontosResgate) {}

  public record Pagamento(@NotNull Scenario cenario) {}

  public record MudancaStatus(@NotNull Status status) {}

  public record Consentimento(@NotNull Boolean consentimento) {}

  public record UserResponse(long id, String nome, Perfil perfil, Long unidadeId) {}

  public record LoginResponse(
      String accessToken, String tokenType, long expiresIn, UserResponse user) {}

  public record ItemResponse(
      long produtoId, String nome, long quantidade, long precoUnitarioCentavos) {}

  public record OrderResponse(
      long id,
      long clienteId,
      long unidadeId,
      Canal canalPedido,
      Status status,
      long subtotalCentavos,
      long descontoCentavos,
      long totalCentavos,
      long pontosResgate,
      String createdAt,
      List<ItemResponse> itens) {}

  public record PaymentResponse(
      long id, long pedidoId, String status, long valorCentavos, Map<String, Object> payload) {}

  public record UnitResponse(long id, String nome, String endereco, boolean active) {}

  public record ProductResponse(long id, String nome, String descricao, boolean active) {}

  public record StockResponse(long produtoId, long quantidade, long precoCentavos) {}

  public record MenuResponse(
      long produtoId, String nome, String descricao, long precoCentavos, long disponivel) {}

  public record PageResponse<T>(List<T> items, int page, int limit) {}

  public record LoyaltyMovement(Long pedidoId, long quantidade, String motivo, String createdAt) {}

  public record LoyaltyResponse(
      long saldo,
      boolean consentimento,
      String consentimentoEm,
      String versao,
      List<LoyaltyMovement> items,
      int page,
      int limit) {}

  public record ConsentResponse(boolean consentimento, String versao) {}

  public record AuditResponse(
      long id, long actor_id, String acao, String recurso, String created_at) {}

  public record ErrorDetail(String field, String issue) {}

  public record ErrorResponse(
      String error,
      String message,
      List<ErrorDetail> details,
      String timestamp,
      String path,
      String requestId) {}
}
