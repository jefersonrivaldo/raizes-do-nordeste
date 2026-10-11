import java.net.URI;
import java.nio.file.*;

/** Usa o proxy já injetado no ambiente, sem imprimir valores ou desativar TLS. */
class MavenProxy {
  public static void main(String[] args) throws Exception {
    String proxy = System.getenv("HTTPS_PROXY");
    if (proxy == null) proxy = System.getenv("https_proxy");
    String body = "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.2.0\">";
    if (proxy != null && !proxy.isBlank()) {
      URI uri = URI.create(proxy);
      if (uri.getUserInfo() != null)
        throw new IllegalStateException(
            "Proxy autenticado requer configuração suportada separada.");
      String host = uri.getHost();
      int port = uri.getPort() < 0 ? 80 : uri.getPort();
      if (host == null || !host.matches("[A-Za-z0-9.-]+"))
        throw new IllegalArgumentException("Host de proxy inválido.");
      body +=
          "<proxies><proxy><id>environment</id><active>true</active><protocol>http</protocol><host>"
              + host
              + "</host><port>"
              + port
              + "</port><nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy></proxies>";
    }
    body += "</settings>";
    Path path = Path.of(".m2/settings-proxy.xml");
    Files.createDirectories(path.getParent());
    Files.writeString(path, body);
  }
}
