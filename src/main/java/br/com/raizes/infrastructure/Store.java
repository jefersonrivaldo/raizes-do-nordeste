package br.com.raizes.infrastructure;

import static br.com.raizes.domain.Rules.require;

import java.sql.Statement;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class Store {
  private final JdbcTemplate jdbc;

  public JdbcTemplate jdbc() {
    return jdbc;
  }

  public Store(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> get(String table, long id) {
    var rows = jdbc.queryForList("SELECT * FROM " + table + " WHERE id=?", id);
    require(!rows.isEmpty(), "NAO_ENCONTRADO", "Recurso não encontrado.", 404);
    return rows.getFirst();
  }

  public Map<String, Object> lock(String table, long id) {
    var rows = jdbc.queryForList("SELECT * FROM " + table + " WHERE id=? FOR UPDATE", id);
    require(!rows.isEmpty(), "NAO_ENCONTRADO", "Recurso não encontrado.", 404);
    return rows.getFirst();
  }

  public long insert(String sql, Object... args) {
    var holder = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          var ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
          return ps;
        },
        holder);
    return holder.getKey().longValue();
  }

  public static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  public static Long nullableNumber(Map<String, Object> row, String key) {
    return row.get(key) == null ? null : number(row, key);
  }

  public static String text(Map<String, Object> row, String key) {
    return (String) row.get(key);
  }

  public static boolean bool(Map<String, Object> row, String key) {
    return Boolean.TRUE.equals(row.get(key));
  }
}
