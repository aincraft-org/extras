package dev.mintychochip.api.rewards;

import java.util.Optional;

/** Optional boundary for external structured criterion proposals. */
public interface CriterionProvider {

  Optional<Criterion> propose(CriterionProposalRequest request);
}
