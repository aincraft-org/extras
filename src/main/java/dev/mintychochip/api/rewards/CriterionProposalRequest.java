package dev.mintychochip.api.rewards;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Bounded, non-sensitive input supplied to an external criterion provider. */
public record CriterionProposalRequest(
    String schemaVersion, LocalDate day, List<String> fallbackSummaries) {

  public CriterionProposalRequest {
    schemaVersion = requireText(schemaVersion, "schemaVersion");
    Objects.requireNonNull(day, "day");
    fallbackSummaries = List.copyOf(fallbackSummaries);
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field);
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return normalized;
  }
}
