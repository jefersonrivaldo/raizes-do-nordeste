package br.com.raizes.infrastructure;

import br.com.raizes.domain.Rules.Scenario;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MockGateway {
  public Map<String, Object> pay(long order, long amount, Scenario scenario) {
    String status =
        switch (scenario) {
          case APROVAR -> "APROVADO";
          case NEGAR -> "NEGADO";
          case INDISPONIVEL -> "ERRO";
        };
    String message =
        switch (scenario) {
          case APROVAR -> "Pagamento aprovado.";
          case NEGAR -> "Pagamento negado.";
          case INDISPONIVEL -> "Gateway indisponível; tente novamente.";
        };
    return Map.of(
        "gateway",
        "MOCK",
        "referencia",
        "mock-" + order,
        "status",
        status,
        "valorCentavos",
        amount,
        "mensagem",
        message);
  }
}
