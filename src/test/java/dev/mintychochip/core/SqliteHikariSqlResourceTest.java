package dev.mintychochip.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.mintychochip.api.MailMessage;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteHikariSqlResourceTest {

  @TempDir Path tempDir;

  @Test
  void mailRepositoryUsesHikariPoolAndSqlResources() throws Exception {
    String insertSql = SqlStatements.load("mail/insert.sql");
    assertTrue(insertSql.toUpperCase().contains("INSERT INTO MAIL"));
    assertTrue(insertSql.contains("?"));

    Path dbFile = tempDir.resolve("mail.db");
    try (SqliteMailRepository repository = new SqliteMailRepository(dbFile)) {
      HikariDataSource pool = assertInstanceOf(HikariDataSource.class, repository.dataSource());
      assertTrue(pool.getHikariPoolMXBean().getTotalConnections() >= 1);
      UUID alice = UUID.randomUUID();
      MailMessage inserted =
          repository.insert(new MailMessage(0, alice, "Alice", "hello", 1_000L, false, null));
      assertTrue(inserted.id() > 0);
      assertEquals(1, repository.count(alice));
      assertEquals("hello", repository.list(alice, 0, 10).get(0).body());
    }
  }
}
