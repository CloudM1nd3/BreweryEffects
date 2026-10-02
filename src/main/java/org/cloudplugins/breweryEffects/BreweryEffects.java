package org.cloudplugins.breweryEffects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * Console-only command: /brewfx <effect> <player>
 * Plays sounds, spawns particles and teleports via the Bukkit API,
 * so vanilla command feedback is never produced (no chat, no log).
 */
public final class BreweryEffects extends JavaPlugin implements CommandExecutor, TabCompleter {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginCommand cmd = getCommand("brewfx");
        if (cmd == null) {
            getLogger().severe("Command brewfx is missing in plugin.yml");
            return;
        }
        cmd.setExecutor(this);
        cmd.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Only the console may use it (Brewery servercommands run as console).
        if (!(sender instanceof ConsoleCommandSender)) {
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            getLogger().info("Config reloaded.");
            return true;
        }
        if (args.length < 2) {
            return true;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            return true;
        }
        run(args[0], player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    private void run(String name, Player player) {
        ConfigurationSection effect = getConfig().getConfigurationSection("effects." + name);
        if (effect == null) {
            getLogger().warning("Unknown effect: " + name);
            return;
        }

        Location base = player.getLocation();
        World world = base.getWorld();

        for (Map<?, ?> m : effect.getMapList("sounds")) {
            world.playSound(base, str(m, "sound", ""), category(str(m, "category", "master")),
                    (float) num(m, "volume", 1), (float) num(m, "pitch", 1));
        }

        for (Map<?, ?> m : effect.getMapList("particles")) {
            String key = str(m, "particle", "").toLowerCase();
            Particle particle = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(key));
            if (particle == null) {
                getLogger().warning("Unknown particle: " + key);
                continue;
            }
            Location at = base.clone().add(num(m, "ox", 0), num(m, "oy", 0), num(m, "oz", 0));
            try {
                world.spawnParticle(particle, at, (int) num(m, "count", 1),
                        num(m, "dx", 0), num(m, "dy", 0), num(m, "dz", 0), num(m, "speed", 0));
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Particle " + key + " needs extra data and is not supported: " + ex.getMessage());
            }
        }

        ConfigurationSection tp = effect.getConfigurationSection("teleport");
        if (tp != null) {
            player.teleportAsync(base.clone().add(tp.getDouble("x"), tp.getDouble("y"), tp.getDouble("z")));
        }
    }

    private static double num(Map<?, ?> m, String key, double def) {
        Object o = m.get(key);
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private static String str(Map<?, ?> m, String key, String def) {
        Object o = m.get(key);
        return o == null ? def : o.toString();
    }

    private static SoundCategory category(String s) {
        try {
            return SoundCategory.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SoundCategory.MASTER;
        }
    }
}