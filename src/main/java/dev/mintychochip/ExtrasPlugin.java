package dev.mintychochip;

import dev.mintychochip.api.ChatService;
import dev.mintychochip.api.FriendService;
import dev.mintychochip.api.MailService;
import dev.mintychochip.api.PartyService;
import dev.mintychochip.api.TitleService;
import dev.mintychochip.api.events.ExtrasEventService;
import dev.mintychochip.api.rewards.DailyRewardService;
import dev.mintychochip.api.rewards.LeaderboardService;
import dev.mintychochip.api.rewards.LoginStreakService;
import dev.mintychochip.core.ChatRouter;
import dev.mintychochip.core.DefaultChatService;
import dev.mintychochip.core.DefaultDailyRewardService;
import dev.mintychochip.core.DefaultFriendService;
import dev.mintychochip.core.DefaultLeaderboardService;
import dev.mintychochip.core.DefaultLoginStreakService;
import dev.mintychochip.core.DefaultMailService;
import dev.mintychochip.core.DefaultPartyService;
import dev.mintychochip.core.DefaultTitleService;
import dev.mintychochip.core.DefaultTradeService;
import dev.mintychochip.core.InProcessExtrasEventService;
import dev.mintychochip.core.JsonTitleRepository;
import dev.mintychochip.core.SqliteChatRepository;
import dev.mintychochip.core.SqliteFriendRepository;
import dev.mintychochip.core.SqliteMailRepository;
import dev.mintychochip.core.SqlitePartyRepository;
import dev.mintychochip.core.SqliteRewardStore;
import dev.mintychochip.paper.ChatCommand;
import dev.mintychochip.paper.ChatListener;
import dev.mintychochip.paper.ChatPresenceRegistry;
import dev.mintychochip.paper.ComposeGui;
import dev.mintychochip.paper.FriendCommand;
import dev.mintychochip.paper.FriendLifecycleListener;
import dev.mintychochip.paper.MailCommand;
import dev.mintychochip.paper.MailboxGui;
import dev.mintychochip.paper.PartyCommand;
import dev.mintychochip.paper.PartyLifecycleListener;
import dev.mintychochip.paper.RewardsCommand;
import dev.mintychochip.paper.RewardsConfig;
import dev.mintychochip.paper.RewardsListener;
import dev.mintychochip.paper.TitleCommand;
import dev.mintychochip.paper.TradeCommand;
import dev.mintychochip.paper.TradeGui;
import dev.mintychochip.paper.WorkflowzCriterionProvider;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Extras entrypoint and lifecycle owner for all plugin services. */
public final class ExtrasPlugin extends JavaPlugin {
  private DefaultPartyService partyService;
  private PartyLifecycleListener partyLifecycleListener;
  private DefaultFriendService friendService;
  private FriendLifecycleListener friendLifecycleListener;
  private DefaultTitleService titleService;
  private SqliteRewardStore rewardStore;
  private DefaultDailyRewardService dailyRewardService;
  private DefaultLeaderboardService leaderboardService;
  private DefaultLoginStreakService loginStreakService;
  private RewardsListener rewardsListener;
  private DefaultTradeService tradeService;
  private SqliteMailRepository mailRepository;
  private MailService mailService;
  private SqliteChatRepository chatRepository;
  private ChatService chatService;
  private ChatPresenceRegistry chatPresenceRegistry;
  private ChatListener chatListener;
  private org.bukkit.event.Listener mailboxGuiListener;
  private InProcessExtrasEventService eventService;
  private org.bukkit.event.Listener composeListener;
  private org.bukkit.event.Listener tradeGuiListener;

