package dev.mintychochip.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import dev.mintychochip.api.rewards.CriterionKind;
import dev.mintychochip.api.rewards.CriterionProposalRequest;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class WorkflowzCriterionProviderTest {

  @Test
  void validStructuredResponseBecomesAValidatedCriterion() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] response =
              "{\"id\":\"generated\",\"type\":\"GAIN_XP\",\"target\":25,\"description\":\"Earn XP\"}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.setExecutor(Executors.newSingleThreadExecutor());
    server.start();
    try {
      WorkflowzCriterionProvider provider =
          new WorkflowzCriterionProvider(
              "OPTIONAL", URI.create("http://localhost:" + server.getAddress().getPort() + "/"));
      Optional<dev.mintychochip.api.rewards.Criterion> criterion =
          provider.propose(
              new CriterionProposalRequest("1", LocalDate.of(2026, 8, 12), List.of("fallback")));
      assertTrue(criterion.isPresent());
      assertEquals(CriterionKind.GAIN_XP, criterion.orElseThrow().kind());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void executableFieldsAreRejected() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] response =
              "{\"id\":\"bad\",\"type\":\"GAIN_XP\",\"target\":25,\"description\":\"Earn\",\"command\":\"op\"}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      WorkflowzCriterionProvider provider =
          new WorkflowzCriterionProvider(
              "OPTIONAL", URI.create("http://localhost:" + server.getAddress().getPort() + "/"));
      assertTrue(
          provider
              .propose(new CriterionProposalRequest("1", LocalDate.of(2026, 8, 12), List.of()))
              .isEmpty());
    } finally {
      server.stop(0);
    }
  }
}
