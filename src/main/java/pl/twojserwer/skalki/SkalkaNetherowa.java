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
    private final Map<UUID, UUID> crystalToGuard = new HashMap<>(); // Kryształ -> ID moba (uproszczone do 1 fali)
    private final Set<UUID> activeGuards = new HashSet<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        getCommand("skalka").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) return true;

        if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            spawnCrystal(player.getLocation());
            player.sendMessage("§a§l[!] §7Postawiono stałą skałkę netherową!");
        }
        return true;
    }

    private void spawnCrystal(Location loc) {
        EnderCrystal crystal = (EnderCrystal) loc.getWorld().spawnEntity(loc, EntityType.ENDER_CRYSTAL);
        crystal.setShowingBottom(true);
        crystal.setCustomName("§c§lSkałka Netherowa");
        crystal.setCustomNameVisible(true);
        crystalHP.put(crystal.getUniqueId(), 4);
    }

    @EventHandler
    public void onCrystalHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        event.setCancelled(true); // Blokada wybuchu

        if (!(event.getDamager() instanceof Player player)) return;

        UUID id = crystal.getUniqueId();
        if (!crystalHP.containsKey(id)) return;

        // Sprawdzanie czy żyją moby
        if (hasLivingGuards()) {
            player.sendMessage("§c§l[!] §7Pokonaj strażników, aby dalej niszczyć skałkę!");
            return;
        }

        int currentHP = crystalHP.get(id) - 1;
        crystalHP.put(id, currentHP);

        if (currentHP <= 0) {
            spawnGuards(crystal.getLocation());
            player.sendMessage("§6§l[!] §ePojawili się strażnicy! Pokonaj ich, by rozbić skałkę!");
            // Wizualny efekt "uśpienia" kryształu
            crystal.setBeamTarget(crystal.getLocation().clone().add(0, -1, 0)); 
        } else {
            player.sendMessage("§e§l[!] §7Skałka traci moc... §6(HP: " + currentHP + "/4)");
            crystal.getWorld().playEffect(crystal.getLocation(), Effect.STEP_SOUND, Material.NETHERRACK);
        }
    }

    private void spawnGuards(Location loc) {
        for (int i = 0; i < 3; i++) {
            EntityType type = (random.nextBoolean()) ? EntityType.WITHER_SKELETON : EntityType.BLAZE;
            Monster m = (Monster) loc.getWorld().spawnEntity(loc.clone().add(random.nextDouble()*2, 1, random.nextDouble()*2), type);
            m.setCustomName("§4Strażnik Skałki");
            activeGuards.add(m.getUniqueId());
        }
    }

    private boolean hasLivingGuards() {
        activeGuards.removeIf(uuid -> Bukkit.getEntity(uuid) == null || Bukkit.getEntity(uuid).isDead());
        return !activeGuards.isEmpty();
    }

    @EventHandler
    public void onGuardDeath(EntityDeathEvent event) {
        if (activeGuards.contains(event.getEntity().getUniqueId())) {
            activeGuards.remove(event.getEntity().getUniqueId());
            
            if (activeGuards.isEmpty()) {
                // Gdy ostatni mob zginie, szukamy najbliższego kryształu do "rozbicia"
                for (Entity e : event.getEntity().getNearbyEntities(10, 10, 10)) {
                    if (e instanceof EnderCrystal crystal && crystalHP.containsKey(e.getUniqueId())) {
                        giveRewardAndDestroy(crystal);
                        break;
                    }
                }
            }
        }
    }

    private void giveRewardAndDestroy(EnderCrystal crystal) {
        Location loc = crystal.getLocation();
        
        // --- DROP SYSTEM ---
        List<ItemStack> drops = new ArrayList<>();
        drops.add(new ItemStack(Material.GHAST_TEAR, random.nextInt(2) + 1));
        drops.add(new ItemStack(Material.DIAMOND, random.nextInt(3) + 1));
        drops.add(new ItemStack(Material.IRON_INGOT, random.nextInt(5) + 3));
        drops.add(new ItemStack(Material.GOLD_INGOT, random.nextInt(5) + 3));
        drops.add(new ItemStack(Material.BLAZE_ROD, random.nextInt(3) + 1));
        drops.add(new ItemStack(Material.NETHER_WART, random.nextInt(4) + 2));

        // Rzadkie dropy
        if (random.nextDouble() < 0.20) drops.add(new ItemStack(Material.GOLDEN_APPLE, 1));
        if (random.nextDouble() < 0.15) drops.add(new ItemStack(Material.WITHER_SKELETON_SKULL, 1));
        if (random.nextDouble() < 0.05) drops.add(new ItemStack(Material.NETHERITE_SCRAP, 1));

        for (ItemStack is : drops) {
            loc.getWorld().dropItemNaturally(loc, is);
        }

        loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1);
        loc.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_DEATH, 1.0f, 0.5f);

        crystal.remove();
        crystalHP.remove(crystal.getUniqueId());

        // Odrodzenie za 30 minut (1800 sekund * 20 ticków)
        Bukkit.getScheduler().runTaskLater(this, () -> spawnCrystal(loc), 36000L);
        Bukkit.broadcastMessage("§6§l[Skałka] §eZostała rozbita! Kolejna pojawi się za 30 minut.");
    }
}
