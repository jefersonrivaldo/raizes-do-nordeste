package br.com.raizes;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Geração de contratos, diagramas e PDF da fonte revisada; --api-only gera apenas contratos. */
public class Documentation {
  static ObjectMapper json = new ObjectMapper();
  static JsonNode schemas;

  record Options(String base, boolean apiOnly) {}

  static Options options(String[] args) {
    String base = null;
    boolean apiOnly = false;
    for (String arg : args) {
      if (arg.equals("--api-only")) apiOnly = true;
      else if (arg.startsWith("--") || base != null)
        throw new IllegalArgumentException("Uso: Documentation [URL-base] [--api-only]");
      else base = arg;
    }
    return new Options(base == null ? "http://127.0.0.1:8000" : base, apiOnly);
  }

  static JsonNode example(JsonNode s) {
    if (s.has("$ref"))
      return example(schemas.get(s.get("$ref").asText().replace("#/components/schemas/", "")));
    if (s.has("example")) return s.get("example");
    if (s.has("enum")) return s.get("enum").get(0);
    if (s.has("default")) return s.get("default");
    if (s.has("anyOf")) return example(s.get("anyOf").get(0));
    String type = s.path("type").asText();
    if (type.equals("object") || s.has("properties")) {
      var out = json.createObjectNode();
      s.path("properties")
          .fields()
          .forEachRemaining(p -> out.set(p.getKey(), example(p.getValue())));
      return out;
    }
    if (type.equals("array")) return json.createArrayNode().add(example(s.get("items")));
    if (type.equals("integer") || type.equals("number")) return json.getNodeFactory().numberNode(1);
    if (type.equals("boolean")) return BooleanNode.TRUE;
    return TextNode.valueOf("texto");
  }

  public static void main(String[] args) throws Exception {
    var options = options(args);
    contracts(options, Path.of("docs"));
    if (options.apiOnly()) {
      System.out.println("Contratos OpenAPI e Markdown gerados em Java.");
      return;
    }
    diagram();
    pdf();
    System.out.println("Contratos, cinco diagramas e PDF da fonte revisada gerados em Java.");
  }

