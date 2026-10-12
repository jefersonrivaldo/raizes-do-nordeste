package br.com.raizes;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseMigrationTest {
  @TempDir Path directory;

  @Test
  void reabrirBancoPersistentePreservaDadosENaoReaplicaMigracao() throws Exception {
    String url = "jdbc:h2:file:" + directory.resolve("migration").toAbsolutePath()
        + ";DB_CLOSE_ON_EXIT=FALSE";
    // Simula um banco já existente na versão anterior, antes da atualização.
    Flyway first = Flyway.configure().dataSource(url, "sa", "").target("1").load();
    assertEquals(1, first.migrate().migrationsExecuted);
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate("INSERT INTO units(nome,endereco) VALUES('Unidade preservada','Endereço de teste')");
    }
    // Fechar a conexão e criar outra instância Flyway testa o arquivo existente.
    Flyway reopened = Flyway.configure().dataSource(url, "sa", "").load();
    // migrate valida os checksums já aplicados e instala a V2 pendente.
    assertEquals(1, reopened.migrate().migrationsExecuted);
    reopened.validate();
    assertEquals(0, reopened.migrate().migrationsExecuted);
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement();
        var rows = statement.executeQuery("SELECT nome FROM units")) {
      assertTrue(rows.next());
      assertEquals("Unidade preservada", rows.getString(1));
      assertFalse(rows.next());
    }
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement();
        var rows = statement.executeQuery(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_NAME='IDX_ORDERS_EXPIRATION'")) {
      assertTrue(rows.next());
      assertEquals(1, rows.getInt(1));
    }
  }
}
