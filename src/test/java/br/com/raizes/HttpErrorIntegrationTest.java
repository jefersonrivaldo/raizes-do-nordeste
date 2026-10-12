package br.com.raizes;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HttpErrorIntegrationTest extends BaseApiTest {
  @Test
  void metodoNaoPermitidoRetorna405EAllow() throws Exception {
    var response = send("POST", "/health", "application/json", "application/json", "{}");

    assertError(response, 405, "METODO_NAO_PERMITIDO", "/health");
    assertTrue(
        Arrays.stream(response.headers().firstValue("Allow").orElseThrow().split(","))
            .map(String::trim)
            .anyMatch("GET"::equals));
  }

  @Test
  void corpoComTipoNaoSuportadoRetorna415ETiposAceitos() throws Exception {
    var response = send("POST", "/auth/login", "text/plain", "application/json", "texto");

    assertError(response, 415, "TIPO_MIDIA_NAO_SUPORTADO", "/auth/login");
    assertTrue(response.headers().firstValue("Accept").orElseThrow().contains("application/json"));
  }

  @Test
  void acceptSemRepresentacaoDisponivelRetorna406ComEnvelopeJson() throws Exception {
    var response = send("GET", "/health", null, "application/xml", null);

    assertError(response, 406, "TIPO_MIDIA_NAO_ACEITAVEL", "/health");
    assertTrue(response.headers().firstValue("Accept").orElseThrow().contains("application/json"));
  }

  @Test
  void getHealthContinuaFuncionando() throws Exception {
    var response = send("GET", "/health", null, "application/json", null);

    assertEquals(200, response.statusCode(), response.body());
    var body = json.readTree(response.body());
    assertEquals("ok", body.get("status").asText());
    assertEquals("ok", body.get("database").asText());
  }

  private HttpResponse<String> send(
      String method, String path, String contentType, String accept, String body) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (contentType != null) request.header("Content-Type", contentType);
    request.header("Accept", accept);
    request.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private void assertError(HttpResponse<String> response, int status, String code, String path)
      throws Exception {
    assertEquals(status, response.statusCode(), response.body());
    assertTrue(
        response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
    var body = json.readTree(response.body());
    assertEquals(6, body.size());
    assertEquals(code, body.get("error").asText());
    assertFalse(body.get("message").asText().isBlank());
    assertTrue(body.get("details").isArray());
    assertTrue(body.get("details").isEmpty());
    assertEquals(path, body.get("path").asText());
    assertDoesNotThrow(() -> Instant.parse(body.get("timestamp").asText()));
    assertDoesNotThrow(() -> UUID.fromString(body.get("requestId").asText()));
    assertEquals(
        response.headers().firstValue("X-Request-ID").orElseThrow(),
        body.get("requestId").asText());
  }
}
