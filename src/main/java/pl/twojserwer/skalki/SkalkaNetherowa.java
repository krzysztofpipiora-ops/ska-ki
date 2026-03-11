package pl.twojserwer.skalki;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class SkalkaNetherowa extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Integer> crystalHP = new HashMap<>();
    private final Set<UUID> activeGuards = new HashSet<>();
    private final Map<UUID, Location> respawnLocs = new HashMap<>();
    private final Random random = new Random();
    private NamespacedKey crystalKey;

    @Override
    public void onEnable() {
        this.crystalKey = new NamespacedKey(this, "is_nether_crystal");
        
        // Rejestracja z dodatkowym sprawdzeniem
        if (getCommand("skalka") != null) {
            getCommand("skalka").setExecutor(this);
        } else {
            getLogger().severe("NIE UDALO SIE ZAREJESTROWAC KOMENDY! Sprawdz plugin.yml");
        }
        
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        
        getLogger().info("=== Plugin SkalkaNetherowa zostal zaladowany! ===");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Komenda tylko dla graczy!");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "Nie masz uprawnien (OP)!");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            try {
                spawnCrystal(player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Skałka postawiona!");
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Blad! Sprawdz konsole serwera.");
                e.printStackTrace();
            }
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Uzyj: /skalka set");
        return true;
    }

    private void spawnCrystal(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        EnderCrystal crystal = (EnderCrystal) world.spawnEntity(loc, EntityType.ENDER_CRYSTAL);
        crystal.setShowingBottom(true);
        crystal.setCustomName("§c§lSkałka Netherowa");
        crystal.setCustomNameVisible(true);
        
        // Oznaczamy krysztal, by wiedziec ze to nasz
        crystal.getPersistentDataContainer().set(crystalKey, PersistentDataType.BYTE, (byte) 1);

        UUID id = crystal.getUniqueId();
        crystalHP.put(id, 4);
        respawnLocs.put(id, loc.clone());
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        if (!crystal.getPersistentDataContainer().has(crystalKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        if (!(event.getDamager() instanceof Player player)) return;

        // Czy sa zywe moby?
        activeGuards.removeIf(uuid -> Bukkit.getEntity(uuid) == null || Bukkit.getEntity(uuid).isDead());
        
        if (!activeGuards.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Najpierw pokonaj straznikow!");
            return;
        }

        UUID id = crystal.getUniqueId();
        int hp = crystalHP.getOrDefault(id, 4) - 1;
        crystalHP.put(id, hp);

        if (hp <= 0) {
            spawnGuards(crystal.getLocation());
            player.sendMessage(ChatColor.GOLD + "Pojawili sie straznicy!");
        } else {
            player.sendMessage(ChatColor.YELLOW + "HP Skałki: " + ChatColor.RED + hp + "/4");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_HIT, 1f, 1f);
        }
    }

    private void spawnGuards(Location loc) {
        for (int i = 0; i < 3; i++) {
            EntityType type = random.nextBoolean() ? EntityType.WITHER_SKELETON : EntityType.BLAZE;
            LivingEntity m = (LivingEntity) loc.getWorld().spawnEntity(loc.clone().add(1, 0, 1), type);
            m.setCustomName("§4Straznik");
            activeGuards.add(m.getUniqueId());
        }
    }

    @EventHandler
    public void onGuardDeath(EntityDeathEvent event) {
        if (activeGuards.contains(event.getEntity().getUniqueId())) {
            activeGuards.remove(event.getEntity().getUniqueId());

            if (activeGuards.isEmpty()) {
                // Rozbijamy najblizszy krysztal
                for (Entity e : event.getEntity().getNearbyEntities(10, 10, 10)) {
                    if (e instanceof EnderCrystal crystal && crystal.getPersistentDataContainer().has(crystalKey, PersistentDataType.BYTE)) {
                        finishCrystal(crystal);
                        break;
                    }
                }
            }
        }
    }

    private void finishCrystal(EnderCrystal crystal) {
        Location loc = crystal.getLocation();
        Location rLoc = respawnLocs.get(crystal.getUniqueId());

        dropItems(loc);
        
        crystal.remove();
        crystalHP.remove(crystal.getUniqueId());

        Bukkit.broadcastMessage("§6§l[!] §eSkałka rozbita! Odrodzi sie za 30 minut.");
        Bukkit.getScheduler().runTaskLater(this, () -> spawnCrystal(rLoc), 36000L);
    }

    private void dropItems(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        w.dropItemNaturally(loc, new ItemStack(Material.GHAST_TEAR, 1));
        w.dropItemNaturally(loc, new ItemStack(Material.DIAMOND, 2));
        w.dropItemNaturally(loc, new ItemStack(Material.BLAZE_ROD, 2));
        w.dropItemNaturally(loc, new ItemStack(Material.GOLD_INGOT, 4));
        if (random.nextDouble() < 0.1) w.dropItemNaturally(loc, new ItemStack(Material.NETHERITE_SCRAP));
    }
}
