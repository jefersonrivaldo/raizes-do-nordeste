package br.com.raizes.infrastructure;

import static br.com.raizes.application.Services.now;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Seed implements ApplicationRunner {
  private final Store db;
  private final PasswordEncoder encoder;
  private final String password;
  private final boolean enabled;

  public Seed(
      Store db,
      PasswordEncoder encoder,
      @Value("${app.seed-password}") String password,
      @Value("${app.seed-enabled}") boolean enabled) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
    this.enabled = enabled;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!enabled) return;
    var admins = db.jdbc().queryForList("SELECT id FROM users WHERE email='admin@raizes.local'");
    long actor;
    if (admins.isEmpty()) {
      if (password.length() < 12
          || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
          || !password.matches("(?s)(?=.*[\\p{L}])(?=.*[0-9]).+"))
        throw new IllegalStateException(
            "Defina SEED_ADMIN_PASSWORD com 12 a 72 bytes, letras e números.");
      actor =
          db.insert(
              "INSERT INTO users(nome,email,password_hash,perfil,created_at) VALUES (?,?,?,?,?)",
              "Administrador de estudo",
              "admin@raizes.local",
              encoder.encode(password),
              "ADMIN",
              now());
    } else actor = Store.number(admins.getFirst(), "id");
    long[] units = new long[2];
    String[] names = {"Recife Centro", "Olinda"};
    for (int i = 0; i < 2; i++) {
      var found = db.jdbc().queryForList("SELECT id FROM units WHERE nome=? ORDER BY id", names[i]);
      units[i] =
          found.isEmpty()
              ? db.insert(
                  "INSERT INTO units(nome,endereco) VALUES (?,?)",
                  names[i],
                  "Rua de estudo, " + (i + 1) + "00")
              : Store.number(found.getFirst(), "id");
    }
    String[] products = {"Baião de dois", "Tapioca de queijo", "Suco de caju"};
    long[] prices = {2500, 1200, 800};
    for (int i = 0; i < 3; i++) {
      var found =
          db.jdbc().queryForList("SELECT id FROM products WHERE nome=? ORDER BY id", products[i]);
      long product =
          found.isEmpty()
              ? db.insert(
                  "INSERT INTO products(nome,descricao) VALUES (?,?)",
                  products[i],
                  "Produto de demonstração")
              : Store.number(found.getFirst(), "id");
      for (long unit : units) {
        if (db.jdbc()
            .queryForList("SELECT 1 FROM stocks WHERE unidade_id=? AND produto_id=?", unit, product)
            .isEmpty()) {
          db.jdbc().update("INSERT INTO stocks VALUES (?,?,100,?)", unit, product, prices[i]);
          db.jdbc()
              .update(
                  "INSERT INTO"
                      + " stock_movements(unidade_id,produto_id,quantidade,motivo,actor_id,created_at)"
                      + " VALUES (?,?,100,?,?,?)",
                  unit,
                  product,
                  "SEED",
                  actor,
                  now());
        }
      }
    }
  }
}
