import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Configuração privada local criada sem sobrescrever arquivo existente. */
class InitLocal {
  public static void main(String[] args) throws Exception {
    Path path = Path.of(".env");
    if (Files.exists(path)) {
      System.out.println(".env existente preservado.");
      return;
    }
    var random = new SecureRandom();
    byte[] jwt = new byte[32], password = new byte[16];
    random.nextBytes(jwt);
    random.nextBytes(password);
    String content =
        "JWT_SECRET="
            + HexFormat.of().formatHex(jwt)
            + "\nTOKEN_TTL_SECONDS=3600\nSEED_ADMIN_PASSWORD=Estudo"
            + HexFormat.of().formatHex(password)
            + "9\nSEED_ENABLED=true\n";
    try {
      Files.createFile(
          path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    } catch (UnsupportedOperationException e) {
      Files.createFile(path);
    }
    Files.writeString(path, content);
    System.out.println(".env privado criado. Nenhum segredo foi exibido.");
  }
}
