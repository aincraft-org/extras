package dev.mintychochip.paper;

import dev.mintychochip.api.cinematic.CameraKeyframe;
import dev.mintychochip.api.cinematic.CameraPose;
import dev.mintychochip.api.cinematic.CinematicResult;
import dev.mintychochip.api.cinematic.CinematicScene;
import dev.mintychochip.api.cinematic.CinematicService;
import dev.mintychochip.api.cinematic.OverlayCue;
import dev.mintychochip.api.cinematic.PlaybackSnapshot;
import dev.mintychochip.api.cinematic.PropCue;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /cinematic} — operators create camera paths, overlay shaders and props, then play/stop
 * named scenes.
 */
public final class CinematicCommand implements BasicCommand {

  static final String USE_PERMISSION = "extras.cinematic.use";
  private static final List<String> ACTIONS =
      List.of("create", "camera", "shaders", "props", "play", "stop", "list");

  private final CinematicService cinematicService;
  private final PaperCinematicController controller;

  public CinematicCommand(CinematicService cinematicService, PaperCinematicController controller) {
    this.cinematicService = java.util.Objects.requireNonNull(cinematicService, "cinematicService");
    this.controller = java.util.Objects.requireNonNull(controller, "controller");
  }

  enum Action {
    CREATE,
    CAMERA,
    SHADERS,
    PROPS,
    PLAY,
    STOP,
    LIST,
    UNKNOWN
  }

  static Action parseAction(String[] args) {
    if (args.length == 0) {
      return Action.UNKNOWN;
    }
    return switch (args[0].toLowerCase(Locale.ROOT)) {
      case "create" -> Action.CREATE;
      case "camera" -> Action.CAMERA;
      case "shaders" -> Action.SHADERS;
      case "props" -> Action.PROPS;
      case "play" -> Action.PLAY;
      case "stop" -> Action.STOP;
      case "list" -> Action.LIST;
      default -> Action.UNKNOWN;
    };
  }

  static List<String> suggestions(String input) {
    String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
    List<String> result = new ArrayList<>();
    for (String action : ACTIONS) {
      if (action.startsWith(prefix)) {
        result.add(action);
      }
    }
    result.sort(null);
    return result;
  }

  @Override
  public String permission() {
    return USE_PERMISSION;
  }

  @Override
  public void execute(CommandSourceStack stack, String[] args) {
    CommandSender sender = stack.getSender();
    if (!sender.hasPermission(USE_PERMISSION)) {
      sender.sendMessage("You do not have permission to use cinematics.");
      return;
    }
    switch (parseAction(args)) {
      case CREATE -> create(sender, args);
      case CAMERA -> camera(sender, args);
      case SHADERS -> shaders(sender, args);
      case PROPS -> props(sender, args);
      case PLAY -> play(sender, args);
      case STOP -> stop(sender, args);
      case LIST -> list(sender);
      default -> sendUsage(sender);
    }
  }

  @Override
  public Collection<String> suggest(CommandSourceStack stack, String[] args) {
    if (args.length == 0 || args.length == 1) {
      return suggestions(args.length == 0 ? "" : args[0]);
    }
    if (args.length == 2
        && (parseAction(args) == Action.CAMERA
            || parseAction(args) == Action.SHADERS
            || parseAction(args) == Action.PROPS)) {
      return filter(List.of("add"), args[1]);
    }
    if (args.length == 2 && parseAction(args) == Action.CREATE) {
      return List.of();
    }
    if (args.length == 2
        && (parseAction(args) == Action.PLAY || parseAction(args) == Action.STOP)) {
      if (parseAction(args) == Action.STOP) {
        return filter(onlinePlayerNames(), args[1]);
      }
      return filter(sceneNames(), args[1]);
    }
    if (args.length == 3 && parseAction(args) == Action.CAMERA) {
      return filter(sceneNames(), args[2]);
    }
    if (args.length == 3 && parseAction(args) == Action.PLAY) {
      return filter(onlinePlayerNames(), args[2]);
    }
    return List.of();
  }

  private void create(CommandSender sender, String... args) {
    if (args.length < 2) {
      sender.sendMessage("Usage: /cinematic create <name>");
      return;
    }
    sender.sendMessage(describe(cinematicService.create(args[1]), args[1]));
  }

