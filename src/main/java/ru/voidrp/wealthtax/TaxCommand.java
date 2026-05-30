package ru.voidrp.wealthtax;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class TaxCommand implements CommandExecutor, TabCompleter {

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.##");
    private static final DateTimeFormatter DT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final WealthTaxPlugin plugin;
    private final Economy economy;
    private final TaxConfig config;
    private final TaxScheduler scheduler;

    public TaxCommand(WealthTaxPlugin plugin, Economy economy, TaxConfig config, TaxScheduler scheduler) {
        this.plugin    = plugin;
        this.economy   = economy;
        this.config    = config;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("voidrp.wealthtax.admin")) {
            sender.sendMessage("§cНет доступа.");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (sub) {
            case "info" -> {
                long last = scheduler.getLastRunMillis();
                long next = scheduler.getNextRunMillis();
                sender.sendMessage("§6[WealthTax] §eПоследний сбор: §f" +
                        (last == 0 ? "ещё не проводился" : DT.format(Instant.ofEpochMilli(last))));
                sender.sendMessage("§6[WealthTax] §eСледующий сбор: §f" +
                        DT.format(Instant.ofEpochMilli(next)));
                sender.sendMessage("§6[WealthTax] §eИнтервал: §f" + config.getIntervalHours() + "ч");
            }
            case "run" -> {
                sender.sendMessage("§6[WealthTax] Запускаю принудительный сбор...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    var results = scheduler.runTax();
                    long taxed = results.stream().filter(r -> r.tax() > 0.01).count();
                    double total = results.stream().mapToDouble(TaxScheduler.TaxResult::tax).sum();
                    sender.sendMessage("§6[WealthTax] §aГотово. §eИгроков: §f" + taxed +
                            "§e, изъято: §f" + FMT.format(total) + "₽");
                });
            }
            case "preview" -> {
                if (args.length < 2) { sender.sendMessage("§cУкажи имя игрока."); return true; }
                OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (op == null || !economy.hasAccount(op)) {
                    sender.sendMessage("§cИгрок не найден или нет счёта.");
                    return true;
                }
                double balance = economy.getBalance(op);
                double tax = config.calculateTax(balance);
                sender.sendMessage("§6[WealthTax] §e" + args[1] +
                        ": баланс §f" + FMT.format(balance) +
                        "₽§e, налог §c" + FMT.format(tax) + "₽" +
                        "§e, остаток §a" + FMT.format(balance - tax) + "₽");
            }
            case "tiers" -> {
                sender.sendMessage("§6=== Налоговые ставки ===");
                var tiers = config.getTiers();
                for (int i = 0; i < tiers.size(); i++) {
                    var t = tiers.get(i);
                    String upper = (i + 1 < tiers.size())
                            ? FMT.format(tiers.get(i + 1).threshold()) + "₽"
                            : "∞";
                    sender.sendMessage(String.format("§e%s₽ §7— %s §f→ §c%.0f%%",
                            FMT.format(t.threshold()), upper, t.rate() * 100));
                }
            }
            default -> {
                sender.sendMessage("§6=== WealthTax ===");
                sender.sendMessage("§e/wealthtax info §7— статус и расписание");
                sender.sendMessage("§e/wealthtax run §7— принудительный сбор");
                sender.sendMessage("§e/wealthtax preview <игрок> §7— предварительный расчёт");
                sender.sendMessage("§e/wealthtax tiers §7— налоговые ставки");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return Arrays.asList("info", "run", "preview", "tiers");
        if (args.length == 2 && args[0].equalsIgnoreCase("preview")) {
            return Bukkit.getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName).toList();
        }
        return List.of();
    }
}
