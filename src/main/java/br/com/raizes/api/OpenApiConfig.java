package br.com.raizes.api;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.headers.Header;
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
                            List<Integer> errors =
                                new ArrayList<>(List.of(401, 403, 404, 405, 406, 409, 415, 422, 503));
                            if (method.name().equals("POST")
                                && (path.equals("/auth/login") || path.equals("/usuarios")))
                              errors.add(429);
                            for (int code : errors) {
                              var response =
                                  new ApiResponse()
                                      .description(
                                          switch (code) {
                                            case 401 -> "Token ausente/inválido";
                                            case 403 -> "Sem permissão";
                                            case 404 -> "Recurso não encontrado";
                                            case 405 -> "Método HTTP não permitido";
                                            case 406 -> "Representação não aceitável";
                                            case 409 -> "Conflito/regra de negócio";
                                            case 415 -> "Tipo de conteúdo não suportado";
                                            case 422 -> "Validação";
                                            case 429 -> "Limite de tentativas excedido";
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
                                                                  "#/components/schemas/ErrorResponse"))));
                              if (code == 429)
                                response.addHeaderObject(
                                    "Retry-After",
                                    new Header()
                                        .description("Segundos até uma nova tentativa ser permitida")
                                        .schema(new IntegerSchema()));
                              op.getResponses()
                                  .addApiResponse(Integer.toString(code), response);
                            }
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
      Map<String, Object> clientProfile = new LinkedHashMap<>();
      clientProfile.put("perfil", "CLIENTE");
      clientProfile.put("unidadeId", null);
      var profileSchema = api.getComponents().getSchemas().get("Vinculo");
      if (profileSchema != null) {
        profileSchema.setExample(clientProfile);
        profileSchema.setDescription(
            "ATENDENTE, COZINHA e GERENTE exigem unidadeId; CLIENTE e ADMIN exigem unidadeId null.");
      }
      var userSchema = api.getComponents().getSchemas().get("UserResponse");
      if (userSchema != null) {
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("id", 2);
        client.put("nome", "Maria de estudo");
        client.putAll(clientProfile);
        userSchema.setExample(client);
      }
      Schema<?> paymentSchema = api.getComponents().getSchemas().get("PaymentResponse");
      if (paymentSchema != null) {
        paymentSchema
            .getProperties()
            .get("status")
            .setDescription("Estado atual da tentativa de pagamento, inclusive ESTORNADO.");
        paymentSchema
            .getProperties()
            .get("payload")
            .setDescription(
                "Resposta original do gateway mock; preservada quando o status muda para ESTORNADO.");
      }
    };
  }
}
