package ru.voidrp.wealthtax;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
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
import java.util.concurrent.TimeUnit;

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
            if (isExempt(op)) continue;
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

        if (config.isNationTreasuryEnabled() && !config.getBackendUrl().isBlank()) {
            applyNationTreasuryTax();
        }

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

    private void applyNationTreasuryTax() {
        try {
            var body = String.format("{\"rate\":%.4f}", config.getNationTreasuryRate());
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(config.getBackendUrl() + "/api/v1/nation-stats/nations/treasury-tax"))
                    .header("Content-Type", "application/json")
                    .header("X-Game-Auth-Secret", config.getGameAuthSecret())
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            var response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            plugin.getLogger().info("[Налог] Казна государств: статус " + response.statusCode()
                    + ", ставка=" + (config.getNationTreasuryRate() * 100) + "%");
        } catch (Exception e) {
            plugin.getLogger().warning("[Налог] Ошибка списания налога с казны: " + e.getMessage());
        }
    }

    private boolean isExempt(OfflinePlayer op) {
        if (op.isOnline() && op.getPlayer() != null)
            return op.getPlayer().hasPermission("voidrp.tax.exempt");
        try {
            LuckPerms lp = LuckPermsProvider.get();
            var user = lp.getUserManager().loadUser(op.getUniqueId()).get(3, TimeUnit.SECONDS);
            if (user == null) return false;
            return user.getCachedData().getPermissionData()
                    .checkPermission("voidrp.tax.exempt").asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    public long getLastRunMillis() { return lastRunMillis; }
    public long getNextRunMillis() { return lastRunMillis + config.getIntervalMs(); }

    public record TaxResult(String playerName, double balanceBefore, double tax) {}
}
