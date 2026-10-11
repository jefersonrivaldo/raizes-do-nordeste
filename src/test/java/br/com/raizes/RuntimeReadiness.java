package br.com.raizes;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** Validação manual da instância persistente, sem exibir credenciais. */
public class RuntimeReadiness {
  static ObjectMapper json = new ObjectMapper();
  static HttpClient http = HttpClient.newHttpClient();
  static String token;

  static JsonNode call(String method, String path, Object body, int expected) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:8000" + path))
            .header("Content-Type", "application/json");
    if (token != null) b.header("Authorization", "Bearer " + token);
    if (method.equals("POST") && !path.equals("/auth/login"))
      b.header("Idempotency-Key", "readiness-" + UUID.randomUUID());
    var r =
        http.send(
            b.method(
                    method,
                    body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    if (r.statusCode() != expected)
      throw new IllegalStateException("Status inesperado em " + path + ": " + r.statusCode());
    return r.body().isEmpty() ? json.nullNode() : json.readTree(r.body());
  }

  public static void main(String[] args) throws Exception {
    Map<String, String> local = new HashMap<>();
    for (String line : Files.readAllLines(Path.of(".env"))) {
      int i = line.indexOf('=');
      if (i > 0) local.put(line.substring(0, i), line.substring(i + 1));
    }
    if (!call("GET", "/health", null, 200).get("database").asText().equals("ok"))
      throw new IllegalStateException("Banco indisponível");
    token =
        call(
                "POST",
                "/auth/login",
                Map.of("email", "admin@raizes.local", "senha", local.get("SEED_ADMIN_PASSWORD")),
                200)
            .get("accessToken")
            .asText();
    if (args.length > 0 && args[0].equals("before")) {
      String email = "runtime-" + UUID.randomUUID() + "@example.com";
      long customer =
          call(
                  "POST",
                  "/usuarios",
                  Map.of(
                      "nome", "Cliente de validação", "email", email, "senha", "ClienteTeste123!"),
                  201)
              .get("id")
              .asLong();
      JsonNode order =
          call(
              "POST",
              "/pedidos",
              Map.of(
                  "clienteId",
                  customer,
                  "unidadeId",
                  1,
                  "canalPedido",
                  "WEB",
                  "itens",
                  List.of(Map.of("produtoId", 1, "quantidade", 1))),
              201);
      call(
          "POST",
          "/pedidos/" + order.get("id").asLong() + "/pagamentos",
          Map.of("cenario", "APROVAR"),
          201);
      call(
          "PATCH",
          "/pedidos/" + order.get("id").asLong() + "/status",
          Map.of("status", "PRONTO"),
          200);
      JsonNode delivered =
          call(
              "PATCH",
              "/pedidos/" + order.get("id").asLong() + "/status",
              Map.of("status", "ENTREGUE"),
              200);
      Files.writeString(Path.of("/tmp/raizes-java-before-restart.json"), delivered.toString());
      System.out.println("Fluxo Java persistente concluído antes do reinício.");
    } else {
      JsonNode before =
          json.readTree(Files.readString(Path.of("/tmp/raizes-java-before-restart.json")));
      JsonNode after = call("GET", "/pedidos/" + before.get("id").asLong(), null, 200);
      if (!before.equals(after)) throw new IllegalStateException("Pedido divergiu após reinício.");
      JsonNode actual = call("GET", "/openapi.json", null, 200),
          saved = json.readTree(Files.readString(Path.of("docs/openapi.json")));
      if (!actual.get("paths").equals(saved.get("paths"))
          || !actual.get("components").equals(saved.get("components")))
        throw new IllegalStateException("Contrato salvo divergiu da API.");
      var docs =
          http.send(
              HttpRequest.newBuilder(URI.create("http://127.0.0.1:8000/docs")).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      if (docs.statusCode() != 302 && docs.statusCode() != 200)
        throw new IllegalStateException("Swagger não serviu");
      Files.writeString(
          Path.of("docs/evidencias-runtime-java.json"),
          json.writerWithDefaultPrettyPrinter()
                  .writeValueAsString(
                      Map.of(
                          "executadoEm",
                          java.time.Instant.now().toString(),
                          "tecnologia",
                          "Java 21 / Spring Boot / H2 / Flyway",
                          "health",
                          true,
                          "swagger",
                          true,
                          "openapiCompatível",
                          true,
                          "persistenciaAposReinicio",
                          true,
                          "pedidoId",
                          before.get("id").asLong(),
                          "status",
                          after.get("status").asText()))
              + "\n");
      evidence();
      System.out.println("API Java, contrato, Swagger e persistência após reinício: aprovados.");
    }
  }

  static void evidence() throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    for (String name : List.of("ApiIntegrationTest", "CollectionContractTest")) {
      var document =
          factory
              .newDocumentBuilder()
              .parse(
                  Path.of("target/surefire-reports/TEST-br.com.raizes." + name + ".xml").toFile());
      for (String tag : List.of("properties", "system-out", "system-err")) {
        var list = document.getElementsByTagName(tag);
        while (list.getLength() > 0) {
          var node = list.item(0);
          node.getParentNode().removeChild(node);
        }
      }
      String output = name.equals("ApiIntegrationTest") ? "api" : "colecao";
      var transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.transform(
          new DOMSource(document), new StreamResult("docs/evidencias-junit-" + output + ".xml"));
    }
  }
}
