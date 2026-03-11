package pl.twojserwer.skalki;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

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
        saveDefaultConfig();
        
        Objects.requireNonNull(getCommand("skalka")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        
        // Wczytaj skalki po restarcie
        loadCrystalsFromConfig();
        
        getLogger().info("SkalkaNetherowa 1.20.4 (Paper) zaladowana pomyślnie!");
    }

    private void loadCrystalsFromConfig() {
        if (getConfig().getConfigurationSection("locations") == null) return;
        for (String key : getConfig().getConfigurationSection("locations").getKeys(false)) {
            Location loc = getConfig().getLocation("locations." + key);
            if (loc != null) spawnCrystal(loc, false);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) return true;

        if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            spawnCrystal(player.getLocation(), true);
            player.sendMessage("§a§l[!] §7Skałka została postawiona i zapisana!");
            return true;
        }
        return false;
    }

    private void spawnCrystal(Location loc, boolean saveToConfig) {
        World world = loc.getWorld();
        if (world == null) return;

        EnderCrystal crystal = world.spawn(loc, EnderCrystal.class, (entity) -> {
            entity.setShowingBottom(true);
            entity.setCustomNameVisible(true);
            entity.setCustomName("§c§lSkałka Netherowa");
            entity.getPersistentDataContainer().set(crystalKey, PersistentDataType.BYTE, (byte) 1);
        });

        UUID id = crystal.getUniqueId();
        crystalHP.put(id, 4);
        respawnLocs.put(id, loc.clone());

        if (saveToConfig) {
            String configPath = "locations." + id;
            getConfig().set(configPath, loc);
            saveConfig();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        if (!crystal.getPersistentDataContainer().has(crystalKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player player)) return;

        activeGuards.removeIf(uuid -> Bukkit.getEntity(uuid) == null || Bukkit.getEntity(uuid).isDead());
        if (!activeGuards.isEmpty()) {
            player.sendMessage("§c§l[!] §7Pokonaj strażników!");
            return;
        }

        int hp = crystalHP.getOrDefault(crystal.getUniqueId(), 4) - 1;
        crystalHP.put(crystal.getUniqueId(), hp);

        if (hp <= 0) {
            spawnGuards(crystal.getLocation());
            player.sendMessage("§6§l[!] §ePojawili się strażnicy!");
        } else {
            player.sendMessage("§e§l[!] §7Moc skałki: §6" + hp + "/4");
            player.playSound(player.getLocation(), Sound.BLOCK_NETHERRACK_HIT, 1f, 1f);
        }
    }

    private void spawnGuards(Location loc) {
        for (int i = 0; i < 3; i++) {
            EntityType type = random.nextBoolean() ? EntityType.WITHER_SKELETON : EntityType.BLAZE;
            LivingEntity m = (LivingEntity) loc.getWorld().spawnEntity(loc.clone().add(random.nextDouble()*2, 0, random.nextDouble()*2), type);
            m.setCustomName("§4Strażnik Skałki");
            activeGuards.add(m.getUniqueId());
        }
    }

    @EventHandler
    public void onGuardDeath(EntityDeathEvent event) {
        if (!activeGuards.contains(event.getEntity().getUniqueId())) return;
        activeGuards.remove(event.getEntity().getUniqueId());

        if (activeGuards.isEmpty()) {
            event.getEntity().getNearbyEntities(10, 10, 10).stream()
                .filter(e -> e instanceof EnderCrystal && e.getPersistentDataContainer().has(crystalKey, PersistentDataType.BYTE))
                .findFirst().ifPresent(e -> finishCrystal((EnderCrystal) e));
        }
    }

    private void finishCrystal(EnderCrystal crystal) {
        Location loc = crystal.getLocation();
        Location rLoc = respawnLocs.get(crystal.getUniqueId());

        dropLoot(loc);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);

        crystalHP.remove(crystal.getUniqueId());
        respawnLocs.remove(crystal.getUniqueId());
        crystal.remove();

        Bukkit.broadcastMessage("§6§l[!] §7Skałka rozbita! Odrodzi się za 30 minut.");
        Bukkit.getScheduler().runTaskLater(this, () -> spawnCrystal(rLoc, false), 36000L);
    }

    private void dropLoot(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        w.dropItemNaturally(loc, new ItemStack(Material.GHAST_TEAR, 1));
        w.dropItemNaturally(loc, new ItemStack(Material.DIAMOND, 2));
        w.dropItemNaturally(loc, new ItemStack(Material.IRON_INGOT, 5));
        w.dropItemNaturally(loc, new ItemStack(Material.GOLD_INGOT, 5));
        w.dropItemNaturally(loc, new ItemStack(Material.BLAZE_ROD, 2));
        w.dropItemNaturally(loc, new ItemStack(Material.NETHER_WART, 4));

        if (random.nextDouble() < 0.20) w.dropItemNaturally(loc, new ItemStack(Material.GOLDEN_APPLE));
        if (random.nextDouble() < 0.10) w.dropItemNaturally(loc, new ItemStack(Material.WITHER_SKELETON_SKULL));
        if (random.nextDouble() < 0.05) w.dropItemNaturally(loc, new ItemStack(Material.NETHERITE_SCRAP));
    }
}
