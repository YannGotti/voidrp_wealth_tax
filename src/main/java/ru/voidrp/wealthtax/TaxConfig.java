package ru.voidrp.wealthtax;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TaxConfig {

    public record Tier(double threshold, double rate) {}

    private final List<Tier> tiers = new ArrayList<>();
    private final long intervalHours;
    private final boolean notifyOnline;
    private final boolean logToConsole;
    private final boolean nationTreasuryEnabled;
    private final double nationTreasuryRate;
    private final String backendUrl;
    private final String gameAuthSecret;

    public TaxConfig(FileConfiguration cfg) {
        List<?> raw = cfg.getList("tiers");
        if (raw != null) {
            for (Object o : raw) {
                if (o instanceof java.util.Map<?, ?> m) {
                    double t = ((Number) m.get("threshold")).doubleValue();
                    double r = ((Number) m.get("rate")).doubleValue();
                    tiers.add(new Tier(t, r));
                }
            }
            tiers.sort(Comparator.comparingDouble(Tier::threshold));
        }
        // Fallback defaults
        if (tiers.isEmpty()) {
            tiers.add(new Tier(0,         0.00));
            tiers.add(new Tier(100_000,   0.02));
            tiers.add(new Tier(500_000,   0.05));
            tiers.add(new Tier(2_000_000, 0.10));
        }

        intervalHours         = cfg.getLong("interval-hours", 168);
        notifyOnline          = cfg.getBoolean("notify-online", true);
        logToConsole          = cfg.getBoolean("log-to-console", true);
        nationTreasuryEnabled = cfg.getBoolean("nation-treasury.enabled", false);
        nationTreasuryRate    = cfg.getDouble("nation-treasury.rate", 0.05);
        backendUrl            = cfg.getString("backend.url", "");
        gameAuthSecret        = cfg.getString("backend.game-auth-secret", "");
    }

    /**
     * Progressive bracket tax (like income tax).
     * Each tier's rate applies only to the balance within that bracket.
     */
    public double calculateTax(double balance) {
        double tax = 0;
        for (int i = 0; i < tiers.size(); i++) {
            double tierStart = tiers.get(i).threshold();
            if (balance <= tierStart) break;
            double tierEnd = (i + 1 < tiers.size()) ? tiers.get(i + 1).threshold() : Double.MAX_VALUE;
            double taxable = Math.min(balance, tierEnd) - tierStart;
            tax += taxable * tiers.get(i).rate();
        }
        return tax;
    }

    public List<Tier> getTiers()              { return tiers; }
    public long getIntervalHours()            { return intervalHours; }
    public long getIntervalMs()               { return intervalHours * 3_600_000L; }
    public boolean isNotifyOnline()           { return notifyOnline; }
    public boolean isLogToConsole()           { return logToConsole; }
    public boolean isNationTreasuryEnabled()  { return nationTreasuryEnabled; }
    public double getNationTreasuryRate()     { return nationTreasuryRate; }
    public String getBackendUrl()             { return backendUrl; }
    public String getGameAuthSecret()         { return gameAuthSecret; }
}
