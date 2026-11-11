package br.com.puraresenha.resenhaclans;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ResenhaClansPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Clan> clans = new LinkedHashMap<>();
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();
    private final Map<UUID, Map<UUID, Invite>> invites = new HashMap<>();
    private final Map<String, Relation> relations = new HashMap<>();
    private final Set<UUID> clanChat = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private final Map<UUID, PlayerSettings> playerSettings = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastTellTarget = new ConcurrentHashMap<>();
    private final Map<UUID, String> clanTagsSnapshot = new ConcurrentHashMap<>();

    private File dataFile;
    private NamespacedKey menuActionKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        menuActionKey = new NamespacedKey(this, "menu_action");
        dataFile = new File(getDataFolder(), "clans.yml");
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Não foi possível criar a pasta de dados.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        loadData();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("clan") == null) {
            getLogger().severe("Comando /clan não está definido em plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("clan").setExecutor(this);
        getCommand("clan").setTabCompleter(this);
        for (String commandName : List.of("clanchat", "g", "msg", "reply", "ignore", "unignore", "ignorelist", "chat")) {
            if (getCommand(commandName) == null) {
                getLogger().severe("Comando /" + commandName + " não está definido em plugin.yml.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            getCommand(commandName).setExecutor(this);
            getCommand(commandName).setTabCompleter(this);
        }
        for (Player online : Bukkit.getOnlinePlayers()) updateTabName(online);
        getLogger().info("ResenhaClans " + getDescription().getVersion() + " carregado. Clans: " + clans.size());
    }

    @Override
    public void onDisable() {
        for (PendingTeleport pending : new ArrayList<>(pendingTeleports.values())) pending.task.cancel();
        pendingTeleports.clear();
        saveData();
    }

    // --------------------------- Persistência ----------------------------

    private void loadData() {
        clans.clear();
        playerStats.clear();
        invites.clear();
        relations.clear();
        playerSettings.clear();
        clanTagsSnapshot.clear();
        YamlConfiguration stored = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection clanSection = stored.getConfigurationSection("clans");
        if (clanSection != null) {
            for (String rawId : clanSection.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(rawId);
                    ConfigurationSection section = clanSection.getConfigurationSection(rawId);
                    if (section == null) continue;
                    UUID leader = UUID.fromString(section.getString("leader", ""));
                    Clan clan = new Clan(id, section.getString("name", "Clan"),
                            section.getString("tag", "CLAN"), section.getString("description", ""),
                            leader, section.getLong("created-at", System.currentTimeMillis()),
                            section.getInt("points", 0), section.getBoolean("friendly-fire", false));
                    ConfigurationSection members = section.getConfigurationSection("members");
                    if (members != null) {
                        for (String rawMember : members.getKeys(false)) {
                            try {
                                UUID memberId = UUID.fromString(rawMember);
                                ConfigurationSection memberSection = members.getConfigurationSection(rawMember);
                                if (memberSection == null) continue;
                                clan.members.put(memberId, new Member(parseRank(memberSection.getString("rank", "MEMBER")),
                                        memberSection.getString("name", "Jogador"), memberSection.getInt("kills", 0),
                                        memberSection.getInt("deaths", 0), memberSection.getLong("joined-at", clan.createdAt)));
                            } catch (IllegalArgumentException ignored) {
                                getLogger().warning("UUID de membro inválido em " + rawId);
                            }
                        }
                    }
                    if (!clan.members.containsKey(leader)) {
                        clan.members.put(leader, new Member(Rank.LEADER, "Líder", 0, 0, clan.createdAt));
                    }
                    for (Map.Entry<UUID, Member> entry : clan.members.entrySet()) {
                        entry.getValue().rank = entry.getKey().equals(leader) ? Rank.LEADER :
                                (entry.getValue().rank == Rank.LEADER ? Rank.MEMBER : entry.getValue().rank);
                    }
                    String worldName = section.getString("home.world");
                    if (worldName != null) {
                        clan.home = new Home(worldName, section.getDouble("home.x"), section.getDouble("home.y"),
                                section.getDouble("home.z"), (float) section.getDouble("home.yaw"),
                                (float) section.getDouble("home.pitch"));
                    }
                    clans.put(id, clan);
                } catch (IllegalArgumentException exception) {
                    getLogger().warning("Clan com UUID inválido ignorado: " + rawId);
                }
            }
        }

        ConfigurationSection statsSection = stored.getConfigurationSection("players");
        if (statsSection != null) {
            for (String rawId : statsSection.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(rawId);
                    ConfigurationSection section = statsSection.getConfigurationSection(rawId);
                    if (section != null) playerStats.put(id, new PlayerStats(section.getString("name", "Jogador"),
                            section.getInt("kills", 0), section.getInt("deaths", 0)));
                } catch (IllegalArgumentException ignored) {
                    getLogger().warning("Registro de jogador inválido: " + rawId);
                }
            }
        }

        ConfigurationSection inviteSection = stored.getConfigurationSection("invites");
        if (inviteSection != null) {
            for (String rawTarget : inviteSection.getKeys(false)) {
                try {
                    UUID target = UUID.fromString(rawTarget);
                    ConfigurationSection targetSection = inviteSection.getConfigurationSection(rawTarget);
                    if (targetSection == null) continue;
                    Map<UUID, Invite> targetInvites = new HashMap<>();
                    for (String rawClan : targetSection.getKeys(false)) {
                        try {
                            UUID clanId = UUID.fromString(rawClan);
                            ConfigurationSection inviteData = targetSection.getConfigurationSection(rawClan);
                            if (inviteData == null) continue;
                            Invite invite = new Invite(UUID.fromString(inviteData.getString("inviter", "")),
                                    inviteData.getLong("expires-at", 0L));
                            if (invite.expiresAt > System.currentTimeMillis() && clans.containsKey(clanId)) {
                                targetInvites.put(clanId, invite);
                            }
                        } catch (IllegalArgumentException ignored) {
                            getLogger().warning("Convite inválido ignorado.");
                        }
                    }
                    if (!targetInvites.isEmpty()) invites.put(target, targetInvites);
                } catch (IllegalArgumentException ignored) {
                    getLogger().warning("Destino de convite inválido ignorado.");
                }
            }
        }

        ConfigurationSection relationSection = stored.getConfigurationSection("relations");
        if (relationSection != null) {
            for (String key : relationSection.getKeys(false)) {
                try {
                    ConfigurationSection section = relationSection.getConfigurationSection(key);
                    if (section == null) continue;
                    UUID a = UUID.fromString(section.getString("a", ""));
                    UUID b = UUID.fromString(section.getString("b", ""));
                    if (!clans.containsKey(a) || !clans.containsKey(b) || a.equals(b)) continue;
                    RelationType type = RelationType.valueOf(section.getString("type", "ALLY_PENDING"));
                    UUID requestedBy = UUID.fromString(section.getString("requested-by", a.toString()));
                    relations.put(relationKey(a, b), new Relation(a, b, type, requestedBy));
                } catch (IllegalArgumentException ignored) {
                    getLogger().warning("Relação inválida ignorada: " + key);
                }
            }
        }

        ConfigurationSection preferencesSection = stored.getConfigurationSection("preferences");
        if (preferencesSection != null) {
            for (String rawPlayerId : preferencesSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(rawPlayerId);
                    ConfigurationSection section = preferencesSection.getConfigurationSection(rawPlayerId);
                    if (section == null) continue;
                    PlayerSettings settings = new PlayerSettings();
                    settings.globalChat = section.getBoolean("global-chat", true);
                    settings.clanChat = section.getBoolean("clan-chat", true);
                    settings.receiveTells = section.getBoolean("receive-tells", true);
                    settings.activity = section.getBoolean("activity", true);
                    settings.invites = section.getBoolean("invites", true);
                    settings.diplomacy = section.getBoolean("diplomacy", true);
                    settings.wars = section.getBoolean("wars", true);
                    settings.announcements = section.getBoolean("announcements", true);
                    for (String rawIgnored : section.getStringList("ignored")) {
                        try { settings.ignored.add(UUID.fromString(rawIgnored)); }
                        catch (IllegalArgumentException ignored) { getLogger().warning("UUID bloqueado inválido em " + rawPlayerId); }
                    }
                    playerSettings.put(playerId, settings);
                } catch (IllegalArgumentException exception) {
                    getLogger().warning("Preferências inválidas ignoradas: " + rawPlayerId);
                }
            }
        }

        Set<UUID> assigned = new HashSet<>();
        for (Clan clan : clans.values()) {
            clan.members.keySet().removeIf(memberId -> !assigned.add(memberId));
            for (UUID memberId : clan.members.keySet()) clanTagsSnapshot.put(memberId, clan.tag);
        }
        long now = System.currentTimeMillis();
        invites.values().forEach(map -> map.values().removeIf(invite -> invite.expiresAt <= now));
        invites.values().removeIf(Map::isEmpty);
    }

    private void saveData() {
        if (dataFile == null) return;
        YamlConfiguration out = new YamlConfiguration();

        for (Clan clan : clans.values()) {
            String base = "clans." + clan.id;
            out.set(base + ".name", clan.name);
            out.set(base + ".tag", clan.tag);
            out.set(base + ".description", clan.description);
            out.set(base + ".leader", clan.leader.toString());
            out.set(base + ".created-at", clan.createdAt);
            out.set(base + ".points", clan.points);
            out.set(base + ".friendly-fire", clan.friendlyFire);
            for (Map.Entry<UUID, Member> entry : clan.members.entrySet()) {
                String memberPath = base + ".members." + entry.getKey();
                Member member = entry.getValue();
                out.set(memberPath + ".rank", member.rank.name());
                out.set(memberPath + ".name", member.lastName);
                out.set(memberPath + ".kills", member.kills);
                out.set(memberPath + ".deaths", member.deaths);
                out.set(memberPath + ".joined-at", member.joinedAt);
            }
            if (clan.home != null) {
                out.set(base + ".home.world", clan.home.world);
                out.set(base + ".home.x", clan.home.x);
                out.set(base + ".home.y", clan.home.y);
                out.set(base + ".home.z", clan.home.z);
                out.set(base + ".home.yaw", clan.home.yaw);
                out.set(base + ".home.pitch", clan.home.pitch);
            }
        }
        for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
            String base = "players." + entry.getKey();
            out.set(base + ".name", entry.getValue().name);
            out.set(base + ".kills", entry.getValue().kills);
            out.set(base + ".deaths", entry.getValue().deaths);
        }
        for (Map.Entry<UUID, Map<UUID, Invite>> targetEntry : invites.entrySet()) {
            for (Map.Entry<UUID, Invite> inviteEntry : targetEntry.getValue().entrySet()) {
                String base = "invites." + targetEntry.getKey() + "." + inviteEntry.getKey();
                out.set(base + ".inviter", inviteEntry.getValue().inviter.toString());
                out.set(base + ".expires-at", inviteEntry.getValue().expiresAt);
            }
        }
        for (Relation relation : relations.values()) {
            String base = "relations." + relationKey(relation.a, relation.b);
            out.set(base + ".a", relation.a.toString());
            out.set(base + ".b", relation.b.toString());
            out.set(base + ".type", relation.type.name());
            out.set(base + ".requested-by", relation.requestedBy.toString());
        }
        for (Map.Entry<UUID, PlayerSettings> entry : playerSettings.entrySet()) {
            String base = "preferences." + entry.getKey();
            PlayerSettings settings = entry.getValue();
            out.set(base + ".global-chat", settings.globalChat);
            out.set(base + ".clan-chat", settings.clanChat);
            out.set(base + ".receive-tells", settings.receiveTells);
            out.set(base + ".activity", settings.activity);
            out.set(base + ".invites", settings.invites);
            out.set(base + ".diplomacy", settings.diplomacy);
            out.set(base + ".wars", settings.wars);
            out.set(base + ".announcements", settings.announcements);
            out.set(base + ".ignored", settings.ignored.stream().map(UUID::toString).toList());
        }

        File temp = new File(getDataFolder(), "clans.yml.tmp");
        try {
            out.save(temp);
            try {
                Files.move(temp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicNotSupported) {
                Files.move(temp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            getLogger().severe("Falha ao salvar clans.yml: " + exception.getMessage());
        }
    }

    // ------------------------------ Eventos ------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerStats stats = playerStats.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerStats(player.getName(), 0, 0));
        stats.name = player.getName();
        Clan clan = findClan(player.getUniqueId());
        if (clan != null && clan.members.containsKey(player.getUniqueId())) clan.members.get(player.getUniqueId()).lastName = player.getName();
        updateTabName(player);
        saveData();
        if (clan != null && getConfig().getBoolean("messages.join-quit", true)) {
            broadcastClanPreference(clan, Component.text(player.getName() + " entrou no servidor.", NamedTextColor.GRAY), "activity");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clanChat.remove(event.getPlayer().getUniqueId());
        lastTellTarget.remove(event.getPlayer().getUniqueId());
        cancelPendingTeleport(event.getPlayer().getUniqueId(), null);
        Clan clan = findClan(event.getPlayer().getUniqueId());
        if (clan != null && getConfig().getBoolean("messages.join-quit", true)) {
            broadcastClanPreference(clan, Component.text(event.getPlayer().getName() + " saiu do servidor.", NamedTextColor.GRAY), "activity");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = getPlayerDamager(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        Clan victimClan = findClan(victim.getUniqueId());
        Clan attackerClan = findClan(attacker.getUniqueId());
        if (victimClan == null || attackerClan == null) return;
        if (victimClan.id.equals(attackerClan.id) && !victimClan.friendlyFire) {
            event.setCancelled(true);
            attacker.sendMessage(Component.text("Fogo amigo está desativado no seu clan.", NamedTextColor.RED));
            return;
        }
        Relation relation = getRelation(victimClan.id, attackerClan.id);
        if (relation != null && relation.type == RelationType.ALLY
                && getConfig().getBoolean("pvp.protect-allies", true)) {
            event.setCancelled(true);
            attacker.sendMessage(Component.text("Você não pode atacar um clan aliado.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        PlayerStats victimStats = getPlayerStats(victim.getUniqueId(), victim.getName());
        victimStats.deaths++;
        Clan victimClan = findClan(victim.getUniqueId());
        if (victimClan != null) {
            Member victimMember = victimClan.members.get(victim.getUniqueId());
            if (victimMember != null) victimMember.deaths++;
        }
        if (killer != null && !killer.equals(victim)) {
            PlayerStats killerStats = getPlayerStats(killer.getUniqueId(), killer.getName());
            killerStats.kills++;
            Clan killerClan = findClan(killer.getUniqueId());
            if (killerClan != null) {
                Member killerMember = killerClan.members.get(killer.getUniqueId());
                if (killerMember != null) killerMember.kills++;
            }
            if (killerClan != null && victimClan != null && !killerClan.id.equals(victimClan.id)) {
                Relation relation = getRelation(killerClan.id, victimClan.id);
                if (relation != null && relation.type == RelationType.WAR) {
                    killerClan.points++;
                    broadcastClanPreference(killerClan, Component.text("+1 ponto de guerra por eliminar " + victim.getName() + ".", NamedTextColor.GOLD), "wars");
                }
            }
        }
        saveData();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPendingTeleportDamage(EntityDamageEvent event) {
        if (getConfig().getBoolean("home.cancel-on-damage", true)
                && event.getEntity() instanceof Player player
                && pendingTeleports.containsKey(player.getUniqueId())) {
            cancelPendingTeleport(player.getUniqueId(), "Teleporte cancelado: você recebeu dano.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!getConfig().getBoolean("home.cancel-on-move", true) || event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (pendingTeleports.containsKey(event.getPlayer().getUniqueId())) {
            cancelPendingTeleport(event.getPlayer().getUniqueId(), "Teleporte cancelado: você se moveu.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        UUID senderId = sender.getUniqueId();

        if (clanChat.contains(senderId)) {
            event.setCancelled(true);
            Component message = event.message();
            Bukkit.getScheduler().runTask(this, () -> deliverClanChat(senderId, message));
            return;
        }

        // Preferences and the tag cache are concurrent because player chat may be asynchronous.
        event.viewers().removeIf(viewer -> {
            if (!(viewer instanceof Player recipient)) return false;
            PlayerSettings prefs = settings(recipient.getUniqueId());
            return !prefs.globalChat || prefs.ignored.contains(senderId);
        });

        String tag = clanTagsSnapshot.get(senderId);
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component display = tag == null ? sourceDisplayName :
                    Component.text("[" + tag + "] ", NamedTextColor.GOLD).append(sourceDisplayName);
            return display.append(Component.text(" » ", NamedTextColor.DARK_GRAY)).append(message);
        });
    }

    private void deliverClanChat(UUID senderId, Component message) {
        Player sender = Bukkit.getPlayer(senderId);
        if (sender == null || !sender.isOnline()) return;
        Clan clan = findClan(senderId);
        if (clan == null) {
            clanChat.remove(senderId);
            error(sender, "Você não pertence a um clan. Use /g para voltar ao chat global.");
            return;
        }

        Component formatted = Component.text("[CLAN] ", NamedTextColor.GOLD)
                .append(Component.text("[" + clan.tag + "] ", NamedTextColor.YELLOW))
                .append(Component.text(sender.getName() + " » ", NamedTextColor.WHITE))
                .append(message);
        for (UUID memberId : clan.members.keySet()) {
            Player recipient = Bukkit.getPlayer(memberId);
            if (recipient == null) continue;
            PlayerSettings prefs = settings(memberId);
            if (memberId.equals(senderId) || (prefs.clanChat && !prefs.ignored.contains(senderId))) {
                recipient.sendMessage(formatted);
            }
        }
    }

    // --------------------------- Comando /clan ---------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("clanchat")) {
            if (!(sender instanceof Player player)) { error(sender, "Somente jogadores podem usar o chat de clan."); return true; }
            toggleClanChat(player, args);
            return true;
        }
        if (commandName.equals("g")) {
            if (!(sender instanceof Player player)) { error(sender, "Somente jogadores podem usar o chat global."); return true; }
            clanChat.remove(player.getUniqueId());
            success(player, "Chat global ativado. Suas próximas mensagens irão para todos.");
            return true;
        }
        if (commandName.equals("msg")) {
            if (!(sender instanceof Player player)) { error(sender, "Somente jogadores podem enviar mensagens privadas."); return true; }
            sendPrivateMessage(player, args);
            return true;
        }
        if (commandName.equals("reply")) {
            if (!(sender instanceof Player player)) { error(sender, "Somente jogadores podem responder mensagens."); return true; }
            replyPrivateMessage(player, args);
            return true;
        }
        if (commandName.equals("ignore")) {
            if (!(sender instanceof Player player)) { error(sender, "Somente jogadores podem bloquear mensagens."); return true; }
            ignorePlayer(player, args);
            return true;
        }
        if (commandName.equals("unignore")) {
            if (!(sender instanceof Player player)) { error(sender, "Somente jogadores podem desbloquear mensagens."); return true; }
            unignorePlayer(player, args);
            return true;
        }
        if (commandName.equals("ignorelist")) {
            if (!(sender instanceof Player player)) { error(sender, "Somente jogadores podem ver sua lista de bloqueios."); return true; }
            showIgnoreList(player);
            return true;
        }
        if (commandName.equals("chat")) {
            if (!(sender instanceof Player player)) { error(sender, "Somente jogadores podem abrir as configurações de chat."); return true; }
            if (args.length >= 2 && args[0].equalsIgnoreCase("toggle")) toggleChatPreference(player, args[1]);
            else openChatSettings(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("resenhaclans.admin")) { error(sender, "Você não tem permissão."); return true; }
            reloadConfig();
            sender.sendMessage(Component.text("Configuração recarregada.", NamedTextColor.GREEN));
            return true;
        }
        if (sub.equals("top")) { showTop(sender); return true; }
        if (sub.equals("list") || sub.equals("listar")) { showClanList(sender); return true; }
        if (sub.equals("menu")) {
            if (sender instanceof Player player) openMenu(player);
            else error(sender, "Esse comando só pode ser usado por um jogador.");
            return true;
        }
        if (sub.equals("create") || sub.equals("criar")) { createClan(sender, args); return true; }
        if (!(sender instanceof Player player)) { error(sender, "Esse comando só pode ser usado por um jogador."); return true; }
        Clan clan = findClan(player.getUniqueId());

        switch (sub) {
            case "help", "ajuda" -> sendHelp(sender);
            case "info" -> showInfo(sender, args.length >= 2 ? findClanByTag(args[1]) : clan);
            case "members", "membros" -> showMembers(sender, clan);
            case "invite", "convidar" -> invitePlayer(player, clan, args);
            case "accept", "aceitar" -> acceptInvite(player, args);
            case "deny", "recusar" -> denyInvite(player, args);
            case "leave", "sair" -> leaveClan(player, clan);
            case "kick", "expulsar" -> kickMember(player, clan, args);
            case "promote", "promover" -> changeRank(player, clan, args, true);
            case "demote", "rebaixar" -> changeRank(player, clan, args, false);
            case "transfer", "transferir" -> transferLeadership(player, clan, args);
            case "disband", "dissolver" -> disband(player, clan, args);
            case "desc", "descricao" -> setDescription(player, clan, args);
            case "announce", "anunciar" -> announce(player, clan, args);
            case "sethome" -> setHome(player, clan);
            case "home" -> goHome(player, clan);
            case "chat" -> toggleClanChat(player, new String[0]);
            case "c" -> sendClanMessage(player, clan, args);
            case "friendlyfire" -> setFriendlyFire(player, clan, args);
            case "ally", "aliar" -> requestAlliance(player, clan, args);
            case "allyaccept", "aceitaralianca" -> acceptAlliance(player, clan, args);
            case "unally", "desaliar" -> removeAlliance(player, clan, args);
            case "allies", "aliados" -> showAllies(player, clan);
            case "war", "guerra" -> declareWar(player, clan, args);
            case "peace", "paz" -> endWar(player, clan, args);
            case "stats", "status" -> showStats(sender, args.length >= 2 ? args[1] : player.getName());
            default -> error(sender, "Subcomando desconhecido. Use /clan ajuda.");
        }
        return true;
    }

    private void createClan(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { error(sender, "Somente jogadores podem criar clans."); return; }
        if (args.length < 3) { error(sender, "Uso: /clan criar <TAG> <nome do clan>"); return; }
        if (findClan(player.getUniqueId()) != null) { error(sender, "Você já pertence a um clan."); return; }
        String tag = args[1].toUpperCase(Locale.ROOT);
        if (!tag.matches("[A-Z0-9]{3,5}")) { error(sender, "A tag deve ter de 3 a 5 caracteres (letras e números)."); return; }
        if (findClanByTag(tag) != null) { error(sender, "Essa tag já está em uso."); return; }
        String name = joinArgs(args, 2);
        if (name.length() < 3 || name.length() > 28 || name.contains("§")) { error(sender, "O nome deve ter de 3 a 28 caracteres."); return; }
        long now = System.currentTimeMillis();
        Clan clan = new Clan(UUID.randomUUID(), name, tag, "", player.getUniqueId(), now, 0, false);
        clan.members.put(player.getUniqueId(), new Member(Rank.LEADER, player.getName(), 0, 0, now));
        clans.put(clan.id, clan);
        getPlayerStats(player.getUniqueId(), player.getName());
        clanTagsSnapshot.put(player.getUniqueId(), clan.tag);
        updateTabName(player);
        saveData();
        player.sendMessage(Component.text("Clan [" + tag + "] " + name + " criado com sucesso!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Você é o líder. Use /clan menu para começar.", NamedTextColor.YELLOW));
    }

    private void invitePlayer(Player actor, Clan clan, String[] args) {
        if (clan == null) { error(actor, "Você não pertence a um clan."); return; }
        if (args.length < 2) { error(actor, "Uso: /clan convidar <jogador>"); return; }
        if (!hasRole(clan, actor.getUniqueId(), Rank.OFFICER)) { error(actor, "Apenas oficiais, colíderes e líder podem convidar."); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { error(actor, "Esse jogador precisa estar online."); return; }
        if (findClan(target.getUniqueId()) != null) { error(actor, "Esse jogador já pertence a um clan."); return; }
        if (clan.members.size() >= getConfig().getInt("clan.max-members", 20)) { error(actor, "O clan atingiu o limite de membros."); return; }
        if (!settings(target.getUniqueId()).invites) { error(actor, "Esse jogador desativou notificações de convites."); return; }
        long expiry = System.currentTimeMillis() + getConfig().getLong("invites.expire-seconds", 300) * 1000L;
        invites.computeIfAbsent(target.getUniqueId(), ignored -> new HashMap<>()).put(clan.id, new Invite(actor.getUniqueId(), expiry));
        saveData();
        success(actor, "Convite enviado para " + target.getName() + ".");
        target.sendMessage(Component.text("Você recebeu um convite do clan [" + clan.tag + "] " + clan.name + ".", NamedTextColor.GOLD));
        target.sendMessage(Component.text("Use /clan aceitar " + clan.tag + " ou /clan recusar " + clan.tag + ".", NamedTextColor.YELLOW));
    }

    private void acceptInvite(Player player, String[] args) {
        if (findClan(player.getUniqueId()) != null) { error(player, "Você já pertence a um clan."); return; }
        Map<UUID, Invite> available = invites.get(player.getUniqueId());
        if (available == null || available.isEmpty()) { error(player, "Você não tem convites pendentes."); return; }
        UUID clanId = resolveInviteClan(player, args.length >= 2 ? args[1] : null);
        if (clanId == null) return;
        Clan clan = clans.get(clanId);
        Invite invite = available.get(clanId);
        if (clan == null || invite == null || invite.expiresAt <= System.currentTimeMillis()) {
            available.remove(clanId); saveData(); error(player, "Esse convite expirou."); return;
        }
        if (clan.members.size() >= getConfig().getInt("clan.max-members", 20)) {
            available.remove(clanId); saveData(); error(player, "O clan está cheio."); return;
        }
        clan.members.put(player.getUniqueId(), new Member(Rank.MEMBER, player.getName(), 0, 0, System.currentTimeMillis()));
        available.remove(clanId);
        if (available.isEmpty()) invites.remove(player.getUniqueId());
        getPlayerStats(player.getUniqueId(), player.getName());
        clanTagsSnapshot.put(player.getUniqueId(), clan.tag);
        updateTabName(player);
        saveData();
        player.sendMessage(Component.text("Você entrou no clan [" + clan.tag + "]!", NamedTextColor.GREEN));
        broadcastClan(clan, Component.text(player.getName() + " entrou para o clan.", NamedTextColor.GREEN));
    }

    private void denyInvite(Player player, String[] args) {
        Map<UUID, Invite> available = invites.get(player.getUniqueId());
        if (available == null || available.isEmpty()) { error(player, "Você não tem convites pendentes."); return; }
        UUID clanId = resolveInviteClan(player, args.length >= 2 ? args[1] : null);
        if (clanId == null) return;
        available.remove(clanId);
        if (available.isEmpty()) invites.remove(player.getUniqueId());
        saveData();
        success(player, "Convite recusado.");
    }

    private UUID resolveInviteClan(Player player, String requestedTag) {
        Map<UUID, Invite> available = invites.get(player.getUniqueId());
        if (available == null || available.isEmpty()) return null;
        if (requestedTag != null) {
            Clan selected = findClanByTag(requestedTag);
            if (selected == null || !available.containsKey(selected.id)) { error(player, "Não existe convite desse clan."); return null; }
            return selected.id;
        }
        List<UUID> valid = available.keySet().stream().filter(clans::containsKey).toList();
        if (valid.size() != 1) { error(player, "Você tem vários convites. Informe a tag: /clan aceitar <TAG>."); return null; }
        return valid.get(0);
    }

    private void leaveClan(Player player, Clan clan) {
        if (clan == null) { error(player, "Você não pertence a um clan."); return; }
        if (clan.leader.equals(player.getUniqueId())) { error(player, "O líder precisa transferir a liderança ou dissolver o clan."); return; }
        clan.members.remove(player.getUniqueId());
        clanChat.remove(player.getUniqueId());
        clanTagsSnapshot.remove(player.getUniqueId());
        player.playerListName(null);
        saveData();
        player.sendMessage(Component.text("Você saiu do clan [" + clan.tag + "].", NamedTextColor.YELLOW));
        broadcastClan(clan, Component.text(player.getName() + " saiu do clan.", NamedTextColor.GRAY));
    }

    private void kickMember(Player actor, Clan clan, String[] args) {
        if (clan == null) { error(actor, "Você não pertence a um clan."); return; }
        if (args.length < 2) { error(actor, "Uso: /clan expulsar <jogador>"); return; }
        Member actorMember = clan.members.get(actor.getUniqueId());
        if (actorMember == null || actorMember.rank.level < Rank.OFFICER.level) { error(actor, "Você não tem permissão."); return; }
        UUID targetId = findMemberByName(clan, args[1]);
        if (targetId == null) { error(actor, "Jogador não encontrado no seu clan."); return; }
        Member targetMember = clan.members.get(targetId);
        if (targetId.equals(actor.getUniqueId()) || targetMember.rank == Rank.LEADER || targetMember.rank.level >= actorMember.rank.level) {
            error(actor, "Você não pode expulsar esse membro."); return;
        }
        clan.members.remove(targetId);
        clanChat.remove(targetId);
        clanTagsSnapshot.remove(targetId);
        saveData();
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            target.playerListName(null);
            target.sendMessage(Component.text("Você foi removido do clan [" + clan.tag + "].", NamedTextColor.RED));
        }
        broadcastClan(clan, Component.text(args[1] + " foi removido do clan.", NamedTextColor.YELLOW));
    }

    private void changeRank(Player actor, Clan clan, String[] args, boolean promote) {
        if (clan == null) { error(actor, "Você não pertence a um clan."); return; }
        if (!clan.leader.equals(actor.getUniqueId())) { error(actor, "Somente o líder pode alterar cargos."); return; }
        if (args.length < 2) { error(actor, "Uso: /clan " + (promote ? "promover" : "rebaixar") + " <jogador>"); return; }
        UUID targetId = findMemberByName(clan, args[1]);
        if (targetId == null || targetId.equals(clan.leader)) { error(actor, "Membro não encontrado ou cargo protegido."); return; }
        Member member = clan.members.get(targetId);
        if (promote) {
            member.rank = switch (member.rank) { case MEMBER -> Rank.OFFICER; case OFFICER -> Rank.COLEADER; default -> member.rank; };
        } else {
            member.rank = switch (member.rank) { case COLEADER -> Rank.OFFICER; case OFFICER -> Rank.MEMBER; default -> member.rank; };
        }
        saveData();
        broadcastClan(clan, Component.text(args[1] + " agora é " + member.rank.display + ".", NamedTextColor.GOLD));
    }

    private void transferLeadership(Player actor, Clan clan, String[] args) {
        if (clan == null) { error(actor, "Você não pertence a um clan."); return; }
        if (!clan.leader.equals(actor.getUniqueId())) { error(actor, "Somente o líder pode transferir a liderança."); return; }
        if (args.length < 2) { error(actor, "Uso: /clan transferir <jogador>"); return; }
        UUID targetId = findMemberByName(clan, args[1]);
        if (targetId == null || targetId.equals(actor.getUniqueId())) { error(actor, "O jogador precisa ser membro do seu clan."); return; }
        clan.members.get(actor.getUniqueId()).rank = Rank.COLEADER;
        clan.members.get(targetId).rank = Rank.LEADER;
        clan.leader = targetId;
        saveData();
        broadcastClan(clan, Component.text("A liderança foi transferida para " + clan.members.get(targetId).lastName + ".", NamedTextColor.GOLD));
    }

    private void disband(Player actor, Clan clan, String[] args) {
        if (clan == null) { error(actor, "Você não pertence a um clan."); return; }
        if (!clan.leader.equals(actor.getUniqueId())) { error(actor, "Somente o líder pode dissolver o clan."); return; }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            error(actor, "Isso apagará o clan e suas relações. Confirme com /clan dissolver confirm."); return;
        }
        Component message = Component.text("O clan [" + clan.tag + "] foi dissolvido.", NamedTextColor.RED);
        for (UUID member : clan.members.keySet()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                online.sendMessage(message);
                online.playerListName(null);
            }
            clanChat.remove(member);
            clanTagsSnapshot.remove(member);
        }
        clans.remove(clan.id);
        relations.values().removeIf(relation -> relation.a.equals(clan.id) || relation.b.equals(clan.id));
        invites.values().forEach(map -> map.remove(clan.id));
        invites.values().removeIf(Map::isEmpty);
        saveData();
    }

    private void setDescription(Player actor, Clan clan, String[] args) {
        if (clan == null) { error(actor, "Você não pertence a um clan."); return; }
        if (!hasRole(clan, actor.getUniqueId(), Rank.COLEADER)) { error(actor, "Somente líder ou colíder pode alterar a descrição."); return; }
        if (args.length < 2) { error(actor, "Uso: /clan desc <texto>"); return; }
        String description = joinArgs(args, 1);
        if (description.length() > 120) { error(actor, "A descrição pode ter no máximo 120 caracteres."); return; }
        clan.description = description;
        saveData();
        success(actor, "Descrição atualizada.");
    }

    private void announce(Player actor, Clan clan, String[] args) {
        if (clan == null) { error(actor, "Você não pertence a um clan."); return; }
        if (!hasRole(clan, actor.getUniqueId(), Rank.COLEADER)) { error(actor, "Somente líder ou colíder pode anunciar."); return; }
        if (args.length < 2) { error(actor, "Uso: /clan anunciar <mensagem>"); return; }
        Component message = Component.text("[ANÚNCIO] [" + clan.tag + "] " + joinArgs(args, 1), NamedTextColor.GOLD);
        broadcastGlobalPreference(message, "announcements");
    }

    private void setHome(Player player, Clan clan) {
        if (clan == null) { error(player, "Você não pertence a um clan."); return; }
        if (!hasRole(clan, player.getUniqueId(), Rank.COLEADER)) { error(player, "Somente líder ou colíder pode definir a base."); return; }
        Location loc = player.getLocation();
        clan.home = new Home(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        saveData();
        success(player, "Base do clan definida.");
    }

    private void goHome(Player player, Clan clan) {
        if (clan == null) { error(player, "Você não pertence a um clan."); return; }
        if (clan.home == null) { error(player, "Seu clan ainda não definiu uma base."); return; }
        if (pendingTeleports.containsKey(player.getUniqueId())) { error(player, "Você já tem um teleporte pendente."); return; }
        World world = Bukkit.getWorld(clan.home.world);
        if (world == null) { error(player, "O mundo da base não está carregado."); return; }
        Location target = new Location(world, clan.home.x, clan.home.y, clan.home.z, clan.home.yaw, clan.home.pitch);
        int delay = Math.max(0, getConfig().getInt("home.teleport-delay-seconds", 3));
        Location origin = player.getLocation().clone();
        boolean cancelOnMove = getConfig().getBoolean("home.cancel-on-move", true);
        UUID clanId = clan.id;
        if (delay == 0) { player.teleport(target); return; }
        player.sendMessage(Component.text("Teleportando em " + delay + " segundos. Não se mova e não receba dano.", NamedTextColor.YELLOW));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
            if (pending == null || !player.isOnline()) return;
            Clan currentClan = findClan(player.getUniqueId());
            if (currentClan == null || !currentClan.id.equals(clanId)) {
                player.sendMessage(Component.text("Teleporte cancelado: seu clan mudou.", NamedTextColor.RED)); return;
            }
            if (player.getWorld() != origin.getWorld()
                    || (cancelOnMove && player.getLocation().distanceSquared(origin) > 0.04)) {
                player.sendMessage(Component.text("Teleporte cancelado: sua posição mudou.", NamedTextColor.RED)); return;
            }
            player.teleport(target);
            player.sendMessage(Component.text("Você chegou à base do clan.", NamedTextColor.GREEN));
        }, delay * 20L);
        pendingTeleports.put(player.getUniqueId(), new PendingTeleport(task, origin, clanId));
    }

    private void cancelPendingTeleport(UUID playerId, String message) {
        PendingTeleport pending = pendingTeleports.remove(playerId);
        if (pending == null) return;
        pending.task.cancel();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && message != null) player.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    private void toggleClanChat(Player player, Clan clan) {
        if (clan == null) { error(player, "Você não pertence a um clan."); return; }
        if (clanChat.remove(player.getUniqueId())) success(player, "Chat do clan desativado.");
        else {
            clanChat.add(player.getUniqueId());
            player.sendMessage(Component.text("Chat do clan ativado. Suas mensagens agora vão só para seu clan.", NamedTextColor.GREEN));
        }
    }

    private void sendClanMessage(Player player, Clan clan, String[] args) {
        if (clan == null) { error(player, "Você não pertence a um clan."); return; }
        if (args.length < 2) { error(player, "Uso: /clan c <mensagem>"); return; }
        Component message = Component.text("[CLAN] [" + clan.tag + "] " + player.getName() + ": ", NamedTextColor.GOLD)
                .append(Component.text(joinArgs(args, 1), NamedTextColor.WHITE));
        broadcastClan(clan, message);
    }

    private void setFriendlyFire(Player player, Clan clan, String[] args) {
        if (clan == null) { error(player, "Você não pertence a um clan."); return; }
        if (!clan.leader.equals(player.getUniqueId())) { error(player, "Somente o líder pode alterar fogo amigo."); return; }
        if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            error(player, "Uso: /clan friendlyfire <on|off>"); return;
        }
        clan.friendlyFire = args[1].equalsIgnoreCase("on");
        saveData();
        success(player, "Fogo amigo " + (clan.friendlyFire ? "ativado" : "desativado") + ".");
    }

    private void requestAlliance(Player actor, Clan clan, String[] args) {
        if (clan == null) { error(actor, "Você não pertence a um clan."); return; }
        if (!hasRole(clan, actor.getUniqueId(), Rank.COLEADER)) { error(actor, "Somente líder ou colíder pode propor alianças."); return; }
        if (args.length < 2) { error(actor, "Uso: /clan ally <TAG>"); return; }
        Clan other = findClanByTag(args[1]);
        if (other == null || other.id.equals(clan.id)) { error(actor, "Clan não encontrado."); return; }
        Relation current = getRelation(clan.id, other.id);
        if (current != null) { error(actor, current.type == RelationType.ALLY ? "Vocês já são aliados." : "Já existe uma relação ativa ou pendente com esse clan."); return; }
        relations.put(relationKey(clan.id, other.id), new Relation(clan.id, other.id, RelationType.ALLY_PENDING, clan.id));
        saveData();
        broadcastClanPreference(clan, Component.text("Proposta de aliança enviada para [" + other.tag + "].", NamedTextColor.GREEN), "diplomacy");
        broadcastClanPreference(other, Component.text("O clan [" + clan.tag + "] propôs uma aliança. Líder/colíder: /clan allyaccept " + clan.tag, NamedTextColor.GOLD), "diplomacy");
    }

    private void acceptAlliance(Player actor, Clan clan, String[] args) {
        if (clan == null) { error(actor, "Você não pertence a um clan."); return; }
        if (!hasRole(clan, actor.getUniqueId(), Rank.COLEADER)) { error(actor, "Somente líder ou colíder pode aceitar alianças."); return; }
        if (args.length < 2) { error(actor, "Uso: /clan allyaccept <TAG>"); return; }
        Clan other = findClanByTag(args[1]);
        if (other == null) { error(actor, "Clan não encontrado."); return; }
        Relation relation = getRelation(clan.id, other.id);
        if (relation == null || relation.type != RelationType.ALLY_PENDING || relation.a.equals(clan.id)) {
            error(actor, "Não existe uma proposta pendente desse clan para você."); return;
        }
        relation.type = RelationType.ALLY;
        relation.requestedBy = clan.id;
        saveData();
        broadcastClanPreference(clan, Component.text("Aliança formada com [" + other.tag + "]!", NamedTextColor.GREEN), "diplomacy");
        broadcastClanPreference(other, Component.text("Aliança formada com [" + clan.tag + "]!", NamedTextColor.GREEN), "diplomacy");
    }

    private void removeAlliance(Player actor, Clan clan, String[] args) {
        if (clan == null) { error(actor, "Você não pertence a um clan."); return; }
        if (!hasRole(clan, actor.getUniqueId(), Rank.COLEADER)) { error(actor, "Somente líder ou colíder pode remover alianças."); return; }
        if (args.length < 2) { error(actor, "Uso: /clan unally <TAG>"); return; }
        Clan other = findClanByTag(args[1]);
        if (other == null) { error(actor, "Clan não encontrado."); return; }
        Relation relation = getRelation(clan.id, other.id);
        if (relation == null || relation.type != RelationType.ALLY) { error(actor, "Vocês não são aliados."); return; }
        relations.remove(relationKey(clan.id, other.id));
        saveData();
        broadcastClanPreference(clan, Component.text("Aliança com [" + other.tag + "] encerrada.", NamedTextColor.YELLOW), "diplomacy");
        broadcastClanPreference(other, Component.text("A aliança com [" + clan.tag + "] foi encerrada.", NamedTextColor.YELLOW), "diplomacy");
    }

    private void showAllies(Player player, Clan clan) {
        if (clan == null) { error(player, "Você não pertence a um clan."); return; }
        List<Clan> allies = relations.values().stream().filter(r -> r.type == RelationType.ALLY)
                .filter(r -> r.a.equals(clan.id) || r.b.equals(clan.id))
                .map(r -> clans.get(r.a.equals(clan.id) ? r.b : r.a)).filter(c -> c != null).toList();
        player.sendMessage(Component.text("Aliados (" + allies.size() + "): " +
                (allies.isEmpty() ? "nenhum" : allies.stream().map(c -> "[" + c.tag + "]").collect(Collectors.joining(", "))),
                NamedTextColor.GOLD));
    }

    private void declareWar(Player actor, Clan clan, String[] args) {
        if (clan == null) { error(actor, "Você não pertence a um clan."); return; }
        if (!hasRole(clan, actor.getUniqueId(), Rank.COLEADER)) { error(actor, "Somente líder ou colíder pode declarar guerra."); return; }
        if (args.length < 2) { error(actor, "Uso: /clan guerra <TAG>"); return; }
        Clan other = findClanByTag(args[1]);
        if (other == null || other.id.equals(clan.id)) { error(actor, "Clan não encontrado."); return; }
        if (getRelation(clan.id, other.id) != null) { error(actor, "Remova a relação atual antes de iniciar uma guerra."); return; }
        relations.put(relationKey(clan.id, other.id), new Relation(clan.id, other.id, RelationType.WAR, clan.id));
        saveData();
        Component announcement = Component.text("GUERRA DECLARADA: [" + clan.tag + "] x [" + other.tag + "]", NamedTextColor.RED, TextDecoration.BOLD);
        broadcastGlobalPreference(announcement, "wars");
    }

    private void endWar(Player actor, Clan clan, String[] args) {
        if (clan == null) { error(actor, "Você não pertence a um clan."); return; }
        if (!hasRole(clan, actor.getUniqueId(), Rank.COLEADER)) { error(actor, "Somente líder ou colíder pode encerrar guerras."); return; }
        if (args.length < 2) { error(actor, "Uso: /clan peace <TAG>"); return; }
        Clan other = findClanByTag(args[1]);
        if (other == null) { error(actor, "Clan não encontrado."); return; }
        Relation relation = getRelation(clan.id, other.id);
        if (relation == null || relation.type != RelationType.WAR) { error(actor, "Não existe guerra ativa com esse clan."); return; }
        relations.remove(relationKey(clan.id, other.id));
        saveData();
        Component announcement = Component.text("GUERRA ENCERRADA: [" + clan.tag + "] x [" + other.tag + "]", NamedTextColor.GREEN);
        broadcastGlobalPreference(announcement, "wars");
    }

    // -------------------------- QoL do chat -----------------------------

    private PlayerSettings settings(UUID playerId) {
        return playerSettings.computeIfAbsent(playerId, ignored -> new PlayerSettings());
    }

    private void toggleClanChat(Player player, String[] args) {
        if (findClan(player.getUniqueId()) == null) {
            clanChat.remove(player.getUniqueId());
            error(player, "Você não pertence a um clan.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("on")) {
            clanChat.add(player.getUniqueId());
        } else if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
            clanChat.remove(player.getUniqueId());
        } else if (!clanChat.add(player.getUniqueId())) {
            clanChat.remove(player.getUniqueId());
        }
        player.sendMessage(Component.text(clanChat.contains(player.getUniqueId())
                ? "Chat do clan ativado. Use /g para voltar ao global."
                : "Chat global ativado.", NamedTextColor.GREEN));
    }

    private void sendPrivateMessage(Player sender, String[] args) {
        if (args.length < 2) {
            error(sender, "Uso: /msg <jogador> <mensagem>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            error(sender, "Jogador não encontrado ou offline.");
            return;
        }
        sendPrivateMessage(sender, target, joinArgs(args, 1));
    }

    private void sendPrivateMessage(Player sender, Player target, String message) {
        if (sender.equals(target)) {
            error(sender, "Você não pode mandar mensagem para si mesmo.");
            return;
        }
        if (message == null || message.isBlank()) {
            error(sender, "Escreva uma mensagem.");
            return;
        }
        PlayerSettings senderPrefs = settings(sender.getUniqueId());
        PlayerSettings targetPrefs = settings(target.getUniqueId());
        if (senderPrefs.ignored.contains(target.getUniqueId())) {
            error(sender, "Você bloqueou esse jogador. Use /unignore " + target.getName() + " para desbloquear.");
            return;
        }
        if (targetPrefs.ignored.contains(sender.getUniqueId())) {
            error(sender, "Esse jogador não está recebendo suas mensagens.");
            return;
        }
        if (!targetPrefs.receiveTells) {
            error(sender, "Esse jogador desativou mensagens privadas.");
            return;
        }

        Component from = Component.text("[PV] Você → " + target.getName() + ": ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(message, NamedTextColor.WHITE));
        Component to = Component.text("[PV] " + sender.getName() + " → você: ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(message, NamedTextColor.WHITE));
        sender.sendMessage(from);
        target.sendMessage(to);
        lastTellTarget.put(sender.getUniqueId(), target.getUniqueId());
        lastTellTarget.put(target.getUniqueId(), sender.getUniqueId());
    }

    private void replyPrivateMessage(Player sender, String[] args) {
        if (args.length == 0) {
            error(sender, "Uso: /r <mensagem>");
            return;
        }
        UUID targetId = lastTellTarget.get(sender.getUniqueId());
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (target == null) {
            lastTellTarget.remove(sender.getUniqueId());
            error(sender, "Você ainda não tem uma conversa para responder.");
            return;
        }
        sendPrivateMessage(sender, target, joinArgs(args, 0));
    }

    private UUID resolvePlayerId(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.hasPlayedBefore() ? cached.getUniqueId() : null;
    }

    private void ignorePlayer(Player player, String[] args) {
        if (args.length < 1) { error(player, "Uso: /ignore <jogador>"); return; }
        UUID targetId = resolvePlayerId(args[0]);
        if (targetId == null) { error(player, "Jogador não encontrado no histórico do servidor."); return; }
        if (targetId.equals(player.getUniqueId())) { error(player, "Você não pode bloquear a si mesmo."); return; }
        PlayerSettings prefs = settings(player.getUniqueId());
        if (!prefs.ignored.add(targetId)) { error(player, "Esse jogador já está bloqueado."); return; }
        saveData();
        success(player, "Mensagens de " + nameOf(targetId) + " bloqueadas.");
    }

    private void unignorePlayer(Player player, String[] args) {
        if (args.length < 1) { error(player, "Uso: /unignore <jogador>"); return; }
        UUID targetId = resolvePlayerId(args[0]);
        if (targetId == null) { error(player, "Jogador não encontrado no histórico do servidor."); return; }
        PlayerSettings prefs = settings(player.getUniqueId());
        if (!prefs.ignored.remove(targetId)) { error(player, "Esse jogador não está bloqueado."); return; }
        saveData();
        success(player, "Mensagens de " + nameOf(targetId) + " desbloqueadas.");
    }

    private void showIgnoreList(Player player) {
        Set<UUID> ignored = settings(player.getUniqueId()).ignored;
        if (ignored.isEmpty()) {
            player.sendMessage(Component.text("Sua lista de bloqueios está vazia.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("Jogadores bloqueados (" + ignored.size() + "): " +
                ignored.stream().map(this::nameOf).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", ")),
                NamedTextColor.GOLD));
    }

    private void toggleChatPreference(Player player, String key) {
        PlayerSettings prefs = settings(player.getUniqueId());
        String normalized = key.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "global" -> prefs.globalChat = !prefs.globalChat;
            case "clan" -> prefs.clanChat = !prefs.clanChat;
            case "tell", "pv", "privado" -> prefs.receiveTells = !prefs.receiveTells;
            case "activity", "atividade" -> prefs.activity = !prefs.activity;
            case "invites", "convites" -> prefs.invites = !prefs.invites;
            case "diplomacy", "diplomacia" -> prefs.diplomacy = !prefs.diplomacy;
            case "wars", "guerras" -> prefs.wars = !prefs.wars;
            case "announcements", "anuncios" -> prefs.announcements = !prefs.announcements;
            default -> { error(player, "Opções: global, clan, tell, activity, invites, diplomacy, wars, announcements."); return; }
        }
        saveData();
        success(player, preferenceLabel(normalized) + ": " +
                (preferenceEnabled(prefs, normalized) ? "ativado" : "desativado") + ".");
    }

    private boolean preferenceEnabled(PlayerSettings prefs, String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "global" -> prefs.globalChat;
            case "clan" -> prefs.clanChat;
            case "tell", "pv", "privado" -> prefs.receiveTells;
            case "activity", "atividade" -> prefs.activity;
            case "invites", "convites" -> prefs.invites;
            case "diplomacy", "diplomacia" -> prefs.diplomacy;
            case "wars", "guerras" -> prefs.wars;
            case "announcements", "anuncios" -> prefs.announcements;
            default -> true;
        };
    }

    private String preferenceLabel(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "global" -> "Chat global";
            case "clan" -> "Chat do clan";
            case "tell", "pv", "privado" -> "Mensagens privadas";
            case "activity", "atividade" -> "Avisos de entrada/saída";
            case "invites", "convites" -> "Convites de clan";
            case "diplomacy", "diplomacia" -> "Alianças";
            case "wars", "guerras" -> "Guerras e pontos";
            case "announcements", "anuncios" -> "Anúncios";
            default -> key;
        };
    }

    private void openChatSettings(Player player) {
        ChatSettingsHolder holder = new ChatSettingsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Configurações de chat"));
        holder.inventory = inventory;
        PlayerSettings prefs = settings(player.getUniqueId());
        addPreferenceItem(inventory, 10, Material.PAPER, "global", prefs.globalChat);
        addPreferenceItem(inventory, 11, Material.BOOK, "clan", prefs.clanChat);
        addPreferenceItem(inventory, 12, Material.WRITABLE_BOOK, "tell", prefs.receiveTells);
        addPreferenceItem(inventory, 13, Material.CLOCK, "activity", prefs.activity);
        addPreferenceItem(inventory, 14, Material.MAP, "invites", prefs.invites);
        addPreferenceItem(inventory, 15, Material.GOLD_INGOT, "diplomacy", prefs.diplomacy);
        addPreferenceItem(inventory, 16, Material.IRON_SWORD, "wars", prefs.wars);
        addPreferenceItem(inventory, 22, Material.BELL, "announcements", prefs.announcements);
        player.openInventory(inventory);
    }

    private void addPreferenceItem(Inventory inventory, int slot, Material material, String key, boolean enabled) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(preferenceLabel(key), NamedTextColor.YELLOW));
        meta.lore(List.of(
                Component.text("Status: " + (enabled ? "ATIVADO" : "DESATIVADO"), enabled ? NamedTextColor.GREEN : NamedTextColor.RED),
                Component.text("Clique para alternar.", NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(menuActionKey, PersistentDataType.STRING, "pref:" + key);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private void updateTabName(Player player) {
        if (!getConfig().getBoolean("tablist.show-clan-tag", true)) {
            player.playerListName(null);
            return;
        }
        Clan clan = findClan(player.getUniqueId());
        if (clan == null) {
            player.playerListName(null);
            clanTagsSnapshot.remove(player.getUniqueId());
            return;
        }
        clanTagsSnapshot.put(player.getUniqueId(), clan.tag);
        Component name = Component.text(player.getName(), NamedTextColor.WHITE)
                .append(Component.text(" ✦", NamedTextColor.GOLD))
                .append(Component.text(" [" + clan.tag + "]", NamedTextColor.DARK_GRAY));
        player.playerListName(name);
    }

    private void broadcastClanPreference(Clan clan, Component message, String preference) {
        for (UUID memberId : clan.members.keySet()) {
            Player online = Bukkit.getPlayer(memberId);
            if (online != null && preferenceEnabled(settings(memberId), preference)) online.sendMessage(message);
        }
    }

    private void broadcastGlobalPreference(Component message, String preference) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (preferenceEnabled(settings(online.getUniqueId()), preference)) online.sendMessage(message);
        }
    }

    private void showInfo(CommandSender sender, Clan clan) {
        if (clan == null) { error(sender, "Clan não encontrado. Use /clan info <TAG>."); return; }
        sender.sendMessage(Component.text("===== [" + clan.tag + "] " + clan.name + " =====", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("Líder: " + nameOf(clan.leader), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Membros: " + clan.members.size() + "/" + getConfig().getInt("clan.max-members", 20), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Pontos de guerra: " + clan.points, NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Criado em: " + java.time.Instant.ofEpochMilli(clan.createdAt), NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("Descrição: " + (clan.description.isBlank() ? "Sem descrição" : clan.description), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Fogo amigo: " + (clan.friendlyFire ? "ligado" : "desligado"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Base: " + (clan.home == null ? "não definida" : "definida"), NamedTextColor.GRAY));
    }

    private void showMembers(CommandSender sender, Clan clan) {
        if (clan == null) { error(sender, "Você não pertence a um clan."); return; }
        sender.sendMessage(Component.text("Membros de [" + clan.tag + "] (" + clan.members.size() + ")", NamedTextColor.GOLD));
        clan.members.entrySet().stream().sorted(Comparator.comparingInt(e -> -e.getValue().rank.level))
                .forEach(entry -> sender.sendMessage(Component.text(entry.getValue().rank.display + " • " + entry.getValue().lastName
                        + "  K:" + entry.getValue().kills + " M:" + entry.getValue().deaths, NamedTextColor.GRAY)));
    }

    private void showTop(CommandSender sender) {
        List<Clan> top = clans.values().stream()
                .sorted(Comparator.comparingInt((Clan c) -> c.points).reversed()
                        .thenComparing(Comparator.comparingInt((Clan c) -> c.members.size()).reversed()))
                .limit(10).toList();
        sender.sendMessage(Component.text("===== TOP CLANS =====", NamedTextColor.GOLD, TextDecoration.BOLD));
        if (top.isEmpty()) { sender.sendMessage(Component.text("Nenhum clan criado ainda.", NamedTextColor.GRAY)); return; }
        int pos = 1;
        for (Clan clan : top) sender.sendMessage(Component.text(pos++ + ". [" + clan.tag + "] " + clan.name
                + " • " + clan.points + " pts • " + clan.members.size() + " membros", NamedTextColor.YELLOW));
    }

    private void showClanList(CommandSender sender) {
        List<Clan> list = clans.values().stream().sorted(Comparator.comparing(c -> c.tag)).limit(20).toList();
        sender.sendMessage(Component.text("Clans (" + clans.size() + "):", NamedTextColor.GOLD));
        if (list.isEmpty()) { sender.sendMessage(Component.text("Nenhum clan criado.", NamedTextColor.GRAY)); return; }
        for (Clan clan : list) sender.sendMessage(Component.text("[" + clan.tag + "] " + clan.name + " • "
                + clan.members.size() + "/" + getConfig().getInt("clan.max-members", 20), NamedTextColor.YELLOW));
    }

    private void showStats(CommandSender sender, String query) {
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(query);
        UUID targetId = target != null ? target.getUniqueId() : null;
        if (targetId == null) {
            for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
                if (entry.getValue().name.equalsIgnoreCase(query)) { targetId = entry.getKey(); break; }
            }
        }
        if (targetId == null || !playerStats.containsKey(targetId)) { error(sender, "Não há estatísticas salvas para esse jogador."); return; }
        PlayerStats stats = playerStats.get(targetId);
        sender.sendMessage(Component.text("Estatísticas de " + stats.name + ": Kills " + stats.kills + " • Mortes " + stats.deaths, NamedTextColor.GOLD));
    }

    // ------------------------------- Menu -------------------------------

    private void openMenu(Player player) {
        Clan clan = findClan(player.getUniqueId());
        ClanMenuHolder holder = new ClanMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("ResenhaClans"));
        holder.inventory = inventory;
        addMenuItem(inventory, 10, Material.BOOK, "Informações do clan", "info");
        addMenuItem(inventory, 12, Material.PLAYER_HEAD, "Membros", "members");
        addMenuItem(inventory, 14, Material.GOLD_INGOT, "Ranking de clans", "top");
        addMenuItem(inventory, 16, Material.PAPER, "Ajuda e comandos", "help");
        addMenuItem(inventory, 20, Material.COMPASS, "Ir para a base", "home");
        addMenuItem(inventory, 22, Material.OAK_SIGN, "Alternar chat do clan", "chat");
        addMenuItem(inventory, 24, Material.WHITE_BANNER, "Ver aliados", "allies");
        if (clan == null) inventory.setItem(10, menuItem(Material.WRITABLE_BOOK, "Você ainda não tem clan", List.of("Use /clan criar <TAG> <nome>")));
        else inventory.setItem(10, menuItem(Material.BOOK, "[" + clan.tag + "] " + clan.name,
                List.of("Membros: " + clan.members.size(), "Pontos: " + clan.points, "Clique para informações")));
        player.openInventory(inventory);
    }

    private void addMenuItem(Inventory inventory, int slot, Material material, String name, String action) {
        ItemStack item = menuItem(material, name, List.of("Clique para abrir"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(menuActionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ChatSettingsHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String action = clicked.getItemMeta().getPersistentDataContainer().get(menuActionKey, PersistentDataType.STRING);
            if (action == null || !action.startsWith("pref:")) return;
            toggleChatPreference(player, action.substring("pref:".length()));
            openChatSettings(player);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof ClanMenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(menuActionKey, PersistentDataType.STRING);
        if (action == null) return;
        player.closeInventory();
        player.performCommand("clan " + action);
    }

    // ----------------------------- Utilitários -----------------------------

    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private PlayerStats getPlayerStats(UUID playerId, String name) {
        PlayerStats stats = playerStats.computeIfAbsent(playerId, ignored -> new PlayerStats(name, 0, 0));
        stats.name = name;
        return stats;
    }

    private Clan findClan(UUID playerId) {
        for (Clan clan : clans.values()) if (clan.members.containsKey(playerId)) return clan;
        return null;
    }

    private Clan findClanByTag(String tag) {
        if (tag == null) return null;
        return clans.values().stream().filter(c -> c.tag.equalsIgnoreCase(tag)).findFirst().orElse(null);
    }

    private UUID findMemberByName(Clan clan, String name) {
        for (Map.Entry<UUID, Member> entry : clan.members.entrySet()) {
            if (entry.getValue().lastName.equalsIgnoreCase(name)) return entry.getKey();
            Player online = Bukkit.getPlayer(entry.getKey());
            if (online != null && online.getName().equalsIgnoreCase(name)) return entry.getKey();
        }
        return null;
    }

    private boolean hasRole(Clan clan, UUID playerId, Rank minimum) {
        Member member = clan.members.get(playerId);
        return member != null && member.rank.level >= minimum.level;
    }

    private String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) return online.getName();
        PlayerStats stats = playerStats.get(id);
        return stats != null ? stats.name : id.toString().substring(0, 8);
    }

    private void broadcastClan(Clan clan, Component message) {
        for (UUID memberId : clan.members.keySet()) {
            Player online = Bukkit.getPlayer(memberId);
            if (online != null) online.sendMessage(message);
        }
    }

    private Relation getRelation(UUID a, UUID b) { return relations.get(relationKey(a, b)); }

    private String relationKey(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
    }

    private Rank parseRank(String raw) {
        try { return Rank.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return Rank.MEMBER; }
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("===== RESENHACLANS =====", NamedTextColor.GOLD, TextDecoration.BOLD));
        String[] lines = {
                "/clan criar <TAG> <nome> • criar clan",
                "/clan menu • abrir menu",
                "/clan info [TAG] • informações",
                "/clan convidar <jogador> • convidar",
                "/clan aceitar [TAG] • aceitar convite",
                "/clan membros • listar membros",
                "/clan promover/rebaixar <jogador> • cargos (líder)",
                "/clan expulsar <jogador> • remover membro",
                "/clan transferir <jogador> • passar liderança",
                "/clan desc <texto> • descrição",
                "/clanchat • fixar mensagens no chat do clan",
                "/g • voltar ao chat global",
                "/msg <jogador> <mensagem> e /r <mensagem> • mensagem privada/resposta",
                "/ignore <jogador>, /unignore <jogador>, /ignorelist • bloquear mensagens",
                "/chat • configurações do chat e notificações",
                "/clan chat ou /clan c <mensagem> • chat privado",
                "/clan sethome e /clan home • base",
                "/clan ally <TAG> • propor aliança",
                "/clan allyaccept <TAG> • aceitar aliança",
                "/clan guerra <TAG> e /clan peace <TAG> • guerra",
                "/clan friendlyfire <on|off> • fogo amigo",
                "/clan top /clan list /clan stats [jogador] • rankings",
                "/clan sair • deixar clan",
                "/clan dissolver confirm • apagar clan (líder)"
        };
        for (String line : lines) sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
    }

    private void success(CommandSender sender, String message) { sender.sendMessage(Component.text(message, NamedTextColor.GREEN)); }
    private void error(CommandSender sender, String message) { sender.sendMessage(Component.text(message, NamedTextColor.RED)); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && command.getName().equalsIgnoreCase("msg")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 1 && command.getName().equalsIgnoreCase("ignore")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 1 && command.getName().equalsIgnoreCase("unignore") && sender instanceof Player player) {
            return settings(player.getUniqueId()).ignored.stream().map(this::nameOf)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && command.getName().equalsIgnoreCase("chat") && args[0].equalsIgnoreCase("toggle")) {
            return List.of("global", "clan", "tell", "activity", "invites", "diplomacy", "wars", "announcements")
                    .stream().filter(key -> key.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 1 && command.getName().equalsIgnoreCase("clanchat")) {
            return List.of("on", "off").stream()
                    .filter(key -> key.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 1) {
            return List.of("criar", "menu", "info", "membros", "convidar", "aceitar", "recusar",
                    "sair", "expulsar", "promover", "rebaixar", "transferir", "dissolver", "desc",
                    "anunciar", "sethome", "home", "chat", "c", "friendlyfire", "ally", "allyaccept",
                    "unally", "allies", "guerra", "peace", "top", "list", "stats", "ajuda", "reload")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (Set.of("convidar", "invite", "expulsar", "kick", "promover", "promote", "rebaixar", "demote",
                    "transferir", "transfer").contains(sub)) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
            if (Set.of("info", "ally", "aliar", "allyaccept", "aceitaralianca", "unally", "desaliar",
                    "guerra", "war", "peace", "paz", "aceitar", "accept", "recusar", "deny").contains(sub)) {
                if (Set.of("aceitar", "accept", "recusar", "deny").contains(sub)) {
                    if (!(sender instanceof Player player)) return List.of();
                    Map<UUID, Invite> available = invites.get(player.getUniqueId());
                    if (available == null) return List.of();
                    return available.keySet().stream().map(clans::get).filter(c -> c != null).map(c -> c.tag)
                            .filter(tag -> tag.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
                }
                return clans.values().stream().map(c -> c.tag)
                        .filter(tag -> tag.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
            if (sub.equals("friendlyfire")) return List.of("on", "off");
        }
        return List.of();
    }

    private enum Rank {
        MEMBER(0, "Membro"), OFFICER(1, "Oficial"), COLEADER(2, "Colíder"), LEADER(3, "Líder");
        private final int level;
        private final String display;
        Rank(int level, String display) { this.level = level; this.display = display; }
    }
    private enum RelationType { ALLY_PENDING, ALLY, WAR }

    private static final class Clan {
        private final UUID id;
        private final String name;
        private final String tag;
        private String description;
        private UUID leader;
        private final long createdAt;
        private int points;
        private boolean friendlyFire;
        private Home home;
        private final LinkedHashMap<UUID, Member> members = new LinkedHashMap<>();
        private Clan(UUID id, String name, String tag, String description, UUID leader, long createdAt, int points, boolean friendlyFire) {
            this.id = id; this.name = name; this.tag = tag; this.description = description;
            this.leader = leader; this.createdAt = createdAt; this.points = points; this.friendlyFire = friendlyFire;
        }
    }
    private static final class Member {
        private Rank rank;
        private String lastName;
        private int kills;
        private int deaths;
        private final long joinedAt;
        private Member(Rank rank, String lastName, int kills, int deaths, long joinedAt) {
            this.rank = rank; this.lastName = lastName; this.kills = kills; this.deaths = deaths; this.joinedAt = joinedAt;
        }
    }
    private static final class PlayerStats {
        private String name;
        private int kills;
        private int deaths;
        private PlayerStats(String name, int kills, int deaths) { this.name = name; this.kills = kills; this.deaths = deaths; }
    }
    private record Home(String world, double x, double y, double z, float yaw, float pitch) {}
    private record Invite(UUID inviter, long expiresAt) {}
    private static final class Relation {
        private final UUID a;
        private final UUID b;
        private RelationType type;
        private UUID requestedBy;
        private Relation(UUID a, UUID b, RelationType type, UUID requestedBy) {
            this.a = a; this.b = b; this.type = type; this.requestedBy = requestedBy;
        }
    }
    private record PendingTeleport(BukkitTask task, Location origin, UUID clanId) {}
    private static final class ClanMenuHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class ChatSettingsHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class PlayerSettings {
        private volatile boolean globalChat = true;
        private volatile boolean clanChat = true;
        private volatile boolean receiveTells = true;
        private volatile boolean activity = true;
        private volatile boolean invites = true;
        private volatile boolean diplomacy = true;
        private volatile boolean wars = true;
        private volatile boolean announcements = true;
        private final Set<UUID> ignored = ConcurrentHashMap.newKeySet();
    }
}
