package br.com.raizes;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Executa as 23 requisições da coleção usando Java, sem depender de Node/Python. */
class CollectionContractTest extends BaseApiTest {
  @Test
  void collectionInteiraComResultadosEsperados() throws Exception {
    var collection = json.readTree(Files.readString(Path.of("docs/postman_collection.json")));
    var vars = new HashMap<String, String>();
    vars.put("baseUrl", "");
    vars.put("adminPassword", "AdminTeste12345");
    vars.put("clientPassword", "ClienteEstudo123!");
    vars.put("runId", UUID.randomUUID().toString());
    vars.put("clientEmail", "cliente-" + vars.get("runId") + "@example.com");
    int requests = 0, assertions = 0;
    for (JsonNode item : requestsInOrder(collection.get("item"))) {
      String name = item.get("name").asText();
      String id = name.substring(0, 3);
      var request = item.get("request");
      String code = item.get("event").get(0).get("script").get("exec").get(0).asText();
      var matcher = Pattern.compile("HTTP (\\d+)").matcher(code);
      assertTrue(matcher.find());
      int expected = Integer.parseInt(matcher.group(1));
      String token = null, key = null;
      for (JsonNode header : request.get("header")) {
        String value = substitute(header.get("value").asText(), vars);
        if (header.get("key").asText().equals("Authorization")) token = value.substring(7);
        if (header.get("key").asText().equals("Idempotency-Key")) key = value;
      }
      Object body =
          request.has("body")
              ? json.readValue(substitute(request.get("body").get("raw").asText(), vars), Map.class)
              : null;
      JsonNode response =
          call(
              request.get("method").asText(),
              substitute(request.get("url").asText(), vars),
              body,
              token,
              expected,
              key);
      requests++;
      assertions++;
      switch (id) {
        case "T01" -> assertEquals("CLIENTE", response.get("perfil").asText());
        case "T02" -> {
          assertTrue(response.get("accessToken").asText().split("\\.").length == 3);
          vars.put("token", response.get("accessToken").asText());
        }
        case "T03" -> {
          assertEquals("ADMIN", response.get("user").get("perfil").asText());
          vars.put("adminToken", response.get("accessToken").asText());
        }
        case "T04" -> {
          assertEquals(1, response.get("items").get(0).get("produtoId").asInt());
          assertEquals(2500, response.get("items").get(0).get("precoCentavos").asInt());
        }
        case "T05" -> assertTrue(response.get("consentimento").asBoolean());
        case "T06" -> {
          assertEquals("AGUARDANDO_PAGAMENTO", response.get("status").asText());
          assertEquals(5000, response.get("totalCentavos").asInt());
          assertEquals("TOTEM", response.get("canalPedido").asText());
          vars.put("pedidoId", response.get("id").asText());
        }
        case "T07" -> assertEquals(vars.get("pedidoId"), response.get("id").asText());
        case "T08" -> assertEquals("NEGADO", response.get("status").asText());
        case "T09" -> assertEquals("APROVADO", response.get("status").asText());
        case "T10" -> assertEquals("PRONTO", response.get("status").asText());
        case "T11" -> assertEquals("ENTREGUE", response.get("status").asText());
        case "T12" -> assertEquals(50, response.get("saldo").asInt());
        case "T13" -> {
          boolean found = false;
          for (JsonNode order : response.get("items"))
            if (order.get("id").asText().equals(vars.get("pedidoId"))
                && order.get("canalPedido").asText().equals("TOTEM")) found = true;
          assertTrue(found);
        }
        case "T23" -> {
          boolean found = false;
          for (JsonNode event : response.get("items"))
            if (event.path("acao").asText().equals("PEDIDO_CRIADO")) found = true;
          assertTrue(found, "Criação de pedido deve gerar auditoria");
        }
        default -> {
          assertEquals(6, response.size());
          for (String field :
              List.of("error", "message", "details", "timestamp", "path", "requestId"))
            assertTrue(response.has(field));
        }
      }
      assertions++;
    }
    assertEquals(23, requests);
    assertEquals(46, assertions);
    Files.writeString(
        Path.of("target/evidencias-colecao-java.json"),
        json.writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    Map.of(
                        "executadoEm",
                        java.time.Instant.now().toString(),
                        "executor",
                        "Java 21 / JUnit 5 / HttpClient",
                        "requisicoes",
                        requests,
                        "gruposDeAssercoes",
                        assertions,
                        "falhas",
                        0))
            + "\n");
  }

  private List<JsonNode> requestsInOrder(JsonNode items) {
    var requests = new ArrayList<JsonNode>();
    for (JsonNode item : items) {
      if (item.has("item")) requests.addAll(requestsInOrder(item.get("item")));
      else requests.add(item);
    }
    return requests;
  }

  private String substitute(String text, Map<String, String> vars) {
    for (var entry : vars.entrySet())
      text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
    return text;
  }
}
