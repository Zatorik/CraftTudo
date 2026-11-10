package br.com.puraresenha.crafttudo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class CraftTudoPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final int MENU_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private List<CraftingRecipe> recipes = new ArrayList<>();

    @Override
    public void onEnable() {
        PluginCommand command = getCommand("crafttudo");
        if (command != null) command.setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        loadRecipes();
        getLogger().info("CraftTudo 2.0 habilitado. Receitas encontradas: " + recipes.size());
    }

    private void loadRecipes() {
        recipes.clear();
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe instanceof CraftingRecipe crafting && crafting.getResult() != null
                    && crafting.getResult().getType() != Material.AIR) {
                recipes.add(crafting);
            }
        }
        recipes.sort(Comparator.comparing(r -> r.getResult().getType().name()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado por um jogador.");
            return true;
        }
        if (!player.hasPermission("crafttudo.use")) {
            player.sendMessage("§cVocê não tem permissão para usar o CraftTudo.");
            return true;
        }
        openMenu(player, 0);
        return true;
    }

    private void openMenu(Player player, int page) {
        MenuHolder holder = new MenuHolder(page);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE, "§6CraftTudo §8| §7Página " + (page + 1));
        holder.inventory = inv;

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, recipes.size());
        for (int i = start; i < end; i++) {
            CraftingRecipe recipe = recipes.get(i);
            ItemStack icon = recipe.getResult().clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add("§7Clique para fabricar o máximo possível");
                lore.add("§eReceita: §f" + recipe.getKey());
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inv.setItem(i - start, icon);
        }
        if (page > 0) inv.setItem(45, named(Material.ARROW, "§ePágina anterior"));
        inv.setItem(49, named(Material.BOOK, "§7Clique numa receita para fabricar"));
        if (end < recipes.size()) inv.setItem(53, named(Material.ARROW, "§ePróxima página"));
        player.openInventory(inv);
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getRawSlot();
        if (slot == 45 && holder.page > 0) {
            openMenu(player, holder.page - 1);
            return;
        }
        if (slot == 53 && (holder.page + 1) * PAGE_SIZE < recipes.size()) {
            openMenu(player, holder.page + 1);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) return;

        int index = holder.page * PAGE_SIZE + slot;
        if (index >= recipes.size()) return;

        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 350) return;
        cooldowns.put(player.getUniqueId(), now);

        int crafted = craftMaximum(player, recipes.get(index));
        if (crafted > 0) {
            player.sendMessage("§aVocê fabricou §f" + crafted + "x §aitem(ns).");
        } else {
            player.sendMessage("§cVocê não tem materiais suficientes ou espaço no inventário.");
        }
    }

    private int craftMaximum(Player player, CraftingRecipe recipe) {
        int crafted = 0;
        int safety = 4096;
        while (crafted < safety && canPayAndFit(player, recipe)) {
            consumeIngredients(player, recipe);
            giveResult(player, recipe.getResult());
            crafted++;
        }
        return crafted;
    }

    private boolean canPayAndFit(Player player, CraftingRecipe recipe) {
        Map<Material, Integer> required = ingredientCounts(recipe);
        if (required.isEmpty()) return false;

        Map<Material, Integer> available = new HashMap<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() != Material.AIR) {
                available.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }

        ItemStack result = recipe.getResult();
        int remaining = result.getAmount();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) remaining -= result.getMaxStackSize();
            else if (item.isSimilar(result)) remaining -= Math.max(0, item.getMaxStackSize() - item.getAmount());
            if (remaining <= 0) return true;
        }
        return remaining <= 0;
    }

    private Map<Material, Integer> ingredientCounts(CraftingRecipe recipe) {
        Map<Material, Integer> counts = new HashMap<>();
        if (recipe instanceof ShapelessRecipe shapeless) {
            shapeless.getChoiceList().forEach(choice -> {
                Material material = firstMaterial(choice);
                if (material != null) counts.merge(material, 1, Integer::sum);
            });
        } else if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            Map<Character, org.bukkit.inventory.RecipeChoice> choices = shaped.getChoiceMap();
            for (String line : shape) {
                for (char c : line.toCharArray()) {
                    if (c == ' ') continue;
                    Material material = firstMaterial(choices.get(c));
                    if (material != null) counts.merge(material, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private Material firstMaterial(org.bukkit.inventory.RecipeChoice choice) {
        if (choice == null) return null;
        if (choice instanceof org.bukkit.inventory.RecipeChoice.MaterialChoice mc) {
            return mc.getChoices().isEmpty() ? null : mc.getChoices().get(0);
        }
        if (choice instanceof org.bukkit.inventory.RecipeChoice.ExactChoice ec) {
            return ec.getChoices().isEmpty() ? null : ec.getChoices().get(0).getType();
        }
        return null;
    }

    private void consumeIngredients(Player player, CraftingRecipe recipe) {
        Map<Material, Integer> required = ingredientCounts(recipe);
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            int left = entry.getValue();
            for (int slot = 0; slot < 36 && left > 0; slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                if (item == null || item.getType() != entry.getKey()) continue;
                int take = Math.min(left, item.getAmount());
                item.setAmount(item.getAmount() - take);
                if (item.getAmount() <= 0) player.getInventory().setItem(slot, null);
                left -= take;
            }
        }
    }

    private void giveResult(Player player, ItemStack result) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(result.clone());
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private static final class MenuHolder implements InventoryHolder {
        private final int page;
        private Inventory inventory;
        private MenuHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
