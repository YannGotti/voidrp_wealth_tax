package ru.voidrp.wealthtax;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class TaxScheduler {

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.##");

    private final WealthTaxPlugin plugin;
    private final Economy economy;
    private final TaxConfig config;
    private final File dataFile;
    private BukkitTask task;
    private long lastRunMillis;

    public TaxScheduler(WealthTaxPlugin plugin, Economy economy, TaxConfig config) {
        this.plugin   = plugin;
        this.economy  = economy;
        this.config   = config;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.lastRunMillis = loadLastRun();
    }

    public void start() {
        // Check every 5 minutes (6000 ticks) on async thread
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAndRun, 200L, 6000L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void checkAndRun() {
        if (System.currentTimeMillis() - lastRunMillis >= config.getIntervalMs()) {
            runTax();
        }
    }

    public List<TaxResult> runTax() {
        List<TaxResult> results = new ArrayList<>();

        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (!economy.hasAccount(op)) continue;
            double balance = economy.getBalance(op);
            double tax = config.calculateTax(balance);
            if (tax < 0.01) continue;

            economy.withdrawPlayer(op, tax);
            results.add(new TaxResult(op.getName(), balance, tax));

            if (config.isLogToConsole()) {
                plugin.getLogger().info(String.format(
                        "[Налог] %s: %.2f₽ → налог %.2f₽ → остаток %.2f₽",
                        op.getName(), balance, tax, balance - tax));
            }

            if (config.isNotifyOnline()) {
                Player online = op.getPlayer();
                if (online != null) {
                    online.sendMessage(
                            "§6[Казна] §eУдержан прогрессивный налог: §c-" + FMT.format(tax) + "₽§e. " +
                            "Баланс: §a" + FMT.format(balance - tax) + "₽"
                    );
                }
            }
        }

        lastRunMillis = System.currentTimeMillis();
        saveLastRun();

        long taxed = results.stream().filter(r -> r.tax() > 0.01).count();
        double total = results.stream().mapToDouble(TaxResult::tax).sum();
        plugin.getLogger().info(String.format(
                "[Налог] Сбор завершён. Игроков: %d, изъято: %s₽", taxed, FMT.format(total)));

        return results;
    }

    private long loadLastRun() {
        if (!dataFile.exists()) return 0L;
        return YamlConfiguration.loadConfiguration(dataFile).getLong("last-run", 0L);
    }

    private void saveLastRun() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("last-run", lastRunMillis);
        try { yml.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Не удалось сохранить data.yml: " + e.getMessage()); }
    }

    public long getLastRunMillis() { return lastRunMillis; }
    public long getNextRunMillis() { return lastRunMillis + config.getIntervalMs(); }

    public record TaxResult(String playerName, double balanceBefore, double tax) {}
}
