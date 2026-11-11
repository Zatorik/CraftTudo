package br.com.puraresenha.resenhaantifreeze;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ResenhaAntiFreezePlugin extends JavaPlugin implements Listener {
    private static final long SECOND = 1000L;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Map<UUID, CombatData> combat = new HashMap<>();
    private final Deque<String> recentReports = new ArrayDeque<>();
    private BukkitTask sampler;
    private Object floodgateApi;
    private Method floodgateCheck;
    private String prefix;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        prefix = color(getConfig().getString("messages.prefix", "&8[&bAntiFreeze&8] "));
        setupFloodgate();
        Bukkit.getPluginManager().registerEvents(this, this);
        long interval = Math.max(1L, getConfig().getLong("sampling.interval-seconds", 5L)) * 20L;
        sampler = Bukkit.getScheduler().runTaskTimer(this, this::sampleCombat, interval, interval);
        getLogger().info("ResenhaAntiFreeze 1.0.0 ativado. Modo diagnostico: nenhuma mecanica de PvP sera alterada.");
    }

    @Override
    public void onDisable() {
        if (sampler != null) sampler.cancel();
        combat.clear();
        getLogger().info("ResenhaAntiFreeze desativado.");
    }

    private void setupFloodgate() {
        if (!getConfig().getBoolean("bedrock.detect-with-floodgate", true)) return;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateApi = apiClass.getMethod("getInstance").invoke(null);
            floodgateCheck = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            getLogger().info("Integracao opcional com Floodgate encontrada.");
        } catch (ReflectiveOperationException ignored) {
            floodgateApi = null;
            floodgateCheck = null;
            getLogger().info("Floodgate nao encontrado. A deteccao Bedrock ficara indisponivel.");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = null;
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            attacker = player;
        } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }

        Player victim = event.getEntity() instanceof Player player ? player : null;
        long now = System.currentTimeMillis();
        if (attacker != null) {
            CombatData data = data(attacker);
            data.lastCombatAt = now;
            data.hitsDealt++;
        }
        if (victim != null) {
            CombatData data = data(victim);
            data.lastCombatAt = now;
            data.hitsReceived++;
        }
        if (attacker != null && victim != null) {
            data(attacker).lastOpponent = victim.getName();
            data(victim).lastOpponent = attacker.getName();
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            CombatData data = data(player);
            if (System.currentTimeMillis() - data.lastCombatAt <= 30_000L) {
                data.projectilesLaunched++;
            }
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        Location location = event.getLocation();
        double radius = Math.max(1.0, getConfig().getDouble("sampling.nearby-radius", 12.0));
        for (Player player : location.getWorld().getPlayers()) {
            CombatData data = combat.get(player.getUniqueId());
            if (data != null && System.currentTimeMillis() - data.lastCombatAt <= 30_000L
                    && player.getLocation().distanceSquared(location) <= radius * radius) {
                data.nearbyExplosions++;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combat.remove(event.getPlayer().getUniqueId());
    }

    private CombatData data(Player player) {
        return combat.computeIfAbsent(player.getUniqueId(), ignored -> new CombatData());
    }

    private void sampleCombat() {
        long now = System.currentTimeMillis();
        long window = Math.max(1L, getConfig().getLong("sampling.combat-window-seconds", 15L)) * SECOND;
        double radius = Math.max(1.0, getConfig().getDouble("sampling.nearby-radius", 12.0));
        double radiusSquared = radius * radius;
        double[] tpsValues = Bukkit.getTPS();
        double tps = tpsValues.length == 0 ? -1.0 : tpsValues[0];
        List<String> linesToSave = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            CombatData data = combat.get(player.getUniqueId());
            if (data == null || now - data.lastCombatAt > window) continue;

            int living = 0;
            int projectiles = 0;
            int primedTnt = 0;
            for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
                if (entity instanceof Player) continue;
                if (entity instanceof Projectile) projectiles++;
                if (entity instanceof TNTPrimed) primedTnt++;
                if (entity instanceof LivingEntity) living++;
            }

            int effects = player.getActivePotionEffects().size();
            boolean bedrock = isBedrock(player.getUniqueId());
            String line = String.format(Locale.US,
                    "[%s] player=%s client=%s ping=%dms tps=%.2f hitsOut=%d hitsIn=%d projectilesLaunched=%d nearbyLiving=%d nearbyProjectiles=%d nearbyTNT=%d potionEffects=%d explosionsNear=%d opponent=%s",
                    LocalTime.now().format(CLOCK), player.getName(), bedrock ? "Bedrock" : "Java/unknown",
                    player.getPing(), tps, data.hitsDealt, data.hitsReceived, data.projectilesLaunched,
                    living, projectiles, primedTnt, effects, data.nearbyExplosions,
                    data.lastOpponent == null ? "unknown" : data.lastOpponent);

            recentReports.addLast(line);
            int maxLines = Math.max(5, getConfig().getInt("diagnostics.max-report-lines", 30));
            while (recentReports.size() > maxLines) recentReports.removeFirst();
            linesToSave.add(line);

            data.hitsDealt = 0;
            data.hitsReceived = 0;
            data.projectilesLaunched = 0;
            data.nearbyExplosions = 0;
        }

        if (linesToSave.isEmpty()) return;
        if (getConfig().getBoolean("diagnostics.log-to-console", true)) {
            linesToSave.forEach(getLogger()::info);
        }
        if (getConfig().getBoolean("diagnostics.save-to-file", true)) {
            List<String> copy = List.copyOf(linesToSave);
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> appendReport(copy));
        }
    }

    private boolean isBedrock(UUID uuid) {
        if (floodgateApi == null || floodgateCheck == null) return false;
        try {
            return Boolean.TRUE.equals(floodgateCheck.invoke(floodgateApi, uuid));
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Falha ao consultar Floodgate: " + exception.getClass().getSimpleName());
            return false;
        }
    }

    private void appendReport(List<String> lines) {
        try {
            Path file = getDataFolder().toPath().resolve("diagnostics.log");
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            getLogger().warning("Nao foi possivel salvar diagnostics.log: " + exception.getMessage());
        }
    }

    private void sendStatus(CommandSender sender, Player target) {
        CombatData data = combat.get(target.getUniqueId());
        boolean active = data != null && System.currentTimeMillis() - data.lastCombatAt
                <= Math.max(1L, getConfig().getLong("sampling.combat-window-seconds", 15L)) * SECOND;
        sender.sendMessage(prefix + color("&fJogador: &b" + target.getName()));
        sender.sendMessage(prefix + color("&fCliente: &b" + (isBedrock(target.getUniqueId()) ? "Bedrock (Floodgate)" : "Java/nao identificado")));
        sender.sendMessage(prefix + color("&fPing: &b" + target.getPing() + "ms &8| &fEm combate: " + (active ? "&aSim" : "&7Nao")));
        if (data != null) sender.sendMessage(prefix + color("&fUltimo adversario: &b" + (data.lastOpponent == null ? "desconhecido" : data.lastOpponent)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            if (args.length >= 2) {
                if (!sender.hasPermission("resenhaantifreeze.admin")) {
                    sender.sendMessage(prefix + color("&cSem permissao."));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(prefix + color("&cJogador offline ou nome incorreto."));
                    return true;
                }
                sendStatus(sender, target);
            } else if (sender instanceof Player player) {
                sendStatus(sender, player);
            } else {
                sender.sendMessage(prefix + color("&fUse &b/antifreeze status <jogador>&f."));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("report")) {
            if (!sender.hasPermission("resenhaantifreeze.admin")) {
                sender.sendMessage(prefix + color("&cSem permissao."));
                return true;
            }
            if (recentReports.isEmpty()) {
                sender.sendMessage(prefix + color("&eAinda nao ha amostras de combate. Entre em PvP para gerar diagnosticos."));
                return true;
            }
            sender.sendMessage(prefix + color("&bUltimas amostras de combate:"));
            for (String line : recentReports) sender.sendMessage(color("&7" + line));
            sender.sendMessage(prefix + color("&7Arquivo: plugins/ResenhaAntiFreeze/diagnostics.log"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("resenhaantifreeze.admin")) {
                sender.sendMessage(prefix + color("&cSem permissao."));
                return true;
            }
            reloadConfig();
            prefix = color(getConfig().getString("messages.prefix", "&8[&bAntiFreeze&8] "));
            if (sampler != null) sampler.cancel();
            long interval = Math.max(1L, getConfig().getLong("sampling.interval-seconds", 5L)) * 20L;
            sampler = Bukkit.getScheduler().runTaskTimer(this, this::sampleCombat, interval, interval);
            sender.sendMessage(prefix + color("&aConfiguracao recarregada."));
            return true;
        }

        sender.sendMessage(prefix + color("&fComandos: &b/antifreeze status [jogador]&f, &b/antifreeze report&f, &b/antifreeze reload"));
        return true;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private static final class CombatData {
        private long lastCombatAt;
        private int hitsDealt;
        private int hitsReceived;
        private int projectilesLaunched;
        private int nearbyExplosions;
        private String lastOpponent;
    }
}
