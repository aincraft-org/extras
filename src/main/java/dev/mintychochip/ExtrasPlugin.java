package dev.mintychochip;

import dev.mintychochip.api.FriendService;
import dev.mintychochip.api.MailService;
import dev.mintychochip.api.PartyService;
import dev.mintychochip.api.TitleService;
import dev.mintychochip.core.DefaultFriendService;
import dev.mintychochip.core.DefaultMailService;
import dev.mintychochip.core.DefaultPartyService;
import dev.mintychochip.core.DefaultTitleService;
import dev.mintychochip.core.DefaultTradeService;
import dev.mintychochip.core.JsonTitleRepository;
import dev.mintychochip.core.SqliteFriendRepository;
import dev.mintychochip.core.SqliteMailRepository;
import dev.mintychochip.core.SqlitePartyRepository;
import dev.mintychochip.paper.ComposeGui;
import dev.mintychochip.paper.FriendCommand;
import dev.mintychochip.paper.FriendLifecycleListener;
import dev.mintychochip.paper.MailCommand;
import dev.mintychochip.paper.MailboxGui;
import dev.mintychochip.paper.PartyCommand;
import dev.mintychochip.paper.PartyLifecycleListener;
import dev.mintychochip.paper.TitleCommand;
import dev.mintychochip.paper.TradeCommand;
import dev.mintychochip.paper.TradeGui;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Extras entrypoint.
 *
 * <p>Constructs the Bukkit-free party, friend, title, mailbox, and transient trade services,
 * registers the persistent services on the Bukkit {@code ServicesManager}, registers the {@code
 * /party}, {@code /friend}, {@code /title}, {@code /mail}, and {@code /trade} commands, and
 * announces member/friend join/quit presence.
 */
public final class ExtrasPlugin extends JavaPlugin {

  private DefaultPartyService partyService;
  private PartyLifecycleListener partyLifecycleListener;
  private DefaultFriendService friendService;
  private FriendLifecycleListener friendLifecycleListener;
  private DefaultTitleService titleService;
  private DefaultTradeService tradeService;
  private SqliteMailRepository mailRepository;
  private MailService mailService;
  private org.bukkit.event.Listener mailboxGuiListener;
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

    SqlitePartyRepository repository = new SqlitePartyRepository(dataDir.resolve("party.db"));
    partyService = new DefaultPartyService(repository);

    Bukkit.getServicesManager()
        .register(PartyService.class, partyService, this, ServicePriority.Normal);

    partyLifecycleListener = new PartyLifecycleListener(partyService);
    Bukkit.getPluginManager().registerEvents(partyLifecycleListener, this);

    SqliteFriendRepository friendRepository =
        new SqliteFriendRepository(dataDir.resolve("friends.db"));
    friendService = new DefaultFriendService(friendRepository, java.time.Clock.systemUTC());

    Bukkit.getServicesManager()
        .register(FriendService.class, friendService, this, ServicePriority.Normal);

    friendLifecycleListener = new FriendLifecycleListener(friendService);
    Bukkit.getPluginManager().registerEvents(friendLifecycleListener, this);

    titleService = new DefaultTitleService(new JsonTitleRepository(dataDir.resolve("titles")));
    Bukkit.getServicesManager()
        .register(TitleService.class, titleService, this, ServicePriority.Normal);

    Path mailboxDb = dataDir.resolve("mailbox").resolve("mailbox.db");
    mailRepository = new SqliteMailRepository(mailboxDb);
    mailService = new DefaultMailService(mailRepository);
    Bukkit.getServicesManager()
        .register(MailService.class, mailService, this, ServicePriority.Normal);

    mailboxGuiListener = MailboxGui.clickListener(mailService);
    Bukkit.getPluginManager().registerEvents(mailboxGuiListener, this);
    composeListener = ComposeGui.listener();
    Bukkit.getPluginManager().registerEvents(composeListener, this);

    tradeService = new DefaultTradeService();
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
            });
    getLogger()
        .info(
            "Extras enabled (SQLite stores at "
                + dataDir.resolve("party.db")
                + ", "
                + dataDir.resolve("friends.db")
                + ", and "
                + mailboxDb
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
    if (composeListener != null) {
      HandlerList.unregisterAll(composeListener);
      composeListener = null;
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
    titleService = null;
    tradeService = null;
    mailService = null;
    Bukkit.getServicesManager().unregister(this);
    getLogger().info("Extras disabled; party, friend, mailbox, and trade stores closed.");
  }

  /** Returns the live party service, or {@code null} if disabled. */
  public PartyService partyService() {
    return partyService;
  }

  /** Returns the live friend service, or {@code null} if disabled. */
  public FriendService friendService() {
    return friendService;
  }

  /** Returns the live title service, or {@code null} if disabled. */
  public TitleService titleService() {
    return titleService;
  }

  /** Returns the live mail service, or {@code null} if disabled. */
  public MailService mailService() {
    return mailService;
  }
}
