package br.com.puraresenha.crafttudo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemCraftResult;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CraftTudoPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final int MENU_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final long CLICK_COOLDOWN_MS = 300L;
    private static final String[] CATEGORY_ORDER = {"ALL", "BUILDING", "EQUIPMENT", "REDSTONE", "MISC"};

    private final List<CraftingRecipe> recipes = new ArrayList<>();
    private final Map<UUID, MenuState> menuStates = new HashMap<>();
    private final Map<UUID, Long> lastCraftClick = new HashMap<>();
    private int maxCraftsPerClick;

    @Override
    public void onEnable() {
        getConfig().addDefault("max-crafts-per-click", 4096);
        getConfig().addDefault("show-all-recipes-by-default", false);
        getConfig().options().copyDefaults(true);
        saveConfig();
        readSettings();

        PluginCommand command = getCommand("crafttudo");
        if (command == null) {
            getLogger().severe("O comando crafttudo não existe no plugin.yml. Desativando o plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        command.setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        reloadRecipes();
        getLogger().info("CraftTudo 3.0.0 habilitado. Receitas suportadas: " + recipes.size());
    }

    @Override
    public void onDisable() {
        menuStates.clear();
        lastCraftClick.clear();
        recipes.clear();
    }

    private void readSettings() {
        maxCraftsPerClick = Math.max(1, Math.min(16384,
                getConfig().getInt("max-crafts-per-click", 4096)));
    }

    private void reloadRecipes() {
        recipes.clear();
        Iterator<Recipe> iterator = Bukkit.recipeIterator();

        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (!(recipe instanceof CraftingRecipe craftingRecipe)) continue;
            if (!(craftingRecipe instanceof ShapedRecipe)
                    && !(craftingRecipe instanceof ShapelessRecipe)) continue;

            ItemStack result = craftingRecipe.getResult();
            if (isEmpty(result)) continue;
            recipes.add(craftingRecipe);
        }

        recipes.sort(Comparator
                .comparing((CraftingRecipe recipe) -> recipe.getCategory().name())
                .thenComparing(recipe -> recipe.getResult().getType().name())
                .thenComparing(recipe -> recipe.getKey().toString()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("recarregar")) {
            if (!sender.hasPermission("crafttudo.admin")) {
                sender.sendMessage("§cVocê não tem permissão para recarregar o CraftTudo.");
                return true;
            }
            reloadConfig();
            readSettings();
            reloadRecipes();
            sender.sendMessage("§aCraftTudo 3.0 recarregado. Receitas suportadas: §f" + recipes.size());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando precisa ser usado por um jogador.");
            return true;
        }
        if (!player.hasPermission("crafttudo.use")) {
            player.sendMessage("§cVocê não tem permissão para usar o CraftTudo.");
            return true;
        }

        MenuState state = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> {
            MenuState newState = new MenuState();
            newState.showAll = getConfig().getBoolean("show-all-recipes-by-default", false);
            return newState;
        });

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("buscar")) {
                if (args.length < 2) {
                    player.sendMessage("§eUso: /crafttudo buscar <nome ou material>");
                    return true;
                }
                state.search = String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        .toLowerCase(Locale.ROOT).trim();
                state.page = 0;
                openMenu(player, state);
                return true;
            }
            if (args[0].equalsIgnoreCase("limpar")) {
                state.search = "";
                state.page = 0;
                openMenu(player, state);
                return true;
            }
            player.sendMessage("§eComandos: /crafttudo, /crafttudo buscar <texto>, /crafttudo limpar");
            return true;
        }

        state.page = 0;
        openMenu(player, state);
        return true;
    }

    private void openMenu(Player player, MenuState state) {
        List<CraftingRecipe> visible = getVisibleRecipes(player, state);
        int maxPage = Math.max(0, (visible.size() - 1) / PAGE_SIZE);
        state.page = Math.max(0, Math.min(state.page, maxPage));
        int start = state.page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, visible.size());
        List<CraftingRecipe> pageRecipes = new ArrayList<>(visible.subList(start, end));

        MenuHolder holder = new MenuHolder(state.page, pageRecipes);
        Inventory inventory = Bukkit.createInventory(
                holder, MENU_SIZE, "§6CraftTudo §8| §7" + (state.page + 1) + "/" + (maxPage + 1));
        holder.inventory = inventory;

        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < pageRecipes.size(); i++) {
            CraftingRecipe recipe = pageRecipes.get(i);
            boolean craftable = hasMatchingMatrix(player, recipe, storage);
            inventory.setItem(i, createRecipeIcon(recipe, craftable));
        }

        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, "§r");
        for (int slot = 45; slot < MENU_SIZE; slot++) inventory.setItem(slot, filler);

        if (state.page > 0) inventory.setItem(45, named(Material.ARROW, "§ePágina anterior"));
        inventory.setItem(47, searchButton(state));
        inventory.setItem(48, named(
                state.showAll ? Material.BARRIER : Material.LIME_DYE,
                state.showAll ? "§eFiltro: todas as receitas" : "§aFiltro: só fabricáveis",
                "§7Clique para alternar."));
        inventory.setItem(49, infoButton(visible.size(), state));
        inventory.setItem(50, categoryButton(state));
        inventory.setItem(51, named(Material.PAPER, "§eLimpar pesquisa",
                state.search.isEmpty() ? "§7Nenhuma pesquisa ativa." : "§f" + state.search));
        if (end < visible.size()) inventory.setItem(53, named(Material.ARROW, "§ePróxima página"));
        player.openInventory(inventory);
    }

    private List<CraftingRecipe> getVisibleRecipes(Player player, MenuState state) {
        List<CraftingRecipe> visible = new ArrayList<>();
        ItemStack[] storage = player.getInventory().getStorageContents();

        for (CraftingRecipe recipe : recipes) {
            if (!state.category.equals("ALL") && !recipe.getCategory().name().equals(state.category)) continue;
            if (!matchesSearch(recipe, state.search)) continue;

            boolean craftable = hasMatchingMatrix(player, recipe, storage);
            if (!state.showAll && !craftable) continue;
            visible.add(recipe);
        }
        return visible;
    }

    private boolean matchesSearch(CraftingRecipe recipe, String search) {
        if (search == null || search.isBlank()) return true;
        String normalized = search.toLowerCase(Locale.ROOT);
        String materialName = recipe.getResult().getType().name().toLowerCase(Locale.ROOT);
        String key = recipe.getKey().toString().toLowerCase(Locale.ROOT);
        ItemMeta meta = recipe.getResult().getItemMeta();
        String displayName = meta != null && meta.hasDisplayName()
                ? meta.getDisplayName().toLowerCase(Locale.ROOT) : "";
        return materialName.contains(normalized) || key.contains(normalized) || displayName.contains(normalized);
    }

    private ItemStack createRecipeIcon(CraftingRecipe recipe, boolean craftable) {
        ItemStack icon = recipe.getResult().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        List<String> lore = new ArrayList<>();
        lore.add(craftable ? "§aFabricável com seu inventário" : "§cFaltam ingredientes");
        lore.add("§7Categoria: §f" + recipe.getCategory().name());
        lore.add("§8" + recipe.getKey());
        lore.add("");
        lore.add(craftable ? "§eClique para fabricar o máximo." : "§7Consiga os ingredientes para liberar.");
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack searchButton(MenuState state) {
        return named(Material.COMPASS, "§ePesquisar receitas",
                "§7Use no chat:", "§f/crafttudo buscar <texto>",
                state.search.isEmpty() ? "§7Nenhuma pesquisa ativa." : "§aAtual: " + state.search);
    }

    private ItemStack infoButton(int total, MenuState state) {
        return named(Material.BOOK, "§6CraftTudo 3.0",
                "§7Receitas nesta lista: §f" + total,
                "§7Filtro: " + (state.showAll ? "§eTodas" : "§aFabricáveis"),
                "§7Categoria: §f" + state.category,
                "§7Pesquisa: §f" + (state.search.isEmpty() ? "nenhuma" : state.search),
                "",
                "§7Usa as escolhas de ingredientes e",
                "§7as sobras devolvidas pelo Paper.");
    }

    private ItemStack categoryButton(MenuState state) {
        return named(Material.CHEST, "§eCategoria: §f" + state.category,
                "§7Clique para mudar.", "§7Próxima: §f" + nextCategory(state.category));
    }

    private String nextCategory(String current) {
        for (int i = 0; i < CATEGORY_ORDER.length; i++) {
            if (CATEGORY_ORDER[i].equals(current)) return CATEGORY_ORDER[(i + 1) % CATEGORY_ORDER.length];
        }
        return "ALL";
    }

    private ItemStack named(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) meta.setLore(Arrays.asList(loreLines));
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
        MenuState state = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuState());

        if (slot == 45 && holder.page > 0) {
            state.page = holder.page - 1;
            openMenu(player, state);
            return;
        }
        if (slot == 53) {
            state.page = holder.page + 1;
            openMenu(player, state);
            return;
        }
        if (slot == 47) {
            player.closeInventory();
            player.sendMessage("§eDigite: §f/crafttudo buscar <nome ou material>");
            player.sendMessage("§7Para remover a pesquisa: §f/crafttudo limpar");
            return;
        }
        if (slot == 48) {
            state.showAll = !state.showAll;
            state.page = 0;
            openMenu(player, state);
            return;
        }
        if (slot == 49) {
            player.sendMessage("§6CraftTudo 3.0 §7| receitas suportadas: §f" + recipes.size());
            return;
        }
        if (slot == 50) {
            state.category = nextCategory(state.category);
            state.page = 0;
            openMenu(player, state);
            return;
        }
        if (slot == 51) {
            state.search = "";
            state.page = 0;
            openMenu(player, state);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE || slot >= holder.pageRecipes.size()) return;

        long now = System.currentTimeMillis();
        long last = lastCraftClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < CLICK_COOLDOWN_MS) return;
        lastCraftClick.put(player.getUniqueId(), now);

        CraftingRecipe selected = holder.pageRecipes.get(slot);
        int crafted = craftMaximum(player, selected);
        if (crafted > 0) {
            player.sendMessage("§aCraftTudo: fabricado §f" + crafted + "x §a"
                    + selected.getResult().getType().name() + ".");
        } else {
            player.sendMessage("§cNão foi possível fabricar. Confira os ingredientes e o espaço no inventário.");
        }
        openMenu(player, state);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private boolean hasMatchingMatrix(Player player, CraftingRecipe recipe, ItemStack[] storage) {
        MatrixPlan plan = createMatrixPlan(recipe, storage);
        if (plan == null) return false;
        Recipe matched = Bukkit.getCraftingRecipe(plan.matrix, player.getWorld());
        return matched instanceof CraftingRecipe matchedCrafting
                && matchedCrafting.getKey().equals(recipe.getKey());
    }

    private int craftMaximum(Player player, CraftingRecipe recipe) {
        ItemStack[] virtualStorage = cloneContents(player.getInventory().getStorageContents());
        int crafted = 0;

        while (crafted < maxCraftsPerClick) {
            MatrixPlan plan = createMatrixPlan(recipe, virtualStorage);
            if (plan == null) break;

            Recipe matched = Bukkit.getCraftingRecipe(plan.matrix, player.getWorld());
            if (!(matched instanceof CraftingRecipe matchedCrafting)
                    || !matchedCrafting.getKey().equals(recipe.getKey())) break;

            ItemCraftResult craftResult;
            try {
                craftResult = Bukkit.craftItemResult(plan.matrix, player.getWorld(), player);
            } catch (RuntimeException exception) {
                getLogger().warning("Falha ao simular a receita " + recipe.getKey()
                        + ": " + exception.getMessage());
                break;
            }

            ItemStack output = craftResult.getResult();
            if (isEmpty(output)) break;

            ItemStack[] nextStorage = cloneContents(virtualStorage);
            if (!consumeOnePerMatrixSlot(nextStorage, plan.sourceSlots)) break;

            int inventoryLimit = player.getInventory().getMaxStackSize();
            if (!addToStorage(nextStorage, output, inventoryLimit)) break;

            boolean leftoversFit = true;
            for (ItemStack remainder : craftResult.getResultingMatrix()) {
                if (!isEmpty(remainder) && !addToStorage(nextStorage, remainder, inventoryLimit)) {
                    leftoversFit = false;
                    break;
                }
            }
            if (!leftoversFit) break;

            for (ItemStack overflow : craftResult.getOverflowItems()) {
                if (!isEmpty(overflow) && !addToStorage(nextStorage, overflow, inventoryLimit)) {
                    leftoversFit = false;
                    break;
                }
            }
            if (!leftoversFit) break;

            virtualStorage = nextStorage;
            crafted++;
        }

        if (crafted > 0) {
            player.getInventory().setStorageContents(virtualStorage);
            player.updateInventory();
        }
        if (crafted == maxCraftsPerClick) {
            player.sendMessage("§eLimite por clique atingido: " + maxCraftsPerClick
                    + ". Clique novamente para continuar.");
        }
        return crafted;
    }

    private boolean consumeOnePerMatrixSlot(ItemStack[] storage, int[] sourceSlots) {
        for (int sourceSlot : sourceSlots) {
            if (sourceSlot < 0) continue;
            if (sourceSlot >= storage.length || isEmpty(storage[sourceSlot])) return false;
            ItemStack stack = storage[sourceSlot];
            if (stack.getAmount() <= 1) storage[sourceSlot] = null;
            else stack.setAmount(stack.getAmount() - 1);
        }
        return true;
    }

    private MatrixPlan createMatrixPlan(CraftingRecipe recipe, ItemStack[] storage) {
        List<IngredientAt> ingredients = new ArrayList<>();

        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
            if (shape.length > 3) return null;

            for (int row = 0; row < shape.length; row++) {
                String line = shape[row];
                if (line.length() > 3) return null;
                for (int column = 0; column < line.length(); column++) {
                    char symbol = line.charAt(column);
                    if (symbol == ' ') continue;
                    RecipeChoice choice = choices.get(symbol);
                    if (choice == null) return null;
                    ingredients.add(new IngredientAt(row * 3 + column, choice));
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            List<RecipeChoice> choices = shapeless.getChoiceList();
            if (choices.isEmpty() || choices.size() > 9) return null;
            for (int i = 0; i < choices.size(); i++) {
                ingredients.add(new IngredientAt(i, choices.get(i)));
            }
        } else {
            return null;
        }

        if (ingredients.isEmpty() || ingredients.size() > 9) return null;

        ingredients.sort(Comparator.comparingInt(
                ingredient -> countMatchingUnits(ingredient.choice, storage)));

        ItemStack[] matrix = new ItemStack[9];
        int[] sourceSlots = new int[9];
        Arrays.fill(sourceSlots, -1);
        int[] usedFromSlot = new int[storage.length];

        if (!assignIngredients(0, ingredients, storage, usedFromSlot, matrix, sourceSlots)) return null;
        return new MatrixPlan(matrix, sourceSlots);
    }

    private boolean assignIngredients(
            int index,
            List<IngredientAt> ingredients,
            ItemStack[] storage,
            int[] usedFromSlot,
            ItemStack[] matrix,
            int[] sourceSlots
    ) {
        if (index >= ingredients.size()) return true;

        IngredientAt ingredient = ingredients.get(index);
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack available = storage[slot];
            if (isEmpty(available) || available.getAmount() <= usedFromSlot[slot]) continue;
            if (!choiceMatches(ingredient.choice, available)) continue;

            ItemStack oneItem = available.clone();
            oneItem.setAmount(1);
            matrix[ingredient.matrixSlot] = oneItem;
            sourceSlots[ingredient.matrixSlot] = slot;
            usedFromSlot[slot]++;

            if (assignIngredients(index + 1, ingredients, storage,
                    usedFromSlot, matrix, sourceSlots)) return true;

            usedFromSlot[slot]--;
            matrix[ingredient.matrixSlot] = null;
            sourceSlots[ingredient.matrixSlot] = -1;
        }
        return false;
    }

    private int countMatchingUnits(RecipeChoice choice, ItemStack[] storage) {
        int count = 0;
        for (ItemStack item : storage) {
            if (!isEmpty(item) && choiceMatches(choice, item)) count += item.getAmount();
        }
        return count;
    }

    private boolean choiceMatches(RecipeChoice choice, ItemStack item) {
        try {
            return choice != null && !isEmpty(item) && choice.test(item);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean addToStorage(ItemStack[] storage, ItemStack source, int inventoryLimit) {
        if (isEmpty(source)) return true;

        ItemStack remaining = source.clone();
        int maxStack = Math.max(1, Math.min(inventoryLimit, remaining.getMaxStackSize()));

        for (int slot = 0; slot < storage.length && remaining.getAmount() > 0; slot++) {
            ItemStack current = storage[slot];
            if (isEmpty(current) || !current.isSimilar(remaining)) continue;
            int slotMax = Math.max(1, Math.min(inventoryLimit, current.getMaxStackSize()));
            int room = slotMax - current.getAmount();
            if (room <= 0) continue;
            int moved = Math.min(room, remaining.getAmount());
            current.setAmount(current.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
        }

        for (int slot = 0; slot < storage.length && remaining.getAmount() > 0; slot++) {
            if (!isEmpty(storage[slot])) continue;
            int moved = Math.min(maxStack, remaining.getAmount());
            ItemStack newStack = remaining.clone();
            newStack.setAmount(moved);
            storage[slot] = newStack;
            remaining.setAmount(remaining.getAmount() - moved);
        }
        return remaining.getAmount() <= 0;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = isEmpty(contents[i]) ? null : contents[i].clone();
        }
        return copy;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static final class IngredientAt {
        private final int matrixSlot;
        private final RecipeChoice choice;

        private IngredientAt(int matrixSlot, RecipeChoice choice) {
            this.matrixSlot = matrixSlot;
            this.choice = choice;
        }
    }

    private static final class MatrixPlan {
        private final ItemStack[] matrix;
        private final int[] sourceSlots;

        private MatrixPlan(ItemStack[] matrix, int[] sourceSlots) {
            this.matrix = matrix;
            this.sourceSlots = sourceSlots;
        }
    }

    private static final class MenuState {
        private int page;
        private boolean showAll;
        private String category = "ALL";
        private String search = "";
    }

    private static final class MenuHolder implements InventoryHolder {
        private final int page;
        private final List<CraftingRecipe> pageRecipes;
        private Inventory inventory;

        private MenuHolder(int page, List<CraftingRecipe> pageRecipes) {
            this.page = page;
            this.pageRecipes = List.copyOf(pageRecipes);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