  private void camera(CommandSender sender, String... args) {
    if (args.length < 3 || !"add".equalsIgnoreCase(args[1])) {
      sender.sendMessage("Usage: /cinematic camera add <name> [time]");
      return;
    }
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can add a camera keyframe from their view.");
      return;
    }
    CameraPose pose = PaperCinematicController.poseOf(player.getLocation());
    CinematicResult result;
    if (args.length >= 4) {
      Optional<Double> time = parseDouble(args[3]);
      if (time.isEmpty()) {
        sender.sendMessage("Time must be a number of seconds.");
        return;
      }
      result = cinematicService.addKeyframe(args[2], new CameraKeyframe(time.get(), pose));
    } else {
      result = cinematicService.addKeyframe(args[2], pose);
    }
    sender.sendMessage(describe(result, args[2]));
  }

  private void shaders(CommandSender sender, String... args) {
    if (args.length < 6 || !"add".equalsIgnoreCase(args[1])) {
      sender.sendMessage("Usage: /cinematic shaders add <name> <overlay> <start> <end>");
      return;
    }
    Optional<Double> start = parseDouble(args[4]);
    Optional<Double> end = parseDouble(args[5]);
    if (start.isEmpty() || end.isEmpty()) {
      sender.sendMessage("Start and end must be numbers of seconds.");
      return;
    }
    OverlayCue cue;
    try {
      cue = new OverlayCue(args[3], start.get(), end.get());
    } catch (IllegalArgumentException invalid) {
      sender.sendMessage("Invalid shader overlay cue.");
      return;
    }
    sender.sendMessage(describe(cinematicService.addShader(args[2], cue), args[2]));
  }

  private void props(CommandSender sender, String... args) {
    if (args.length < 6 || !"add".equalsIgnoreCase(args[1])) {
      sender.sendMessage("Usage: /cinematic props add <name> <prop> <start> <end>");
      return;
    }
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can add a prop from their view.");
      return;
    }
    Optional<Double> start = parseDouble(args[4]);
    Optional<Double> end = parseDouble(args[5]);
    if (start.isEmpty() || end.isEmpty()) {
      sender.sendMessage("Start and end must be numbers of seconds.");
      return;
    }
    PropCue cue;
    try {
      cue =
          new PropCue(
              args[3],
              start.get(),
              end.get(),
              PaperCinematicController.poseOf(player.getLocation()));
    } catch (IllegalArgumentException invalid) {
      sender.sendMessage("Invalid prop cue.");
      return;
    }
    sender.sendMessage(describe(cinematicService.addProp(args[2], cue), args[2]));
  }

  private void play(CommandSender sender, String... args) {
    if (args.length < 2) {
      sender.sendMessage("Usage: /cinematic play <name> [player]");
      return;
    }
    Player target = resolveTarget(sender, args, 2);
    if (target == null) {
      return;
    }
    CinematicResult result = controller.play(target, args[1]);
    if (result == CinematicResult.SUCCESS) {
      sender.sendMessage("Playing cinematic " + args[1] + " for " + target.getName() + ".");
      return;
    }
    sender.sendMessage(describe(result, args[1]));
  }

  private void stop(CommandSender sender, String... args) {
    Player target = resolveTarget(sender, args, 1);
    if (target == null) {
      return;
    }
    Optional<PlaybackSnapshot> stopped = controller.stop(target);
    if (stopped.isEmpty()) {
      sender.sendMessage(target.getName() + " is not playing a cinematic.");
      return;
    }
    sender.sendMessage("Stopped cinematic for " + target.getName() + ".");
  }

  private void list(CommandSender sender) {
    Collection<CinematicScene> scenes = cinematicService.scenes();
    if (scenes.isEmpty()) {
      sender.sendMessage("No cinematic scenes.");
      return;
    }
    sender.sendMessage("Cinematic scenes:");
    for (CinematicScene scene : scenes) {
      sender.sendMessage(
          "  "
              + scene.name()
              + " — "
              + scene.keyframes().size()
              + " camera keyframes, "
              + scene.shaders().size()
              + " shaders, "
              + scene.props().size()
              + " props");
    }
  }

  private Player resolveTarget(CommandSender sender, String[] args, int playerIndex) {
    if (args.length > playerIndex) {
      Player online = Bukkit.getPlayerExact(args[playerIndex]);
      if (online == null) {
        sender.sendMessage("Unknown player: " + args[playerIndex]);
        return null;
      }
      return online;
    }
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Specify a player from the console.");
      return null;
    }
    return player;
  }

  private List<String> sceneNames() {
    List<String> names = new ArrayList<>();
    for (CinematicScene scene : cinematicService.scenes()) {
      names.add(scene.name());
    }
    return names;
  }

  private static Optional<Double> parseDouble(String raw) {
    try {
      return Optional.of(Double.parseDouble(raw));
    } catch (NumberFormatException invalid) {
      return Optional.empty();
    }
  }

  private static String describe(CinematicResult result, String scene) {
    return switch (result) {
      case SUCCESS -> "Updated cinematic " + scene + ".";
      case TOO_FEW_KEYFRAMES ->
          "Scene " + scene + " needs at least two camera keyframes before it can play.";
      case UNKNOWN_SCENE -> "Unknown cinematic scene: " + scene + ".";
      case INVALID_NAME -> "Invalid scene name (use 1–64 [a-z0-9_-] characters).";
      case ALREADY_EXISTS -> "Cinematic scene " + scene + " already exists.";
      case ALREADY_PLAYING -> "That player is already playing a cinematic.";
      case INVALID_KEYFRAME -> "Invalid camera keyframe (time must be unique and >= 0).";
      case INVALID_CUE -> "Invalid shader or prop cue.";
    };
  }

  private static void sendUsage(CommandSender sender) {
    sender.sendMessage("Usage: /cinematic create <name>");
    sender.sendMessage("       /cinematic camera add <name> [time]");
    sender.sendMessage("       /cinematic shaders add <name> <overlay> <start> <end>");
    sender.sendMessage("       /cinematic props add <name> <prop> <start> <end>");
    sender.sendMessage("       /cinematic play <name> [player]");
    sender.sendMessage("       /cinematic stop [player]");
    sender.sendMessage("       /cinematic list");
  }

  private static List<String> filter(List<String> candidates, String prefix) {
    String lower = prefix.toLowerCase(Locale.ROOT);
    List<String> result = new ArrayList<>();
    for (String candidate : candidates) {
      if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
        result.add(candidate);
      }
    }
    return result;
  }

  private static List<String> onlinePlayerNames() {
    List<String> names = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      names.add(player.getName());
    }
    return names;
  }
}