  static void contracts(Options options, Path docs) throws Exception {
    if (!options.apiOnly() && !Files.isRegularFile(docs.resolve("relatorio.md")))
      throw new IllegalStateException(
          "Relatório obrigatório ausente: "
              + docs.resolve("relatorio.md")
              + ". Use --api-only para gerar somente os contratos da API.");
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(options.base() + "/openapi.json")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) throw new IllegalStateException("API/OpenAPI indisponível");
    var api = json.readTree(response.body());
    schemas = api.get("components").get("schemas");
    Files.writeString(
        docs.resolve("openapi.json"),
        json.writerWithDefaultPrettyPrinter().writeValueAsString(api) + "\n");
    var text =
        new StringBuilder(
            "# Contratos da API Java\n\n"
                + "Gerado do OpenAPI real do Spring Boot. Exemplos são ilustrativos. Valores em"
                + " centavos; listagens retornam items/page/limit (limit até 100). Idempotency-Key"
                + " tem 8–100 caracteres alfanuméricos, ponto, hífen ou sublinhado. Equipe informa"
                + " clienteId na criação de pedido. Reenvio idempotente retorna estado atual.\n\n");
    api.get("paths")
        .fields()
        .forEachRemaining(
            path ->
                path.getValue()
                    .fields()
                    .forEachRemaining(
                        operation -> {
                          JsonNode op = operation.getValue();
                          if (!op.has("responses")) return;
                          text.append("## ")
                              .append(operation.getKey().toUpperCase())
                              .append(" ")
                              .append(path.getKey())
                              .append("\n\n")
                              .append(op.path("summary").asText())
                              .append("\n\n")
                              .append(op.path("description").asText())
                              .append("\n\nAutenticação: ")
                              .append(
                                  op.path("security").isEmpty()
                                      ? "público"
                                      : "JWT Bearer; perfil/unidade conforme resumo")
                              .append(
                                  ".\n\n"
                                      + "Parâmetros:\n\n"
                                      + "| Nome | Local | Obrigatório | Contrato |\n"
                                      + "|---|---|---|---|\n");
                          for (var p : op.path("parameters"))
                            text.append("| ")
                                .append(p.path("name").asText())
                                .append(" | ")
                                .append(p.path("in").asText())
                                .append(" | ")
                                .append(p.path("required").asBoolean())
                                .append(" | `")
                                .append(p.path("schema"))
                                .append("` |\n");
                          JsonNode body =
                              op.path("requestBody")
                                  .path("content")
                                  .path("application/json")
                                  .get("schema");
                          text.append("\nRequest:\n\n");
                          if (body == null) text.append("Sem corpo.\n");
                          else appendExample(text, example(body));
                          op.get("responses")
                              .fields()
                              .forEachRemaining(
                                  r -> {
                                    if (r.getKey().startsWith("2")) {
                                      text.append("\nResponse HTTP ")
                                          .append(r.getKey())
                                          .append(":\n\n");
                                      var media = r.getValue().path("content");
                                      var s = media.path("application/json").get("schema");
                                      if (s == null && media.isObject() && !media.isEmpty())
                                        s = media.elements().next().get("schema");
                                      if (s != null) appendExample(text, example(s));
                                      else text.append("Sem corpo.\n");
                                    }
                                  });
                          text.append(
                              "\n"
                                  + "Erros: 401 autenticação, 403 autorização, 404 inexistente, 405"
                                  + " método HTTP, 406 representação, 409 regra/integridade, 415 tipo"
                                  + " de conteúdo, 422 validação, 503 banco ocupado; JSON"
                                  + " ErrorResponse uniforme. Nem todos os erros se aplicam a toda"
                                  + " rota.\n\n");
                          if (op.path("responses").has("429"))
                            text.append(
                                "Limite de tentativas: HTTP 429 com Retry-After indicando os segundos"
                                    + " até uma nova tentativa.\n\n");
                        }));
    text.append(
        "## Padrão de erro\n\n"
            + "```json\n"
            + "{\"error\":\"ESTOQUE_INSUFICIENTE\",\"message\":\"Estoque insuficiente na"
            + " unidade.\",\"details\":[],\"timestamp\":\"2026-10-11T15:00:00Z\",\"path\":\"/pedidos\",\"requestId\":\"uuid\"}\n"
            + "```\n\n"
            + "HTTP 201 em pagamento significa tentativa persistida; examine status"
            + " APROVADO/NEGADO/ERRO. O campo status representa o estado atual e pode mudar para"
            + " ESTORNADO após cancelamento. payload preserva a resposta original do gateway mock,"
            + " mesmo após o estorno. Erros de validação omitem os valores enviados.\n");
    Files.writeString(docs.resolve("api.md"), text);
  }

  static void appendExample(StringBuilder out, JsonNode node) {
    try {
      out.append("```json\n")
          .append(json.writerWithDefaultPrettyPrinter().writeValueAsString(node))
          .append("\n```\n");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static void diagram() throws Exception {
    String sql = Files.readString(Path.of("src/main/resources/db/migration/V1__initial.sql"));
    var matcher = Pattern.compile("CREATE TABLE (\\w+) \\((.*?)\\);", Pattern.DOTALL).matcher(sql);
    StringBuilder dot =
        new StringBuilder(
            "digraph ER { graph [rankdir=TB,bgcolor=\"white\"];node [shape=record,fontname=\"DejaVu"
                + " Sans\",fontsize=10];edge [fontname=\"DejaVu Sans\",fontsize=9];\n");
    List<String> edges = new ArrayList<>();
    while (matcher.find()) {
      String table = matcher.group(1), body = matcher.group(2);
      List<String> entries = new ArrayList<>();
      int depth = 0, last = 0;
      for (int i = 0; i < body.length(); i++) {
        if (body.charAt(i) == '(') depth++;
        if (body.charAt(i) == ')') depth--;
        if (body.charAt(i) == ',' && depth == 0) {
          entries.add(body.substring(last, i).trim());
          last = i + 1;
        }
      }
      entries.add(body.substring(last).trim());
      dot.append(table).append(" [label=\"{").append(table).append("|");
      for (String entry : entries) {
        if (entry.matches("^(PRIMARY|FOREIGN|UNIQUE|CHECK).*")) continue;
        String name = entry.split("\\s+")[0];
        if (entry.split("\\s+").length < 2)
          throw new IllegalStateException("Entrada DER inválida: " + table + " -> " + entry);
        String type = entry.split("\\s+")[1];
        boolean pk =
            entry.contains("PRIMARY KEY")
                || Pattern.compile("PRIMARY KEY\\([^)]*\\b" + name + "\\b").matcher(body).find();
        boolean fk =
            entry.contains("REFERENCES")
                || Pattern.compile("FOREIGN KEY\\([^)]*\\b" + name + "\\b").matcher(body).find();
        dot.append(name)
            .append(" : ")
            .append(type)
            .append(pk ? " PK" : "")
            .append(fk ? " FK" : "")
            .append(entry.contains("NOT NULL") ? " NOT NULL" : "")
            .append("\\l");
        var ref = Pattern.compile("REFERENCES (\\w+)\\((\\w+)\\)").matcher(entry);
        if (ref.find())
          edges.add(
              table
                  + " -> "
                  + ref.group(1)
                  + " [label=\""
                  + name
                  + " -> "
                  + ref.group(2)
                  + " (N:1)\"];");
      }
      var composite =
          Pattern.compile("FOREIGN KEY\\(([^)]+)\\) REFERENCES (\\w+)\\(([^)]+)\\)").matcher(body);
      while (composite.find())
        edges.add(
            table + " -> " + composite.group(2) + " [label=\"" + composite.group(1) + " (N:1)\"];");
      dot.append("}\"];\n");
    }
    for (String edge : edges) dot.append(edge).append('\n');
    dot.append('}');
    Files.writeString(Path.of("docs/diagramas/der.dot"), dot);
    for (String name : List.of("der", "casos-de-uso", "classes", "arquitetura", "sequencia"))
      for (String format : List.of("svg", "png")) {
        var pb =
            new ProcessBuilder(
                "dot",
                "-T" + format,
                "docs/diagramas/" + name + ".dot",
                "-o",
                "docs/diagramas/" + name + "." + format);
        pb.environment().put("XDG_CACHE_HOME", "/tmp/raizes-font-cache");
        if (pb.inheritIO().start().waitFor() != 0)
          throw new IllegalStateException("Graphviz falhou");
      }
  }

  static void pdf() throws Exception {
    AcademicReport.generate();
  }
}
