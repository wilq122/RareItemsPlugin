package pl.yourname.rareitems;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RareItemsPlugin extends JavaPlugin implements Listener {
    
    private FileConfiguration tradesConfig;
    private File tradesFile;
    private NamespacedKey rareItemKey;
    private NamespacedKey villagerKey;
    private Map<String, Double> blockChances;
    private Map<String, String> rareItems;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        createTradesConfig();
        rareItemKey = new NamespacedKey(this, "rare_item");
        villagerKey = new NamespacedKey(this, "rare_trader");
        
        initializeDefaults();
        loadConfiguration();
        
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("RareItems Plugin włączony!");
    }
    
    private void initializeDefaults() {
        blockChances = new HashMap<>();
        blockChances.put("STONE", 0.1);
        blockChances.put("COAL_ORE", 0.5);
        blockChances.put("IRON_ORE", 1.0);
        blockChances.put("GOLD_ORE", 2.0);
        blockChances.put("DIAMOND_ORE", 5.0);
        
        rareItems = new HashMap<>();
        rareItems.put("ancient_gem", "§6Starożytny Klejnot");
        rareItems.put("mystic_shard", "§5Mistyczny Odłamek");
        rareItems.put("dragon_scale", "§cŁuska Smoka");
    }
    
    private void loadConfiguration() {
        FileConfiguration config = getConfig();
        for (String key : blockChances.keySet()) {
            blockChances.put(key, config.getDouble("chances." + key, blockChances.get(key)));
        }
        
        if (config.getConfigurationSection("rare_items") != null) {
            rareItems.clear();
            for (String key : config.getConfigurationSection("rare_items").getKeys(false)) {
                rareItems.put(key, config.getString("rare_items." + key));
            }
        }
    }
    
    private void createTradesConfig() {
        tradesFile = new File(getDataFolder(), "trades.yml");
        if (!tradesFile.exists()) {
            tradesFile.getParentFile().mkdirs();
            saveResource("trades.yml", false);
        }
        tradesConfig = YamlConfiguration.loadConfiguration(tradesFile);
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        
        if (!blockChances.containsKey(b.getType().name())) return;
        
        double chance = blockChances.get(b.getType().name());
        if (Math.random() * 100 > chance) return;
        
        String[] itemKeys = rareItems.keySet().toArray(new String[0]);
        String selectedKey = itemKeys[new Random().nextInt(itemKeys.length)];
        
        ItemStack rareItem = createRareItem(selectedKey);
        p.getInventory().addItem(rareItem);
        
        Bukkit.broadcastMessage(ChatColor.GOLD + "Gratulacje! Gracz " + 
            ChatColor.YELLOW + p.getName() + ChatColor.GOLD + 
            " wykopał właśnie rzadki przedmiot!");
    }
    
    private ItemStack createRareItem(String key) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(rareItems.get(key));
        meta.setLore(Arrays.asList("§7Rzadki przedmiot", "§7Można wymienić u handlarza"));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.getPersistentDataContainer().set(rareItemKey, PersistentDataType.STRING, key);
        item.setItemMeta(meta);
        return item;
    }
    
    @EventHandler
    public void onVillagerClick(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager)) return;
        
        Villager villager = (Villager) e.getRightClicked();
        if (!villager.getPersistentDataContainer().has(villagerKey, PersistentDataType.BYTE)) return;
        
        e.setCancelled(true);
        openTradeGUI(e.getPlayer(), villager);
    }
    
    private void openTradeGUI(Player p, Villager villager) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6Wymiana Rzadkich Przedmiotów");
        
        List<String> trades = tradesConfig.getStringList("trades");
        int slot = 0;
        
        for (String trade : trades) {
            if (slot >= 21) break;
            String[] parts = trade.split(":");
            if (parts.length < 4) continue;
            
            String rareItemKey = parts[0];
            int amount = Integer.parseInt(parts[1]);
            Material reward = Material.valueOf(parts[2]);
            int rewardAmount = Integer.parseInt(parts[3]);
            
            ItemStack display = new ItemStack(reward, rewardAmount);
            ItemMeta meta = display.getItemMeta();
            meta.setDisplayName("§a" + reward.name());
            meta.setLore(Arrays.asList(
                "§7Wymiana: " + amount + "x " + rareItems.get(rareItemKey),
                "§7Za: " + rewardAmount + "x " + reward.name(),
                "§eKliknij aby wymienić!"
            ));
            display.setItemMeta(meta);
            
            inv.setItem(slot, display);
            slot++;
        }
        
        p.openInventory(inv);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("§6Wymiana Rzadkich Przedmiotów")) return;
        
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        
        Player p = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        int slot = e.getSlot();
        List<String> trades = tradesConfig.getStringList("trades");
        
        if (slot >= trades.size()) return;
        
        String trade = trades.get(slot);
        String[] parts = trade.split(":");
        String rareItemKey = parts[0];
        int requiredAmount = Integer.parseInt(parts[1]);
        Material reward = Material.valueOf(parts[2]);
        int rewardAmount = Integer.parseInt(parts[3]);
        
        int playerAmount = countRareItems(p, rareItemKey);
        if (playerAmount < requiredAmount) {
            p.sendMessage("§cNie masz wystarczająco rzadkich przedmiotów!");
            return;
        }
        
        removeRareItems(p, rareItemKey, requiredAmount);
        p.getInventory().addItem(new ItemStack(reward, rewardAmount));
        p.sendMessage("§aWymiana zakończona sukcesem!");
        p.closeInventory();
    }
    
    private int countRareItems(Player p, String key) {
        int count = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            if (!item.getItemMeta().getPersistentDataContainer().has(rareItemKey, PersistentDataType.STRING)) continue;
            
            String itemKey = item.getItemMeta().getPersistentDataContainer().get(rareItemKey, PersistentDataType.STRING);
            if (key.equals(itemKey)) count += item.getAmount();
        }
        return count;
    }
    
    private void removeRareItems(Player p, String key, int amount) {
        int toRemove = amount;
        for (ItemStack item : p.getInventory().getContents()) {
            if (toRemove <= 0) break;
            if (item == null || !item.hasItemMeta()) continue;
            if (!item.getItemMeta().getPersistentDataContainer().has(rareItemKey, PersistentDataType.STRING)) continue;
            
            String itemKey = item.getItemMeta().getPersistentDataContainer().get(rareItemKey, PersistentDataType.STRING);
            if (!key.equals(itemKey)) continue;
            
            int itemAmount = item.getAmount();
            if (itemAmount <= toRemove) {
                toRemove -= itemAmount;
                item.setAmount(0);
            } else {
                item.setAmount(itemAmount - toRemove);
                toRemove = 0;
            }
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("rareitems")) return false;
        
        if (!sender.hasPermission("rareitems.admin")) {
            sender.sendMessage("§cBrak uprawnień!");
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage("§6=== RareItems Commands ===");
            sender.sendMessage("§e/rareitems reload §7- Przeładuj konfigurację");
            sender.sendMessage("§e/rareitems give <gracz> <przedmiot> §7- Daj rzadki przedmiot");
            sender.sendMessage("§e/rareitems villager §7- Stwórz handlarza");
            sender.sendMessage("§e/rareitems trade add <item> <amount> <reward> <rewardAmount>");
            sender.sendMessage("§e/rareitems trade remove <index>");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                reloadConfig();
                loadConfiguration();
                sender.sendMessage("§aKonfiguracja przeładowana!");
                break;
                
            case "give":
                if (args.length < 3) {
                    sender.sendMessage("§cUżycie: /rareitems give <gracz> <przedmiot>");
                    return true;
                }
                
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cGracz nie znaleziony!");
                    return true;
                }
                
                if (!rareItems.containsKey(args[2])) {
                    sender.sendMessage("§cNieznany przedmiot! Dostępne: " + String.join(", ", rareItems.keySet()));
                    return true;
                }
                
                target.getInventory().addItem(createRareItem(args[2]));
                sender.sendMessage("§aPrzedmiot został dany graczowi!");
                break;
                
            case "villager":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cKomenda tylko dla graczy!");
                    return true;
                }
                
                Player p = (Player) sender;
                Villager villager = p.getWorld().spawn(p.getLocation(), Villager.class);
                villager.setCustomName("§6Handlarz Rzadkich Przedmiotów");
                villager.setCustomNameVisible(true);
                villager.getPersistentDataContainer().set(villagerKey, PersistentDataType.BYTE, (byte) 1);
                villager.setAI(false);
                sender.sendMessage("§aHandlarz został stworzony!");
                break;
                
            case "trade":
                if (args.length < 2) {
                    sender.sendMessage("§cUżycie: /rareitems trade <add|remove|list>");
                    return true;
                }
                
                switch (args[1].toLowerCase()) {
                    case "add":
                        if (args.length < 6) {
                            sender.sendMessage("§cUżycie: /rareitems trade add <item> <amount> <reward> <rewardAmount>");
                            return true;
                        }
                        
                        String newTrade = args[2] + ":" + args[3] + ":" + args[4] + ":" + args[5];
                        List<String> trades = tradesConfig.getStringList("trades");
                        trades.add(newTrade);
                        tradesConfig.set("trades", trades);
                        saveTradesConfig();
                        sender.sendMessage("§aWymiana została dodana!");
                        break;
                        
                    case "remove":
                        if (args.length < 3) {
                            sender.sendMessage("§cUżycie: /rareitems trade remove <index>");
                            return true;
                        }
                        
                        try {
                            int index = Integer.parseInt(args[2]);
                            List<String> tradesList = tradesConfig.getStringList("trades");
                            if (index < 0 || index >= tradesList.size()) {
                                sender.sendMessage("§cNieprawidłowy indeks!");
                                return true;
                            }
                            tradesList.remove(index);
                            tradesConfig.set("trades", tradesList);
                            saveTradesConfig();
                            sender.sendMessage("§aWymiana została usunięta!");
                        } catch (NumberFormatException e) {
                            sender.sendMessage("§cNieprawidłowy numer!");
                        }
                        break;
                        
                    case "list":
                        List<String> allTrades = tradesConfig.getStringList("trades");
                        sender.sendMessage("§6=== Lista Wymian ===");
                        for (int i = 0; i < allTrades.size(); i++) {
                            sender.sendMessage("§e" + i + ": §7" + allTrades.get(i));
                        }
                        break;
                }
                break;
                
            default:
                sender.sendMessage("§cNieznana komenda!");
        }
        
        return true;
    }
    
    private void saveTradesConfig() {
        try {
            tradesConfig.save(tradesFile);
        } catch (IOException e) {
            getLogger().severe("Nie można zapisać trades.yml!");
        }
    }
}
