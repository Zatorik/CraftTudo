package br.com.puraresenha.limpachao;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class LimpaChaoPlugin extends JavaPlugin {
    private BukkitTask timerTask;
    private int secondsUntilClear;
    private Set<Integer> countdownSeconds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        registerCommand();
        startTimer();

        getLogger().info("LimpaChao ativado. Próxima limpeza em "
                + secondsUntilClear + " segundos.");
    }

    @Override
    public void onDisable() {
        if (timerTask != null) {
            timerTask.cancel();
        }
    }

    private void registerCommand() {
        if (getCommand("limpachao") == null) {
            getLogger().severe("Comando limpachao não encontrado no plugin.yml!");
            return;
        }

        getCommand("limpachao").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("limpachao.admin")) {
                sender.sendMessage(color(getConfig().getString(
                        "messages.no-permission", "&cSem permissão.")));
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadSettings();
                startTimer();
                sender.sendMessage(color(getConfig().getString(
                        "messages.reloaded", "&aConfiguração recarregada.")));
                return true;
            }

            sender.sendMessage(color(getConfig().getString(
                    "messages.usage", "&eUse: /limpachao reload")));
            return true;
        });
    }

    private void loadSettings() {
        int intervalMinutes = Math.max(1, getConfig().getInt("interval-minutes", 5));
        secondsUntilClear = intervalMinutes * 60;

        List<Integer> configured = getConfig().getIntegerList("countdown-seconds");
        if (configured.isEmpty()) {
            configured = List.of(30, 20, 10, 5, 4, 3, 2, 1);
        }

        countdownSeconds = new HashSet<>();
        for (Integer second : configured) {
            if (second != null && second > 0 && second < intervalMinutes * 60) {
                countdownSeconds.add(second);
            }
        }
    }

    private void startTimer() {
        if (timerTask != null) {
            timerTask.cancel();
        }

        timerTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            secondsUntilClear--;

            if (countdownSeconds.contains(secondsUntilClear)) {
                String message = getConfig().getString(
                        "messages.warning", "&eLimpeza em &c{time}&e.");
                broadcast(message.replace("{time}", secondsUntilClear + "s"));
            }

            if (secondsUntilClear <= 0) {
                clearDroppedItems();
                secondsUntilClear = Math.max(1,
                        getConfig().getInt("interval-minutes", 5)) * 60;
            }
        }, 20L, 20L);
    }

    private void clearDroppedItems() {
        int removed = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item) {
                    entity.remove();
                    removed++;
                }
            }
        }

        String message = getConfig().getString(
                "messages.cleaned", "&aChão limpo! {amount} itens removidos.");
        broadcast(message.replace("{amount}", String.valueOf(removed)));
        getLogger().info("Limpeza concluída: " + removed + " item(ns) removido(s).");
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(color(message));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
