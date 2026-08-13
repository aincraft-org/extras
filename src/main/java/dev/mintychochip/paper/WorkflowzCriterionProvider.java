package dev.mintychochip.paper;

import dev.mintychochip.api.rewards.CraftItemsCriterion;
import dev.mintychochip.api.rewards.Criterion;
import dev.mintychochip.api.rewards.CriterionKind;
import dev.mintychochip.api.rewards.CriterionProposalRequest;
import dev.mintychochip.api.rewards.CriterionProvider;
import dev.mintychochip.api.rewards.GainXpCriterion;
import dev.mintychochip.api.rewards.KillEntitiesCriterion;
import dev.mintychochip.api.rewards.LoginDaysCriterion;
import dev.mintychochip.api.rewards.MaterialKey;
import dev.mintychochip.api.rewards.MineBlocksCriterion;
import dev.mintychochip.api.rewards.PlayTimeCriterion;
import dev.mintychochip.api.rewards.Reward;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional bounded workflowz JSON client; it only returns validated criterion data. */
public final class WorkflowzCriterionProvider implements CriterionProvider {

  private static final Pattern FIELD =
      Pattern.compile("\\\"([a-zA-Z_]+)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
  private static final Pattern NUMBER = Pattern.compile("\\\"target\\\"\\s*:\\s*(\\d+)");

  private final String mode;
  private final URI endpoint;
  private final HttpClient client;
  private final Duration requestTimeout;
  private final int maxResponseBytes;

  public WorkflowzCriterionProvider(String mode, URI endpoint) {
    this(
        mode,
        endpoint,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
        Duration.ofSeconds(8),
        64 * 1024);
  }

  WorkflowzCriterionProvider(
      String mode, URI endpoint, HttpClient client, Duration requestTimeout, int maxResponseBytes) {
    this.mode = Objects.requireNonNull(mode, "mode").trim().toUpperCase(Locale.ROOT);
    this.endpoint = endpoint;
    this.client = Objects.requireNonNull(client, "client");
    this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    if (maxResponseBytes <= 0) {
      throw new IllegalArgumentException("maxResponseBytes must be positive");
    }
    this.maxResponseBytes = maxResponseBytes;
  }

  @Override
  public Optional<Criterion> propose(CriterionProposalRequest request) {
    if (endpoint == null || "DISABLED".equals(mode)) {
      return Optional.empty();
    }
    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder(endpoint)
              .timeout(requestTimeout)
              .header("content-type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(encode(request)))
              .build();
      HttpResponse<byte[]> response =
          client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() < 200 || response.statusCode() > 299) {
        return Optional.empty();
      }
      byte[] body = response.body();
      if (body.length > maxResponseBytes) {
        return Optional.empty();
      }
      return decode(new String(body, java.nio.charset.StandardCharsets.UTF_8), request);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (java.io.IOException | RuntimeException exception) {
      return Optional.empty();
    }
  }

  /** Runs the blocking proposal call away from the Paper thread. */
  public CompletableFuture<Optional<Criterion>> proposeAsync(CriterionProposalRequest request) {
    return CompletableFuture.supplyAsync(() -> propose(request));
  }

  private Optional<Criterion> decode(String body, CriterionProposalRequest request) {
    String lower = body.toLowerCase(Locale.ROOT);
    if (lower.contains("\"command\"")
        || lower.contains("\"run_command\"")
        || lower.contains("\"actions\"")) {
      return Optional.empty();
    }
    java.util.Map<String, String> fields = new java.util.HashMap<>();
    Matcher fieldMatcher = FIELD.matcher(body);
    while (fieldMatcher.find()) {
      fields.put(fieldMatcher.group(1), fieldMatcher.group(2));
    }
    Matcher targetMatcher = NUMBER.matcher(body);
    if (!targetMatcher.find()) {
      return Optional.empty();
    }
    int target = Integer.parseInt(targetMatcher.group(1));
    if (target <= 0 || target > 1_000_000) {
      return Optional.empty();
    }
    String id = fields.getOrDefault("id", "workflowz-" + request.day());
    String description = fields.get("description");
    String type = fields.get("type");
    if (description == null || type == null) {
      return Optional.empty();
    }
    Reward reward = Reward.xp(100);
    return Optional.of(create(id, description, type, fields, target, reward));
  }

  private static Criterion create(
      String id,
      String description,
      String type,
      java.util.Map<String, String> fields,
      int target,
      Reward reward) {
    return switch (CriterionKind.valueOf(type.toUpperCase(Locale.ROOT))) {
      case MINE_BLOCKS ->
          new MineBlocksCriterion(
              id, description, MaterialKey.parse(required(fields, "key")), target, reward);
      case KILL_ENTITIES ->
          new KillEntitiesCriterion(
              id, description, MaterialKey.parse(required(fields, "key")), target, reward);
      case CRAFT_ITEMS ->
          new CraftItemsCriterion(
              id, description, MaterialKey.parse(required(fields, "key")), target, reward);
      case GAIN_XP -> new GainXpCriterion(id, description, target, reward);
      case LOGIN_DAYS -> new LoginDaysCriterion(id, description, target, reward);
      case PLAY_TIME -> new PlayTimeCriterion(id, description, target, reward);
    };
  }

  private static String required(java.util.Map<String, String> fields, String key) {
    String value = fields.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("missing " + key);
    }
    return value;
  }

  private static String encode(CriterionProposalRequest request) {
    return "{\"schemaVersion\":\""
        + escape(request.schemaVersion())
        + "\",\"day\":\""
        + request.day()
        + "\",\"fallbacks\":["
        + request.fallbackSummaries().stream()
            .map(value -> "\"" + escape(value) + "\"")
            .collect(java.util.stream.Collectors.joining(","))
        + "]}";
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