  @Override
  public void onEnable() {
    if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
      getLogger().severe("Failed to create data folder; disabling.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    Path dataDir = getDataFolder().toPath();

    eventService =
        new InProcessExtrasEventService(
            failure -> getLogger().warning("Extras event subscriber failed: " + failure));
    Bukkit.getServicesManager()
        .register(ExtrasEventService.class, eventService, this, ServicePriority.Normal);

    partyService =
        new DefaultPartyService(
            new SqlitePartyRepository(dataDir.resolve("party.db")), eventService);
    Bukkit.getServicesManager()
        .register(PartyService.class, partyService, this, ServicePriority.Normal);
    partyLifecycleListener = new PartyLifecycleListener(partyService);
    Bukkit.getPluginManager().registerEvents(partyLifecycleListener, this);
    friendService =
        new DefaultFriendService(
            new SqliteFriendRepository(dataDir.resolve("friends.db")),
            java.time.Clock.systemUTC(),
            eventService);
    Bukkit.getServicesManager()
        .register(FriendService.class, friendService, this, ServicePriority.Normal);
    friendLifecycleListener = new FriendLifecycleListener(friendService);
    Bukkit.getPluginManager().registerEvents(friendLifecycleListener, this);

    titleService =
        new DefaultTitleService(
            new JsonTitleRepository(dataDir.resolve("titles")),
            java.time.Clock.systemUTC(),
            eventService);
    Bukkit.getServicesManager()
        .register(TitleService.class, titleService, this, ServicePriority.Normal);

    chatRepository = new SqliteChatRepository(dataDir.resolve("chat.db"));
    chatService = new DefaultChatService(chatRepository, java.time.Clock.systemUTC(), eventService);
    Bukkit.getServicesManager()
        .register(ChatService.class, chatService, this, ServicePriority.Normal);
    chatPresenceRegistry = new ChatPresenceRegistry();
    for (org.bukkit.entity.Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
      player
          .getScheduler()
          .execute(
              this,
              () -> {
                org.bukkit.Location location = player.getLocation();
                chatPresenceRegistry.update(
                    new dev.mintychochip.core.PresenceSnapshot(
                        player.getUniqueId(),
                        location.getWorld().getUID(),
                        location.getX(),
                        location.getY(),
                        location.getZ()));
              },
              null,
              1L);
    }
    chatListener =
        new ChatListener(
            this, chatService, partyService, titleService, new ChatRouter(), chatPresenceRegistry);
    RewardsConfig rewardsConfig = RewardsConfig.load(this);
    rewardStore = new SqliteRewardStore(dataDir.resolve("rewards.db"));
    java.time.Clock rewardsClock = java.time.Clock.systemUTC();
    leaderboardService = new DefaultLeaderboardService(rewardStore, rewardsClock);
    dailyRewardService =
        new DefaultDailyRewardService(
            rewardStore,
            rewardsClock,
            rewardsConfig.criterionPool().get(0),
            rewardsConfig.criterionPool(),
            leaderboardService,
            eventService);
    loginStreakService = new DefaultLoginStreakService(rewardStore, rewardsClock, eventService);
    WorkflowzCriterionProvider workflowzProvider =
        new WorkflowzCriterionProvider(
            rewardsConfig.providerMode(),
            rewardsConfig.providerEndpoint().isBlank()
                ? null
                : java.net.URI.create(rewardsConfig.providerEndpoint()));
    Bukkit.getServicesManager()
        .register(DailyRewardService.class, dailyRewardService, this, ServicePriority.Normal);
    Bukkit.getServicesManager()
        .register(LeaderboardService.class, leaderboardService, this, ServicePriority.Normal);
    Bukkit.getServicesManager()
        .register(LoginStreakService.class, loginStreakService, this, ServicePriority.Normal);
    rewardsListener =
        new RewardsListener(
            this,
            dailyRewardService,
            loginStreakService,
            workflowzProvider,
            rewardsConfig.criterionPool());
    Bukkit.getPluginManager().registerEvents(rewardsListener, this);
    Bukkit.getPluginManager().registerEvents(chatListener, this);
    Path mailboxDb = dataDir.resolve("mailbox").resolve("mailbox.db");
    mailRepository = new SqliteMailRepository(mailboxDb);
    mailService = new DefaultMailService(mailRepository, java.time.Clock.systemUTC(), eventService);
    Bukkit.getServicesManager()
        .register(MailService.class, mailService, this, ServicePriority.Normal);
    mailboxGuiListener = MailboxGui.clickListener(mailService);
    Bukkit.getPluginManager().registerEvents(mailboxGuiListener, this);
    composeListener = ComposeGui.listener();
    Bukkit.getPluginManager().registerEvents(composeListener, this);

    tradeService = new DefaultTradeService(java.time.Clock.systemUTC(), eventService);
    tradeGuiListener = TradeGui.listener(tradeService);
    Bukkit.getPluginManager().registerEvents(tradeGuiListener, this);

    getLifecycleManager()
        .registerEventHandler(
            LifecycleEvents.COMMANDS,
            event -> {
              event
                  .registrar()
                  .register(
                      "party",
                      "Manage persistent player parties.",
                      List.of(),
                      new PartyCommand(partyService));
              event
                  .registrar()
                  .register(
                      "rewards",
                      "Claim daily rewards and view leaderboards.",
                      List.of("daily", "leaderboard", "streak"),
                      new RewardsCommand(
                          dailyRewardService,
                          leaderboardService,
                          loginStreakService,
                          rewardsConfig.criterionPool(),
                          rewardsConfig.commandAllowlist()));
              event
                  .registrar()
                  .register(
                      "friend",
                      "Manage persistent player friendships.",
                      List.of("friends"),
                      new FriendCommand(friendService));
              event
                  .registrar()
                  .register(
                      "title",
                      "Manage cosmetic player titles.",
                      List.of("titles"),
                      new TitleCommand(titleService));
              event
                  .registrar()
                  .register(
                      "mail",
                      "Player mailbox — send, read, and claim mail.",
                      List.of(),
                      new MailCommand(mailService));
              event
                  .registrar()
                  .register(
                      "trade",
                      "Trade items with another online player.",
                      List.of(),
                      new TradeCommand(tradeService));
              event
                  .registrar()
                  .register(
                      "chat",
                      "Manage chat channels and preferences.",
                      List.of("ch", "c"),
                      new ChatCommand(chatService, chatListener::sendOnce));
            });
    getLogger()
        .info(
            "Extras enabled (SQLite stores at "
                + dataDir.resolve("party.db")
                + ", "
                + dataDir.resolve("friends.db")
                + ", "
                + mailboxDb
                + ", and "
                + dataDir.resolve("chat.db")
                + "; titles at "
                + dataDir.resolve("titles")
                + ").");
  }

  @Override
  public void onDisable() {
    TradeGui.closeActiveSessions();
    ComposeGui.closeActiveSessions();
    MailboxGui.clearViews();
    if (tradeGuiListener != null) {
      HandlerList.unregisterAll(tradeGuiListener);
      tradeGuiListener = null;
    }
    if (mailboxGuiListener != null) {
      HandlerList.unregisterAll(mailboxGuiListener);
      mailboxGuiListener = null;
    }
    if (rewardsListener != null) {
      HandlerList.unregisterAll(rewardsListener);
      rewardsListener = null;
    }
    if (dailyRewardService != null) {
      dailyRewardService.close();
      dailyRewardService = null;
    }
    if (leaderboardService != null) {
      leaderboardService = null;
    }
    if (loginStreakService != null) {
      loginStreakService = null;
    }
    rewardStore = null;
    if (composeListener != null) {
      HandlerList.unregisterAll(composeListener);
      composeListener = null;
    }
    if (chatListener != null) {
      HandlerList.unregisterAll(chatListener);
      chatListener.close();
      chatListener = null;
    }
    if (partyLifecycleListener != null) {
      HandlerList.unregisterAll(partyLifecycleListener);
      partyLifecycleListener = null;
    }
    if (friendLifecycleListener != null) {
      HandlerList.unregisterAll(friendLifecycleListener);
      friendLifecycleListener = null;
    }
    if (partyService != null) {
      partyService.close();
      partyService = null;
    }
    if (friendService != null) {
      friendService.close();
      friendService = null;
    }
    if (mailRepository != null) {
      mailRepository.close();
      mailRepository = null;
    }
    if (chatRepository != null) {
      chatRepository.close();
      chatRepository = null;
    }
    titleService = null;
    tradeService = null;
    mailService = null;
    chatService = null;
    chatPresenceRegistry = null;
    if (eventService != null) {
      eventService.close();
      eventService = null;
    }
    Bukkit.getServicesManager().unregister(this);
    getLogger().info("Extras disabled; party, friend, mailbox, trade, and chat stores closed.");
  }

  public PartyService partyService() {
    return partyService;
  }

  public FriendService friendService() {
    return friendService;
  }

  public TitleService titleService() {
    return titleService;
  }

  public MailService mailService() {
    return mailService;
  }

  public ChatService chatService() {
    return chatService;
  }
}
