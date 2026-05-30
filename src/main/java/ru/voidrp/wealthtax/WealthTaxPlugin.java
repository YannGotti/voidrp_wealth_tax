package ru.voidrp.wealthtax;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class WealthTaxPlugin extends JavaPlugin {

    private Economy economy;
    private TaxConfig taxConfig;
    private TaxScheduler taxScheduler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        taxConfig = new TaxConfig(getConfig());

        if (!setupEconomy()) {
            getLogger().severe("Vault не найден — отключаем плагин.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        taxScheduler = new TaxScheduler(this, economy, taxConfig);
        taxScheduler.start();

        PluginCommand cmd = getCommand("wealthtax");
        if (cmd != null) {
            TaxCommand handler = new TaxCommand(this, economy, taxConfig, taxScheduler);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        getLogger().info("VoidRP WealthTax запущен. Интервал: " + taxConfig.getIntervalHours() + "ч.");
    }

    @Override
    public void onDisable() {
        if (taxScheduler != null) taxScheduler.stop();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }
}
