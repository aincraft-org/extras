package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.api.MailMessage;
import java.nio.file.Path;
import java.util.UUID;
import org.aincraft.db.sql.SqlDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteHikariSqlResourceTest {

  @TempDir Path tempDir;

  @Test
  void mailRepositoryUsesUtilityDatabaseAndSqlResources() throws Exception {
    String insertSql = SqlStatements.load("mail/insert.sql");
    assertTrue(insertSql.toUpperCase().contains("INSERT INTO MAIL"));
    assertTrue(insertSql.contains("?"));

    Path dbFile = tempDir.resolve("mail.db");
    try (SqliteMailRepository repository = new SqliteMailRepository(dbFile)) {
      SqlDatabase database = repository.database();
      assertFalse(database.closed());
      UUID alice = UUID.randomUUID();
      MailMessage inserted =
          repository.insert(new MailMessage(0, alice, "Alice", "hello", 1_000L, false, null));
      assertTrue(inserted.id() > 0);
      assertEquals(1, repository.count(alice));
      assertEquals("hello", repository.list(alice, 0, 10).get(0).body());
    }
  }
}
