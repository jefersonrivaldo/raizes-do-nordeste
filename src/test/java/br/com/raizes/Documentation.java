package br.com.raizes;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

/** Geração de contratos, DER e PDF inteiramente em Java. Execute após iniciar a API. */
public class Documentation {
  static ObjectMapper json = new ObjectMapper();
  static JsonNode schemas;

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
    String base = args.length == 0 ? "http://127.0.0.1:8000" : args[0];
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(base + "/openapi.json")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) throw new IllegalStateException("API/OpenAPI indisponível");
    var api = json.readTree(response.body());
    schemas = api.get("components").get("schemas");
    Files.writeString(
        Path.of("docs/openapi.json"),
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
                                      var s =
                                          r.getValue()
                                              .path("content")
                                              .path("application/json")
                                              .get("schema");
                                      if (s != null) appendExample(text, example(s));
                                      else text.append("Sem corpo.\n");
                                    }
                                  });
                          text.append(
                              "\n"
                                  + "Erros: 401 autenticação, 403 autorização, 404 inexistente, 409"
                                  + " regra/integridade, 422 validação, 503 banco ocupado; JSON"
                                  + " ErrorResponse uniforme. Nem todos os erros se aplicam a toda"
                                  + " rota.\n\n");
                        }));
    text.append(
        "## Padrão de erro\n\n"
            + "```json\n"
            + "{\"error\":\"ESTOQUE_INSUFICIENTE\",\"message\":\"Estoque insuficiente na"
            + " unidade.\",\"details\":[],\"timestamp\":\"2026-10-11T15:00:00Z\",\"path\":\"/pedidos\",\"requestId\":\"uuid\"}\n"
            + "```\n\n"
            + "HTTP 201 em pagamento significa tentativa persistida; examine status"
            + " APROVADO/NEGADO/ERRO. Erros de validação omitem os valores enviados.\n");
    Files.writeString(Path.of("docs/api.md"), text);
    diagram();
    pdf();
    System.out.println("Contratos, DER e relatório PDF gerados em Java.");
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
    for (String name : List.of("der", "casos-de-uso", "classes", "arquitetura"))
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
    try (PDDocument document = new PDDocument()) {
      PDType0Font font =
          PDType0Font.load(
              document, Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf").toFile());
      PDPage page = null;
      PDPageContentStream stream = null;
      float y = 0;
      int pageNo = 0;
      for (String original : Files.readAllLines(Path.of("docs/relatorio.md"))) {
        String line = original.replace("`", "");
        boolean heading = line.startsWith("#");
        line = line.replaceAll("^#+\\s*", "");
        if (line.isBlank()) line = " ";
        if (line.matches("\\|[|: -]+\\|")) continue;
        float size = heading ? 13 : 9.5f;
        float maxWidth = PDRectangle.A4.getWidth() - 110;
        StringBuilder current = new StringBuilder();
        List<String> wrapped = new ArrayList<>();
        for (String word : line.split(" ")) {
          String next = current.isEmpty() ? word : current + " " + word;
          float width = font.getStringWidth(next) / 1000 * size;
          if (width > maxWidth && !current.isEmpty()) {
            wrapped.add(current.toString());
            current = new StringBuilder(word);
          } else current = new StringBuilder(next);
        }
        wrapped.add(current.toString());
        for (String part : wrapped) {
          if (page == null || y < 65) {
            if (stream != null) stream.close();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = 780;
            pageNo++;
            stream.beginText();
            stream.setFont(font, 8);
            stream.newLineAtOffset(55, 35);
            stream.showText("Raízes do Nordeste — Java | " + pageNo);
            stream.endText();
          }
          stream.beginText();
          stream.setFont(font, size);
          stream.newLineAtOffset(55, y);
          stream.showText(part);
          stream.endText();
          y -= heading ? 21 : 15;
        }
        if (heading) y -= 5;
      }
      if (stream != null) stream.close();
      for (String name : List.of("casos-de-uso", "classes", "arquitetura", "der")) {
        var imagePage = new PDPage(PDRectangle.A4);
        document.addPage(imagePage);
        var image =
            LosslessFactory.createFromImage(
                document, ImageIO.read(Path.of("docs/diagramas/" + name + ".png").toFile()));
        float scale = Math.min(485f / image.getWidth(), 670f / image.getHeight());
        try (var content = new PDPageContentStream(document, imagePage)) {
          content.beginText();
          content.setFont(font, 15);
          content.newLineAtOffset(55, 785);
          content.showText("Diagrama — " + name);
          content.endText();
          content.drawImage(image, 55, 65, image.getWidth() * scale, image.getHeight() * scale);
        }
      }
      document.getDocumentInformation().setTitle("Raízes do Nordeste — relatório técnico Java");
      document.save("docs/relatorio-tecnico.pdf");
    }
    try (PDDocument document = new PDDocument()) {
      var page = new PDPage(new PDRectangle(1190.55f, 841.89f));
      document.addPage(page);
      var image =
          LosslessFactory.createFromImage(
              document, ImageIO.read(Path.of("docs/diagramas/der.png").toFile()));
      float scale = Math.min(1120f / image.getWidth(), 770f / image.getHeight());
      try (var content = new PDPageContentStream(document, page)) {
        content.drawImage(image, 35, 35, image.getWidth() * scale, image.getHeight() * scale);
      }
      document.save("docs/diagramas/der.pdf");
    }
  }
}
