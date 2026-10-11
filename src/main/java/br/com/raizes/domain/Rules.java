package br.com.raizes.domain;

import java.util.Map;
import java.util.Set;

public final class Rules {
  private Rules() {}

  public enum Canal {
    APP,
    TOTEM,
    BALCAO,
    PICKUP,
    WEB
  }

  public enum Perfil {
    CLIENTE,
    ATENDENTE,
    COZINHA,
    GERENTE,
    ADMIN
  }

  public enum Status {
    AGUARDANDO_PAGAMENTO,
    EM_PREPARO,
    PRONTO,
    ENTREGUE,
    CANCELADO
  }

  public enum Scenario {
    APROVAR,
    NEGAR,
    INDISPONIVEL
  }

  public static final class BusinessException extends RuntimeException {
    public final String code;
    public final int status;

    public BusinessException(String code, String message, int status) {
      super(message);
      this.code = code;
      this.status = status;
    }
  }

  public static void require(boolean condition, String code, String message, int status) {
    if (!condition) throw new BusinessException(code, message, status);
  }

  public static void require(boolean condition, String code, String message) {
    require(condition, code, message, 409);
  }

  public static void transition(String from, Status to) {
    Map<String, Set<Status>> transitions =
        Map.of(
            "AGUARDANDO_PAGAMENTO",
            Set.of(Status.CANCELADO),
            "EM_PREPARO",
            Set.of(Status.PRONTO, Status.CANCELADO),
            "PRONTO",
            Set.of(Status.ENTREGUE, Status.CANCELADO));
    require(
        transitions.getOrDefault(from, Set.of()).contains(to),
        "TRANSICAO_INVALIDA",
        "Transição de status inválida.");
  }

  public record Totals(long total, long discount) {}

  public static Totals totals(long subtotal, String coupon, long points) {
    if (coupon != null)
      require(
          coupon.equals("NORDESTE10") && subtotal >= 5000,
          "CUPOM_INVALIDO",
          "NORDESTE10 exige subtotal de pelo menos 5000 centavos.");
    require(points % 100 == 0, "RESGATE_INVALIDO", "Resgate deve ser múltiplo de 100 pontos.");
    long discount = coupon == null ? 0 : subtotal / 10;
    require(
        points * 10 <= subtotal - discount,
        "RESGATE_INVALIDO",
        "Resgate excede o valor do pedido.");
    return new Totals(subtotal - discount - points * 10, discount + points * 10);
  }
}
