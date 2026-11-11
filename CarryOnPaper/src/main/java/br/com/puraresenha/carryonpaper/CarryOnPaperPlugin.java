package br.com.puraresenha.carryonpaper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Furnace;
import org.bukkit.block.Hopper;
import org.bukkit.block.Lidded;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CarryOnPaperPlugin extends JavaPlugin implements Listener {
    private static final Set<EntityType> ALLOWED_MOBS = Set.of(
            EntityType.ALLAY,
            EntityType.ARMADILLO,
            EntityType.AXOLOTL,
            EntityType.BAT,
            EntityType.CAMEL,
            EntityType.CAT,
            EntityType.CHICKEN,
            EntityType.COD,
            EntityType.COW,
            EntityType.DONKEY,
            EntityType.FOX,
            EntityType.FROG,
            EntityType.GLOW_SQUID,
            EntityType.HORSE,
            EntityType.MOOSHROOM,
            EntityType.MULE,
            EntityType.OCELOT,
            EntityType.PARROT,
            EntityType.PIG,
            EntityType.RABBIT,
            EntityType.SALMON,
            EntityType.SHEEP,
            EntityType.SNIFFER,
            EntityType.SQUID,
            EntityType.STRIDER,
            EntityType.TADPOLE,
            EntityType.TROPICAL_FISH,
            EntityType.TURTLE,
            EntityType.VILLAGER,
            EntityType.WANDERING_TRADER
    );

    private static final Set<Material> ALLOWED_BLOCKS = Set.of(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL,
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER,
            Material.DISPENSER,
            Material.DROPPER,
            Material.HOPPER,
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX
    );

    private final Map<UUID, Mob> carriedMobs = new HashMap<>();

    private NamespacedKey blockTokenKey;
    private NamespacedKey blockMaterialKey;
    private NamespacedKey blockDataKey;
    private NamespacedKey inventoryKey;
    private NamespacedKey tilePdcKey;
    private NamespacedKey customNameKey;
    private NamespacedKey hopperCooldownKey;
    private NamespacedKey furnaceBurnTimeKey;
    private NamespacedKey furnaceCookTimeKey;
    private NamespacedKey furnaceCookTimeTotalKey;
    private NamespacedKey furnaceCookSpeedKey;
    private NamespacedKey furnaceRecipesKey;

    private NamespacedKey mobOriginWorldKey;
    private NamespacedKey mobOriginXKey;
    private NamespacedKey mobOriginYKey;
    private NamespacedKey mobOriginZKey;

    private NamespacedKey mobMarkerKey;
    private NamespacedKey mobAiKey;
    private NamespacedKey mobInvulnerableKey;
    private NamespacedKey mobGravityKey;
    private NamespacedKey mobSilentKey;
    private NamespacedKey mobCollidableKey;

    @Override
    public void onEnable() {
        blockTokenKey = new NamespacedKey(this, "block_token");
        blockMaterialKey = new NamespacedKey(this, "block_material");
        blockDataKey = new NamespacedKey(this, "block_data");
        inventoryKey = new NamespacedKey(this, "inventory");
        tilePdcKey = new NamespacedKey(this, "tile_pdc");
        customNameKey = new NamespacedKey(this, "custom_name");
        hopperCooldownKey = new NamespacedKey(this, "hopper_cooldown");
        furnaceBurnTimeKey = new NamespacedKey(this, "furnace_burn_time");
        furnaceCookTimeKey = new NamespacedKey(this, "furnace_cook_time");
        furnaceCookTimeTotalKey = new NamespacedKey(this, "furnace_cook_time_total");
        furnaceCookSpeedKey = new NamespacedKey(this, "furnace_cook_speed");
        furnaceRecipesKey = new NamespacedKey(this, "furnace_recipes");

        mobOriginWorldKey = new NamespacedKey(this, "mob_origin_world");
        mobOriginXKey = new NamespacedKey(this, "mob_origin_x");
        mobOriginYKey = new NamespacedKey(this, "mob_origin_y");
        mobOriginZKey = new NamespacedKey(this, "mob_origin_z");

        mobMarkerKey = new NamespacedKey(this, "mob_carried");
        mobAiKey = new NamespacedKey(this, "mob_ai");
        mobInvulnerableKey = new NamespacedKey(this, "mob_invulnerable");
        mobGravityKey = new NamespacedKey(this, "mob_gravity");
        mobSilentKey = new NamespacedKey(this, "mob_silent");
        mobCollidableKey = new NamespacedKey(this, "mob_collidable");

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTask(this, this::recoverOrphanedMobs);
        Bukkit.getScheduler().runTaskTimer(this, this::checkCarriedMobs, 20L, 20L);

        getLogger().info("CarryOnPaper carregado. Lista fechada de mobs e blocos ativa.");
    }

    @Override
    public void onDisable() {
        for (UUID playerId : new HashSet<>(carriedMobs.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                releaseMob(player, false);
            } else {
                Mob mob = carriedMobs.remove(playerId);
                if (mob != null && mob.isValid()) {
                    Location safe;
                    if (requiresWater(mob.getType())) {
                        Location origin = getSavedMobOrigin(mob);
                        safe = origin == null ? null : findSafeWaterLocation(mob, origin.getWorld(), origin);
                    } else {
                        safe = findSafeLocation(mob.getWorld(), mob.getLocation());
                    }
                    if (safe != null) {
                        if (mob.getVehicle() != null) mob.leaveVehicle();
                        mob.teleport(safe);
                        restoreMobFlags(mob);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            if (hasBlockToken(player) || hasCarriedMob(player)) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        boolean rightClick = event.getAction().name().startsWith("RIGHT_CLICK");

        if (hasCarriedMob(player)) {
            event.setCancelled(true);
            if (rightClick) {
                releaseMob(player, true);
            }
            return;
        }

        if (hasBlockToken(player)) {
            event.setCancelled(true);
            if (rightClick) {
                placeCarriedBlock(player, event.getClickedBlock(), event.getBlockFace());
            }
            return;
        }

        if (event.isCancelled() || !rightClick) {
            return;
        }

        if (!player.hasPermission("carryonpaper.use")
                || !player.isSneaking()
                || !handsAreEmpty(player)
                || event.getClickedBlock() == null) {
            return;
        }

        tryPickupBlock(player, event.getClickedBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();

        if (hasCarriedMob(player)) {
            event.setCancelled(true);
            releaseMob(player, true);
            return;
        }

        if (hasBlockToken(player)) {
            event.setCancelled(true);
            return;
        }

        if (event.isCancelled()
                || !player.hasPermission("carryonpaper.use")
                || !player.isSneaking()
                || !handsAreEmpty(player)
                || player.isInsideVehicle()
                || !player.getPassengers().isEmpty()) {
            return;
        }

        Entity target = event.getRightClicked();

        if (!(target instanceof Mob mob) || !ALLOWED_MOBS.contains(target.getType())) {
            return;
        }

        if (!target.isValid()
                || target.isDead()
                || target.isInsideVehicle()
                || !target.getPassengers().isEmpty()
                || isMobMarked(mob)) {
            return;
        }

        if (requiresWater(target.getType()) && !mob.isInWater()) {
            player.sendMessage(Component.text("Esse mob aquático só pode ser pego dentro da água.", NamedTextColor.RED));
            return;
        }

        saveMobFlags(mob);
        mob.setAI(false);
        mob.setInvulnerable(true);
        mob.setGravity(false);
        mob.setSilent(true);
        mob.setCollidable(false);

        boolean mounted;
        try {
            mounted = player.addPassenger(mob);
        } catch (RuntimeException exception) {
            mounted = false;
            getLogger().warning("Não foi possível anexar um mob ao jogador: " + exception.getMessage());
        }

        if (!mounted) {
            restoreMobFlags(mob);
            player.sendMessage(Component.text("Não foi possível carregar esse mob aqui.", NamedTextColor.RED));
            return;
        }

        carriedMobs.put(player.getUniqueId(), mob);
        event.setCancelled(true);
        player.sendMessage(Component.text("Mob carregado. Clique com o botão direito para soltar.", NamedTextColor.GREEN));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && (hasBlockToken(player) || hasCarriedMob(player))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && (hasBlockToken(player) || hasCarriedMob(player))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        if (hasBlockToken(event.getPlayer())
                || hasCarriedMob(event.getPlayer())
                || isBlockToken(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (hasBlockToken(event.getPlayer()) || hasCarriedMob(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (hasBlockToken(player) || hasCarriedMob(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDeath(PlayerDeathEvent event) {
        releaseMob(event.getEntity(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onQuit(PlayerQuitEvent event) {
        releaseMob(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        if (hasCarriedMob(event.getPlayer())) {
            releaseMob(event.getPlayer(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline() && hasCarriedMob(player)) {
                releaseMob(player, false);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCarriedMobDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Mob mob && isMobMarked(mob)) {
            event.setCancelled(true);
        }
    }

    private void tryPickupBlock(Player player, Block block, PlayerInteractEvent interaction) {
        Material material = block.getType();

        if (!ALLOWED_BLOCKS.contains(material)) {
            return;
        }

        BlockState initialState = block.getState();
        if (!(initialState instanceof Container initialContainer)) {
            return;
        }

        if (initialContainer.isLocked()
                || (initialState instanceof Lidded lidded && lidded.isOpen())
                || (initialContainer instanceof Lootable lootable && lootable.getLootTable() != null)) {
            player.sendMessage(Component.text("Esse bloco está aberto, trancado ou possui loot aleatório; não pode ser carregado.", NamedTextColor.RED));
            return;
        }

        if (initialState instanceof org.bukkit.block.Chest
                && block.getBlockData() instanceof org.bukkit.block.data.type.Chest chestData
                && chestData.getType() != Type.SINGLE) {
            player.sendMessage(Component.text("Feche o baú duplo antes de tentar carregá-lo.", NamedTextColor.RED));
            return;
        }

        BlockBreakEvent permissionCheck = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(permissionCheck);
        if (permissionCheck.isCancelled() || block.getType() != material) {
            return;
        }

        BlockState currentState = block.getState();
        if (!(currentState instanceof Container container)
                || container.isLocked()
                || (currentState instanceof Lootable lootable && lootable.getLootTable() != null)) {
            return;
        }

        ItemStack token = createBlockToken(block, container);
        if (token == null) {
            player.sendMessage(Component.text("Não consegui salvar os dados desse bloco. Nada foi removido.", NamedTextColor.RED));
            return;
        }

        interaction.setCancelled(true);
        player.getInventory().setItemInOffHand(token);
        block.setType(Material.AIR, false);

        player.sendMessage(Component.text("Bloco carregado. Clique com o botão direito em uma superfície para colocá-lo.", NamedTextColor.GREEN));
    }

    private ItemStack createBlockToken(Block block, Container container) {
        try {
            YamlConfiguration inventoryConfig = new YamlConfiguration();
            inventoryConfig.set("contents", container.getSnapshotInventory().getContents());
            byte[] tileData = container.getPersistentDataContainer().serializeToBytes();

            ItemStack token = new ItemStack(block.getType(), 1);
            ItemMeta meta = token.getItemMeta();
            if (meta == null) {
                return null;
            }

            meta.displayName(Component.text("Carregando: " + friendlyName(block.getType()), NamedTextColor.GOLD));
            meta.lore(List.of(
                    Component.text("CarryOnPaper", NamedTextColor.DARK_GRAY),
                    Component.text("Clique com o botão direito para colocar.", NamedTextColor.GRAY)
            ));

            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(blockTokenKey, PersistentDataType.BYTE, (byte) 1);
            data.set(blockMaterialKey, PersistentDataType.STRING, block.getType().name());
            data.set(blockDataKey, PersistentDataType.STRING, block.getBlockData().getAsString());
            data.set(inventoryKey, PersistentDataType.STRING, inventoryConfig.saveToString());
            data.set(tilePdcKey, PersistentDataType.BYTE_ARRAY, tileData);

            if (container.getCustomName() != null) {
                data.set(customNameKey, PersistentDataType.STRING, container.getCustomName());
            }
            if (container instanceof Hopper hopper) {
                data.set(hopperCooldownKey, PersistentDataType.INTEGER, hopper.getTransferCooldown());
            }
            if (container instanceof Furnace furnace) {
                data.set(furnaceBurnTimeKey, PersistentDataType.INTEGER, (int) furnace.getBurnTime());
                data.set(furnaceCookTimeKey, PersistentDataType.INTEGER, (int) furnace.getCookTime());
                data.set(furnaceCookTimeTotalKey, PersistentDataType.INTEGER, furnace.getCookTimeTotal());
                data.set(furnaceCookSpeedKey, PersistentDataType.DOUBLE, furnace.getCookSpeedMultiplier());
                data.set(furnaceRecipesKey, PersistentDataType.STRING, serializeFurnaceRecipes(furnace));
            }

            token.setItemMeta(meta);
            return token;
        } catch (IOException | RuntimeException exception) {
            getLogger().warning("Falha ao salvar bloco " + block.getType() + ": " + exception.getMessage());
            return null;
        }
    }

    private void placeCarriedBlock(Player player, Block clickedBlock, org.bukkit.block.BlockFace clickedFace) {
        ItemStack token = player.getInventory().getItemInOffHand();
        if (!isBlockToken(token)) {
            return;
        }

        Block against = clickedBlock;
        org.bukkit.block.BlockFace face = clickedFace;

        if (against == null) {
            RayTraceResult hit = player.getWorld().rayTraceBlocks(
                    player.getEyeLocation(),
                    player.getEyeLocation().getDirection(),
                    5.0,
                    FluidCollisionMode.NEVER,
                    true
            );
            if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
                against = hit.getHitBlock();
                face = hit.getHitBlockFace();
            }
        }

        if (against == null || face == null) {
            player.sendMessage(Component.text("Olhe para uma superfície para colocar o bloco.", NamedTextColor.YELLOW));
            return;
        }

        Block target = against.getRelative(face);
        if (!target.getType().isAir()) {
            player.sendMessage(Component.text("O espaço precisa estar vazio.", NamedTextColor.RED));
            return;
        }

        ItemMeta meta = token.getItemMeta();
        if (meta == null) {
            player.sendMessage(Component.text("O bloco carregado está inválido. Não descartei o item.", NamedTextColor.RED));
            return;
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        String materialName = data.get(blockMaterialKey, PersistentDataType.STRING);
        String blockDataString = data.get(blockDataKey, PersistentDataType.STRING);
        String inventoryString = data.get(inventoryKey, PersistentDataType.STRING);
        byte[] tileData = data.get(tilePdcKey, PersistentDataType.BYTE_ARRAY);

        Material material = materialName == null ? null : Material.matchMaterial(materialName);
        if (material == null || !ALLOWED_BLOCKS.contains(material)
                || blockDataString == null || inventoryString == null || tileData == null) {
            player.sendMessage(Component.text("Os dados do bloco estão incompletos. O item foi mantido.", NamedTextColor.RED));
            return;
        }

        YamlConfiguration inventoryConfig = new YamlConfiguration();
        try {
            inventoryConfig.loadFromString(inventoryString);
        } catch (InvalidConfigurationException exception) {
            player.sendMessage(Component.text("Não foi possível ler o inventário salvo. O item foi mantido.", NamedTextColor.RED));
            getLogger().warning("Inventário de bloco inválido: " + exception.getMessage());
            return;
        }

        List<?> rawContents = inventoryConfig.getList("contents");
        if (rawContents == null) {
            player.sendMessage(Component.text("O inventário salvo está ausente. O item foi mantido.", NamedTextColor.RED));
            return;
        }

        ItemStack[] contents = new ItemStack[rawContents.size()];
        for (int i = 0; i < rawContents.size(); i++) {
            Object entry = rawContents.get(i);
            if (entry != null && !(entry instanceof ItemStack)) {
                player.sendMessage(Component.text("O inventário salvo contém dados inválidos. O item foi mantido.", NamedTextColor.RED));
                return;
            }
            contents[i] = entry instanceof ItemStack item ? item.clone() : null;
        }

        BlockData blockData;
        try {
            blockData = Bukkit.createBlockData(blockDataString);
            if (blockData.getMaterial() != material) {
                throw new IllegalArgumentException("O tipo do BlockData não corresponde ao material salvo.");
            }
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text("A orientação do bloco não pôde ser recuperada. O item foi mantido.", NamedTextColor.RED));
            return;
        }

        BlockState replacedState = target.getState();

        try {
            target.setType(material, false);
            target.setBlockData(blockData, false);

            BlockState placedState = target.getState();
            if (!(placedState instanceof Container placedContainer)
                    || placedContainer.getSnapshotInventory().getSize() != contents.length) {
                target.setType(Material.AIR, false);
                player.sendMessage(Component.text("Esse bloco não pôde ser restaurado com segurança. O item foi mantido.", NamedTextColor.RED));
                return;
            }

            placedContainer.getSnapshotInventory().setContents(contents);

            String customName = data.get(customNameKey, PersistentDataType.STRING);
            if (customName != null) {
                placedContainer.setCustomName(customName);
            }

            Integer hopperCooldown = data.get(hopperCooldownKey, PersistentDataType.INTEGER);
            if (placedContainer instanceof Hopper hopper && hopperCooldown != null) {
                hopper.setTransferCooldown(Math.max(0, hopperCooldown));
            }

            if (placedContainer instanceof Furnace furnace && !restoreFurnaceState(furnace, data)) {
                target.setType(Material.AIR, false);
                player.sendMessage(Component.text("Os dados do forno não puderam ser restaurados. O item foi mantido.", NamedTextColor.RED));
                return;
            }

            placedContainer.getPersistentDataContainer().readFromBytes(tileData, true);
            if (!placedContainer.update(true, false)) {
                target.setType(Material.AIR, false);
                player.sendMessage(Component.text("O servidor recusou a restauração do bloco. O item foi mantido.", NamedTextColor.RED));
                return;
            }

            BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                    target,
                    replacedState,
                    against,
                    token.clone(),
                    player,
                    true,
                    EquipmentSlot.OFF_HAND
            );
            Bukkit.getPluginManager().callEvent(placeEvent);

            if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
                target.setType(Material.AIR, false);
                player.sendMessage(Component.text("Você não pode colocar esse bloco nesse local.", NamedTextColor.RED));
                return;
            }

            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            player.sendMessage(Component.text("Bloco colocado com inventário e dados restaurados.", NamedTextColor.GREEN));
        } catch (Exception exception) {
            if (target.getType() == material) {
                target.setType(Material.AIR, false);
            }
            getLogger().severe("Falha ao colocar bloco carregado: " + exception.getMessage());
            player.sendMessage(Component.text("A colocação falhou. O item carregado foi mantido; tente outro local.", NamedTextColor.RED));
        }
    }

    private void saveMobFlags(Mob mob) {
        PersistentDataContainer data = mob.getPersistentDataContainer();
        data.set(mobMarkerKey, PersistentDataType.BYTE, (byte) 1);
        data.set(mobAiKey, PersistentDataType.BYTE, boolByte(mob.hasAI()));
        data.set(mobInvulnerableKey, PersistentDataType.BYTE, boolByte(mob.isInvulnerable()));
        data.set(mobGravityKey, PersistentDataType.BYTE, boolByte(mob.hasGravity()));
        data.set(mobSilentKey, PersistentDataType.BYTE, boolByte(mob.isSilent()));
        data.set(mobCollidableKey, PersistentDataType.BYTE, boolByte(mob.isCollidable()));

        Location origin = mob.getLocation();
        data.set(mobOriginWorldKey, PersistentDataType.STRING, origin.getWorld().getName());
        data.set(mobOriginXKey, PersistentDataType.DOUBLE, origin.getX());
        data.set(mobOriginYKey, PersistentDataType.DOUBLE, origin.getY());
        data.set(mobOriginZKey, PersistentDataType.DOUBLE, origin.getZ());
    }

    private void restoreMobFlags(Mob mob) {
        PersistentDataContainer data = mob.getPersistentDataContainer();

        Byte ai = data.get(mobAiKey, PersistentDataType.BYTE);
        Byte invulnerable = data.get(mobInvulnerableKey, PersistentDataType.BYTE);
        Byte gravity = data.get(mobGravityKey, PersistentDataType.BYTE);
        Byte silent = data.get(mobSilentKey, PersistentDataType.BYTE);
        Byte collidable = data.get(mobCollidableKey, PersistentDataType.BYTE);

        if (ai != null) mob.setAI(ai == 1);
        if (invulnerable != null) mob.setInvulnerable(invulnerable == 1);
        if (gravity != null) mob.setGravity(gravity == 1);
        if (silent != null) mob.setSilent(silent == 1);
        if (collidable != null) mob.setCollidable(collidable == 1);

        data.remove(mobMarkerKey);
        data.remove(mobAiKey);
        data.remove(mobInvulnerableKey);
        data.remove(mobGravityKey);
        data.remove(mobSilentKey);
        data.remove(mobCollidableKey);
        data.remove(mobOriginWorldKey);
        data.remove(mobOriginXKey);
        data.remove(mobOriginYKey);
        data.remove(mobOriginZKey);
    }

    private void releaseMob(Player player, boolean notify) {
        Mob mob = carriedMobs.get(player.getUniqueId());

        if (mob == null || !mob.isValid()) {
            mob = player.getPassengers().stream()
                    .filter(Mob.class::isInstance)
                    .map(Mob.class::cast)
                    .filter(this::isMobMarked)
                    .findFirst()
                    .orElse(null);
        }

        if (mob == null || !mob.isValid()) {
            carriedMobs.remove(player.getUniqueId());
            return;
        }

        Location safe = requiresWater(mob.getType())
                ? findSafeWaterLocation(mob, player.getWorld(), player.getLocation())
                : findSafeLocation(player.getWorld(), player.getLocation());

        if (safe == null && requiresWater(mob.getType())) {
            Location origin = getSavedMobOrigin(mob);
            if (origin != null) {
                safe = findSafeWaterLocation(mob, origin.getWorld(), origin);
            }
        }

        if (safe == null) {
            if (notify && player.isOnline()) {
                player.sendMessage(Component.text("Preciso de um espaço seguro para soltar esse mob. Ele continua protegido enquanto isso.", NamedTextColor.YELLOW));
            }
            return;
        }

        if (mob.getVehicle() == player) {
            player.removePassenger(mob);
        } else if (mob.getVehicle() != null) {
            mob.leaveVehicle();
        }

        mob.teleport(safe);
        mob.setVelocity(new Vector(0, 0, 0));
        mob.setFallDistance(0);
        restoreMobFlags(mob);
        carriedMobs.remove(player.getUniqueId());

        if (notify && player.isOnline()) {
            player.sendMessage(Component.text("Mob solto.", NamedTextColor.GREEN));
        }
    }

    private void checkCarriedMobs() {
        for (Map.Entry<UUID, Mob> entry : new HashMap<>(carriedMobs).entrySet()) {
            UUID playerId = entry.getKey();
            Player player = Bukkit.getPlayer(playerId);
            Mob mob = entry.getValue();

            if (mob == null || !mob.isValid() || mob.isDead()) {
                carriedMobs.remove(playerId);
                continue;
            }

            if (player != null && mob.getVehicle() == player) {
                continue;
            }

            Location safe;
            if (requiresWater(mob.getType())) {
                Location origin = getSavedMobOrigin(mob);
                safe = origin == null ? null : findSafeWaterLocation(mob, origin.getWorld(), origin);
                if (safe == null && player != null) {
                    safe = findSafeWaterLocation(mob, player.getWorld(), player.getLocation());
                }
            } else {
                Location around = player != null ? player.getLocation() : mob.getLocation();
                World world = player != null ? player.getWorld() : mob.getWorld();
                safe = findSafeLocation(world, around);
            }

            if (safe == null) {
                continue; // Mantém o mob marcado e invulnerável até existir um local seguro.
            }

            if (mob.getVehicle() != null) {
                mob.leaveVehicle();
            }
            mob.teleport(safe);
            mob.setVelocity(new Vector(0, 0, 0));
            mob.setFallDistance(0);
            restoreMobFlags(mob);
            carriedMobs.remove(playerId);
        }
    }

    private void recoverOrphanedMobs() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Mob mob) || !isMobMarked(mob)) {
                    continue;
                }

                if (mob.getVehicle() instanceof Player player) {
                    carriedMobs.put(player.getUniqueId(), mob);
                    continue;
                }

                Location safe;
                if (requiresWater(mob.getType())) {
                    Location origin = getSavedMobOrigin(mob);
                    safe = origin == null ? null : findSafeWaterLocation(mob, origin.getWorld(), origin);
                } else {
                    safe = findSafeLocation(mob.getWorld(), mob.getLocation());
                }

                if (safe != null) {
                    mob.teleport(safe);
                    restoreMobFlags(mob);
                }
            }
        }
    }

    private boolean hasCarriedMob(Player player) {
        Mob mapped = carriedMobs.get(player.getUniqueId());
        if (mapped != null && mapped.isValid() && isMobMarked(mapped)) {
            return true;
        }

        return player.getPassengers().stream()
                .filter(Mob.class::isInstance)
                .map(Mob.class::cast)
                .anyMatch(this::isMobMarked);
    }

    private boolean isMobMarked(Mob mob) {
        return mob.getPersistentDataContainer().has(mobMarkerKey, PersistentDataType.BYTE);
    }

    private boolean requiresWater(EntityType type) {
        return switch (type) {
            case AXOLOTL, COD, GLOW_SQUID, SALMON, SQUID, TADPOLE, TROPICAL_FISH -> true;
            default -> false;
        };
    }

    private Location getSavedMobOrigin(Mob mob) {
        PersistentDataContainer data = mob.getPersistentDataContainer();
        String worldName = data.get(mobOriginWorldKey, PersistentDataType.STRING);
        Double x = data.get(mobOriginXKey, PersistentDataType.DOUBLE);
        Double y = data.get(mobOriginYKey, PersistentDataType.DOUBLE);
        Double z = data.get(mobOriginZKey, PersistentDataType.DOUBLE);
        if (worldName == null || x == null || y == null || z == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }

    private Location findSafeWaterLocation(Mob mob, World world, Location around) {
        int baseX = around.getBlockX();
        int baseY = around.getBlockY();
        int baseZ = around.getBlockZ();
        int height = Math.max(1, (int) Math.ceil(mob.getHeight()));

        for (int radius = 0; radius <= 8; radius++) {
            for (int dy = 3; dy >= -5; dy--) {
                int y = baseY + dy;
                if (y < world.getMinHeight() || y + height >= world.getMaxHeight()) {
                    continue;
                }

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }

                        int x = baseX + dx;
                        int z = baseZ + dz;
                        boolean waterColumn = true;

                        for (int offset = 0; offset < height; offset++) {
                            if (world.getBlockAt(x, y + offset, z).getType() != Material.WATER) {
                                waterColumn = false;
                                break;
                            }
                        }

                        if (waterColumn) {
                            return new Location(world, x + 0.5, y + 0.1, z + 0.5, around.getYaw(), around.getPitch());
                        }
                    }
                }
            }
        }

        return null;
    }

    private String serializeFurnaceRecipes(Furnace furnace) {
        YamlConfiguration recipes = new YamlConfiguration();
        for (Map.Entry<CookingRecipe<?>, Integer> entry : furnace.getRecipesUsed().entrySet()) {
            recipes.set("used." + entry.getKey().getKey(), entry.getValue());
        }
        return recipes.saveToString();
    }

    private boolean restoreFurnaceState(Furnace furnace, PersistentDataContainer data) {
        Integer burnTime = data.get(furnaceBurnTimeKey, PersistentDataType.INTEGER);
        Integer cookTime = data.get(furnaceCookTimeKey, PersistentDataType.INTEGER);
        Integer cookTimeTotal = data.get(furnaceCookTimeTotalKey, PersistentDataType.INTEGER);
        Double cookSpeed = data.get(furnaceCookSpeedKey, PersistentDataType.DOUBLE);
        String serializedRecipes = data.get(furnaceRecipesKey, PersistentDataType.STRING);

        if (burnTime == null || cookTime == null || cookTimeTotal == null
                || cookSpeed == null || serializedRecipes == null) {
            return false;
        }

        YamlConfiguration recipeConfig = new YamlConfiguration();
        try {
            recipeConfig.loadFromString(serializedRecipes);
        } catch (InvalidConfigurationException exception) {
            getLogger().warning("Receitas salvas do forno inválidas: " + exception.getMessage());
            return false;
        }

        Map<CookingRecipe<?>, Integer> restoredRecipes = new HashMap<>();
        if (recipeConfig.isConfigurationSection("used")) {
            for (String keyText : recipeConfig.getConfigurationSection("used").getKeys(false)) {
                NamespacedKey recipeKey = NamespacedKey.fromString(keyText);
                if (recipeKey == null) {
                    return false;
                }

                Recipe recipe = Bukkit.getRecipe(recipeKey);
                if (!(recipe instanceof CookingRecipe<?> cookingRecipe)) {
                    getLogger().warning("Receita de forno ausente durante restauração: " + keyText);
                    return false;
                }

                int count = recipeConfig.getInt("used." + keyText, -1);
                if (count < 0) {
                    return false;
                }
                restoredRecipes.put(cookingRecipe, count);
            }
        }

        furnace.setBurnTime((short) Math.max(0, Math.min(Short.MAX_VALUE, burnTime)));
        furnace.setCookTime((short) Math.max(0, Math.min(Short.MAX_VALUE, cookTime)));
        furnace.setCookTimeTotal(Math.max(0, cookTimeTotal));
        furnace.setCookSpeedMultiplier(Math.max(0.0, Math.min(200.0, cookSpeed)));
        furnace.setRecipesUsed(restoredRecipes);
        return true;
    }

    private Location findSafeLocation(World world, Location around) {
        int baseX = around.getBlockX();
        int baseY = around.getBlockY();
        int baseZ = around.getBlockZ();

        for (int radius = 0; radius <= 3; radius++) {
            for (int dy = 2; dy >= -6; dy--) {
                int y = baseY + dy;
                if (y <= world.getMinHeight() || y + 2 >= world.getMaxHeight()) {
                    continue;
                }

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }

                        int x = baseX + dx;
                        int z = baseZ + dz;
                        Block floor = world.getBlockAt(x, y - 1, z);
                        Block feet = world.getBlockAt(x, y, z);
                        Block head = world.getBlockAt(x, y + 1, z);
                        if (floor.getType().isSolid()
                                && feet.isPassable()
                                && head.isPassable()
                                && feet.getType() != Material.WATER
                                && feet.getType() != Material.LAVA) {
                            return new Location(world, x + 0.5, y, z + 0.5, around.getYaw(), around.getPitch());
                        }
                    }
                }
            }
        }

        return null;
    }

    private boolean hasBlockToken(Player player) {
        return isBlockToken(player.getInventory().getItemInOffHand());
    }

    private boolean isBlockToken(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }

        return item.getItemMeta().getPersistentDataContainer()
                .has(blockTokenKey, PersistentDataType.BYTE);
    }

    private boolean handsAreEmpty(Player player) {
        return player.getInventory().getItemInMainHand().getType().isAir()
                && player.getInventory().getItemInOffHand().getType().isAir();
    }

    private byte boolByte(boolean value) {
        return (byte) (value ? 1 : 0);
    }

    private String friendlyName(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }
}
