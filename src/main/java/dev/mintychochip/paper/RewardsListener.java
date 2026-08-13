package dev.mintychochip.paper;

import dev.mintychochip.api.rewards.CriterionKind;
import dev.mintychochip.api.rewards.CriterionProgress;
import dev.mintychochip.api.rewards.CriterionProposalRequest;
import dev.mintychochip.api.rewards.DailyRewardService;
import dev.mintychochip.api.rewards.LoginStreakService;
import dev.mintychochip.api.rewards.StreakResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/** Maps successful Paper events to the criterion API. */
public final class RewardsListener implements Listener {

  private final Plugin plugin;
  private final DailyRewardService dailyRewardService;
  private final LoginStreakService streakService;
  private final WorkflowzCriterionProvider criterionProvider;
  private final List<dev.mintychochip.api.rewards.Criterion> criterionPool;
  private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();
  private final AtomicReference<LocalDate> proposalDate = new AtomicReference<>();

  public RewardsListener(
      Plugin plugin, DailyRewardService dailyRewardService, LoginStreakService streakService) {
    this(plugin, dailyRewardService, streakService, null, List.of());
  }

  public RewardsListener(
      Plugin plugin,
      DailyRewardService dailyRewardService,
      LoginStreakService streakService,
      WorkflowzCriterionProvider criterionProvider,
      List<dev.mintychochip.api.rewards.Criterion> criterionPool) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.dailyRewardService = Objects.requireNonNull(dailyRewardService, "dailyRewardService");
    this.streakService = Objects.requireNonNull(streakService, "streakService");
    this.criterionProvider = criterionProvider;
    this.criterionPool = List.copyOf(criterionPool);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    joinTimes.put(player.getUniqueId(), System.currentTimeMillis());
    StreakResult result = streakService.recordLogin(player.getUniqueId());
    if (result == StreakResult.INCREMENTED || result == StreakResult.RESET) {
      player.sendMessage(
          "Login streak: " + streakService.streak(player.getUniqueId()).currentStreak() + " days.");
    }
    dailyRewardService.recordProgress(
        player.getUniqueId(), new CriterionProgress(CriterionKind.LOGIN_DAYS, "login", 1));
    LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
    if (criterionProvider != null && proposalDate.compareAndSet(null, today)) {
      criterionProvider
          .proposeAsync(new CriterionProposalRequest("1", today, criterionSummaries()))
          .thenAccept(
              proposal ->
                  proposal.ifPresent(
                      criterion ->
                          player
                              .getScheduler()
                              .execute(
                                  plugin,
                                  () -> dailyRewardService.forceCriterion(criterion),
                                  null,
                                  1L)));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    Long joinedAt = joinTimes.remove(playerId);
    if (joinedAt != null) {
      int seconds = (int) Math.max(1L, (System.currentTimeMillis() - joinedAt) / 1000L);
      record(playerId, new CriterionProgress(CriterionKind.PLAY_TIME, "seconds", seconds));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    record(
        event.getPlayer().getUniqueId(),
        new CriterionProgress(
            CriterionKind.MINE_BLOCKS,
            "minecraft:" + event.getBlock().getType().name().toLowerCase(java.util.Locale.ROOT),
            1));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityDeath(EntityDeathEvent event) {
    Player killer = event.getEntity().getKiller();
    if (killer != null) {
      record(
          killer.getUniqueId(),
          new CriterionProgress(
              CriterionKind.KILL_ENTITIES,
              "minecraft:" + event.getEntityType().name().toLowerCase(java.util.Locale.ROOT),
              1));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onCraft(CraftItemEvent event) {
    if (event.getWhoClicked() instanceof Player player && event.getRecipe() != null) {
      record(
          player.getUniqueId(),
          new CriterionProgress(
              CriterionKind.CRAFT_ITEMS,
              "minecraft:"
                  + event
                      .getRecipe()
                      .getResult()
                      .getType()
                      .name()
                      .toLowerCase(java.util.Locale.ROOT),
              event.getRecipe().getResult().getAmount()));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onExperience(PlayerExpChangeEvent event) {
    if (event.getAmount() > 0) {
      record(
          event.getPlayer().getUniqueId(),
          new CriterionProgress(CriterionKind.GAIN_XP, "xp", event.getAmount()));
    }
  }

  private List<String> criterionSummaries() {
    return criterionPool.stream()
        .map(value -> value.kind().name() + ":" + value.description())
        .toList();
  }

  private void record(UUID playerId, CriterionProgress progress) {
    dailyRewardService.recordProgress(playerId, progress);
  }
}
