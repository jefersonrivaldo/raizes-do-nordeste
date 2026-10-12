package br.com.raizes;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentationTest extends BaseApiTest {
  @TempDir Path temporary;

  @Test
  void exemplosDeClienteSaoAceitosPelaApiESemVinculoComUnidade() throws Exception {
    var schemas = call("GET", "/openapi.json", null, null, 200).path("components").path("schemas");
    var cadastro = schemas.path("Cadastro").path("example");
    var registered = call("POST", "/usuarios", cadastro, null, 201);
    var userExample = schemas.path("UserResponse").path("example");
    assertEquals(userExample.path("nome"), registered.path("nome"));
    assertEquals(userExample.path("perfil"), registered.path("perfil"));
    assertTrue(userExample.path("unidadeId").isNull());
    assertEquals(userExample.path("unidadeId"), registered.path("unidadeId"));

    var loggedIn = call("POST", "/auth/login", schemas.path("Login").path("example"), null, 200);
    assertEquals(registered, loggedIn.path("user"));
    long id = registered.path("id").asLong();
    call(
        "PUT",
        "/usuarios/" + id + "/perfil",
        Map.of("perfil", "ATENDENTE", "unidadeId", 1),
        admin,
        200);
    var updated =
        call(
            "PUT",
            "/usuarios/" + id + "/perfil",
            schemas.path("Vinculo").path("example"),
            admin,
            200);
    assertEquals(registered, updated);
  }

  @Test
  void apiOnlyGeraContratosSemRelatorioDiagramasOuPdf() throws Exception {
    var docs = Files.createDirectory(temporary.resolve("docs"));
    var options =
        Documentation.options(new String[] {"http://127.0.0.1:" + port, "--api-only"});
    assertTrue(options.apiOnly());
    Documentation.contracts(options, docs);
    try (var files = Files.list(docs)) {
      assertEquals(
          List.of("api.md", "openapi.json"),
          files.map(p -> p.getFileName().toString()).sorted().toList());
    }
    var api = json.readTree(docs.resolve("openapi.json").toFile());
    assertEquals(
        call("GET", "/openapi.json", null, null, 200),
        api,
        "O contrato salvo deve corresponder à API em execução.");
    var markdown = Files.readString(docs.resolve("api.md"));
    var examples = Pattern.compile("```json\\n(.*?)\\n```", Pattern.DOTALL).matcher(markdown);
    int count = 0;
    while (examples.find()) {
      assertValidProfiles(json.readTree(examples.group(1)));
      count++;
    }
    assertTrue(count > 0);
    assertTrue(markdown.contains("payload preserva a resposta original do gateway mock"));
    assertTrue(markdown.contains("HTTP 429 com Retry-After"));
    for (String path : List.of("/auth/login", "/usuarios"))
      assertEquals(
          "integer",
          api.path("paths")
              .path(path)
              .path("post")
              .path("responses")
              .path("429")
              .path("headers")
              .path("Retry-After")
              .path("schema")
              .path("type")
              .asText());
    assertFalse(api.path("paths").path("/health").path("get").path("responses").has("429"));
  }

  @Test
  void modoCompletoSemRelatorioFalhaAntesDeAlterarArquivos() throws Exception {
    var docs = Files.createDirectory(temporary.resolve("docs"));
    Files.writeString(docs.resolve("api.md"), "contratos preservados");
    Files.writeString(docs.resolve("openapi.json"), "openapi preservado");
    var options = Documentation.options(new String[] {"http://127.0.0.1:1"});
    var error =
        assertThrows(IllegalStateException.class, () -> Documentation.contracts(options, docs));
    assertTrue(error.getMessage().contains("relatorio.md"));
    assertTrue(error.getMessage().contains("--api-only"));
    assertEquals("contratos preservados", Files.readString(docs.resolve("api.md")));
    assertEquals("openapi preservado", Files.readString(docs.resolve("openapi.json")));
  }

  private void assertValidProfiles(JsonNode node) {
    if (node.isObject()) {
      String profile = node.path("perfil").asText();
      if (profile.equals("CLIENTE") || profile.equals("ADMIN"))
        assertTrue(node.path("unidadeId").isNull(), "Perfil sem vínculo: " + node);
    }
    node.elements().forEachRemaining(this::assertValidProfiles);
  }
}
