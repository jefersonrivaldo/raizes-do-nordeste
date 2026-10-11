package br.com.raizes.api;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Raízes do Nordeste",
            version = "2.0.0-java",
            description =
                "API 100% Java. JWT e perfis, canalPedido obrigatório, estoque por unidade, valores"
                    + " em centavos e pagamento exclusivamente mock."))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfig {
  @Bean
  OpenApiCustomizer customize() {
    return api -> {
      ModelConverters.getInstance()
          .read(Contracts.ErrorResponse.class)
          .forEach((name, schema) -> api.getComponents().addSchemas(name, schema));
      api.getPaths()
          .forEach(
              (path, item) ->
                  item.readOperationsMap()
                      .forEach(
                          (method, op) -> {
                            boolean publicRoute =
                                path.equals("/health")
                                    || path.equals("/auth/login")
                                    || (path.equals("/usuarios") && method.name().equals("POST"))
                                    || (method.name().equals("GET")
                                        && (path.equals("/unidades")
                                            || path.equals("/produtos")
                                            || path.endsWith("/cardapio")));
                            op.setSecurity(
                                publicRoute
                                    ? List.of()
                                    : List.of(new SecurityRequirement().addList("bearerAuth")));
                            for (int code : new int[] {401, 403, 404, 409, 422, 503})
                              op.getResponses()
                                  .addApiResponse(
                                      Integer.toString(code),
                                      new ApiResponse()
                                          .description(
                                              switch (code) {
                                                case 401 -> "Token ausente/inválido";
                                                case 403 -> "Sem permissão";
                                                case 404 -> "Recurso não encontrado";
                                                case 409 -> "Conflito/regra de negócio";
                                                case 422 -> "Validação";
                                                default -> "Banco ocupado";
                                              })
                                          .content(
                                              new Content()
                                                  .addMediaType(
                                                      "application/json",
                                                      new MediaType()
                                                          .schema(
                                                              new Schema<>()
                                                                  .$ref(
                                                                      "#/components/schemas/ErrorResponse")))));
                          }));
      Map<String, Object> request =
          Map.of(
              "unidadeId",
              1,
              "canalPedido",
              "TOTEM",
              "itens",
              List.of(Map.of("produtoId", 1, "quantidade", 2)),
              "formaPagamento",
              "MOCK");
      if (api.getComponents().getSchemas().get("Pedido") != null)
        api.getComponents().getSchemas().get("Pedido").setExample(request);
      if (api.getComponents().getSchemas().get("Cadastro") != null)
        api.getComponents()
            .getSchemas()
            .get("Cadastro")
            .setExample(
                Map.of(
                    "nome",
                    "Maria de estudo",
                    "email",
                    "maria@example.com",
                    "senha",
                    "SenhaEstudo123!"));
      if (api.getComponents().getSchemas().get("Login") != null)
        api.getComponents()
            .getSchemas()
            .get("Login")
            .setExample(Map.of("email", "maria@example.com", "senha", "SenhaEstudo123!"));
    };
  }
}
