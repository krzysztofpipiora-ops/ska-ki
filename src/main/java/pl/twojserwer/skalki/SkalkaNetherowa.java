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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class SkalkaNetherowa extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Integer> crystalHP = new HashMap<>();
    private final Set<UUID> activeGuards = new HashSet<>();
    private final Map<UUID, Location> respawnLocations = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        // Rejestracja komendy i eventów
        if (getCommand("skalka") != null) {
            getCommand("skalka").setExecutor(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        
        // Tworzenie folderu pluginu
        saveDefaultConfig();
        getLogger().info("Plugin SkalkaNetherowa zostal wlaczony!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko gracz moze uzywac tej komendy!");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage("§cNie masz uprawnien!");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            Location loc = player.getLocation();
            spawnCrystal(loc);
            player.sendMessage("§a§l[!] §7Skałka zostala utworzona w Twojej pozycji!");
            return true;
        }
        
        player.sendMessage("§eUzyj: /skalka set");
        return true;
    }

    private void spawnCrystal(Location loc) {
        if (loc.getWorld() == null) return;
        
        EnderCrystal crystal = (EnderCrystal) loc.getWorld().spawnEntity(loc, EntityType.ENDER_CRYSTAL);
        crystal.setShowingBottom(true);
        crystal.setCustomName("§c§lSkałka Netherowa");
        crystal.setCustomNameVisible(true);
        
        UUID id = crystal.getUniqueId();
        crystalHP.put(id, 4);
        respawnLocations.put(id, loc.clone());
    }

    @EventHandler
    public void onCrystalHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        
        // Sprawdzamy czy to nasza skałka (czy jest w mapie HP)
        if (!crystalHP.containsKey(crystal.getUniqueId())) return;

        event.setCancelled(true);

        if (!(event.getDamager() instanceof Player player)) return;

        if (hasLivingGuards()) {
            player.sendMessage("§c§l[!] §7Musisz najpierw zabic straznikow!");
            return;
        }

        UUID id = crystal.getUniqueId();
        int hp = crystalHP.get(id) - 1;
        crystalHP.put(id, hp);

        if (hp <= 0) {
            spawnGuards(crystal.getLocation());
            player.sendMessage("§6§l[!] §eStraznicy sie pojawili! Pokonaj ich!");
        } else {
            player.sendMessage("§e§l[!] §7Skałka: §6" + hp + "§7/4 HP");
            crystal.getWorld().spawnParticle(Particle.BLOCK_CRACK, crystal.getLocation().add(0, 1, 0), 20, Bukkit.createBlockData(Material.NETHERRACK));
        }
    }

    private void spawnGuards(Location loc) {
        for (int i = 0; i < 3; i++) {
            EntityType type = (random.nextBoolean()) ? EntityType.WITHER_SKELETON : EntityType.BLAZE;
            Monster m = (Monster) loc.getWorld().spawnEntity(loc.clone().add(random.nextDouble()*2, 0.5, random.nextDouble()*2), type);
            m.setCustomName("§4Straznik Skalki");
            activeGuards.add(m.getUniqueId());
        }
    }

    private boolean hasLivingGuards() {
        activeGuards.removeIf(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            return e == null || e.isDead();
        });
        return !activeGuards.isEmpty();
    }

    @EventHandler
    public void onGuardDeath(EntityDeathEvent event) {
        if (activeGuards.contains(event.getEntity().getUniqueId())) {
            activeGuards.remove(event.getEntity().getUniqueId());
            
            if (activeGuards.isEmpty()) {
                // Szukamy kryształu w pobliżu miejsca śmierci ostatniego moba
                event.getEntity().getNearbyEntities(10, 10, 10).stream()
                    .filter(e -> e instanceof EnderCrystal && crystalHP.containsKey(e.getUniqueId()))
                    .map(e -> (EnderCrystal) e)
                    .findFirst()
                    .ifPresent(this::giveRewardAndDestroy);
            }
        }
    }

    private void giveRewardAndDestroy(EnderCrystal crystal) {
        Location loc = crystal.getLocation();
        Location respawnLoc = respawnLocations.get(crystal.getUniqueId());

        // Drop
        dropItems(loc);

        // Efekty
        loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

        crystalHP.remove(crystal.getUniqueId());
        respawnLocations.remove(crystal.getUniqueId());
        crystal.remove();

        Bukkit.broadcastMessage("§6§l[Skałka] §eZniszczona! Odrodzi sie za 30 minut.");

        // Respawn po 30 min (1800 sekund)
        Bukkit.getScheduler().runTaskLater(this, () -> spawnCrystal(respawnLoc), 20L * 1800L);
    }

    private void dropItems(Location loc) {
        World w = loc.getWorld();
        w.dropItemNaturally(loc, new ItemStack(Material.GHAST_TEAR, random.nextInt(2) + 1));
        w.dropItemNaturally(loc, new ItemStack(Material.DIAMOND, random.nextInt(2) + 1));
        w.dropItemNaturally(loc, new ItemStack(Material.IRON_INGOT, 4));
        w.dropItemNaturally(loc, new ItemStack(Material.GOLD_INGOT, 4));
        w.dropItemNaturally(loc, new ItemStack(Material.BLAZE_ROD, 2));
        w.dropItemNaturally(loc, new ItemStack(Material.NETHER_WART, 3));

        if (random.nextDouble() < 0.20) w.dropItemNaturally(loc, new ItemStack(Material.GOLDEN_APPLE));
        if (random.nextDouble() < 0.10) w.dropItemNaturally(loc, new ItemStack(Material.WITHER_SKELETON_SKULL));
        if (random.nextDouble() < 0.05) w.dropItemNaturally(loc, new ItemStack(Material.NETHERITE_SCRAP));
    }
}
