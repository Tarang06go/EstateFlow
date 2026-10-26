package com.estateflow.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Per-user display preferences stored in a small properties file (default ~/.estateflow/preferences.properties).
 * Nothing sensitive is stored. Unknown or invalid stored values fall back to the defaults, so a damaged
 * file can never stop the application from starting.
 */
public class UserPreferences {

    public static final List<String> CURRENCY_SYMBOLS = List.of("\u20B9", "Rs.", "$", "");
    public static final List<String> DATE_PATTERNS = List.of("dd MMM yyyy", "dd/MM/yyyy", "yyyy-MM-dd");
    public static final List<Integer> DASHBOARD_MONTHS = List.of(3, 6, 12);

    private static final String DEFAULT_CURRENCY = "\u20B9";
    private static final String DEFAULT_DATE = "dd MMM yyyy";
    private static final int DEFAULT_MONTHS = 6;

    private final Path file;
    private volatile String currencySymbol = DEFAULT_CURRENCY;
    private volatile String datePattern = DEFAULT_DATE;
    private volatile int dashboardMonths = DEFAULT_MONTHS;
    private volatile boolean rentReminders = true;
    private volatile boolean leaseReminders = true;
    private volatile boolean workOrderReminders = true;

    public UserPreferences(Path file) {
        this.file = file;
    }

    public static Path defaultFile() {
        return Path.of(System.getProperty("user.home"), ".estateflow", "preferences.properties");
    }

    /** In-memory defaults only (tests, or when no file is wanted). */
    public static UserPreferences defaults() {
        return new UserPreferences(null);
    }

    public static UserPreferences load(Path file) {
        UserPreferences prefs = new UserPreferences(file);
        if (file != null && Files.isRegularFile(file)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
                prefs.currencySymbol = pick(p.getProperty("currency.symbol"), CURRENCY_SYMBOLS, DEFAULT_CURRENCY);
                prefs.datePattern = pick(p.getProperty("date.pattern"), DATE_PATTERNS, DEFAULT_DATE);
                prefs.dashboardMonths = parseMonths(p.getProperty("dashboard.months"));
                prefs.rentReminders = parseBoolean(p.getProperty("reminders.rent"), true);
                prefs.leaseReminders = parseBoolean(p.getProperty("reminders.leases"), true);
                prefs.workOrderReminders = parseBoolean(p.getProperty("reminders.work-orders"), true);
            } catch (IOException | IllegalArgumentException e) {
                // unreadable or malformed preferences: keep the defaults
            }
        }
        return prefs;
    }

    private static <T> T pick(String raw, List<T> allowed, T fallback) {
        if (raw == null) {
            return fallback;
        }
        for (T candidate : allowed) {
            if (String.valueOf(candidate).equals(raw)) {
                return candidate;
            }
        }
        return fallback;
    }

    private static int parseMonths(String raw) {
        try {
            int v = Integer.parseInt(raw.trim());
            return DASHBOARD_MONTHS.contains(v) ? v : DEFAULT_MONTHS;
        } catch (RuntimeException e) {
            return DEFAULT_MONTHS;
        }
    }

    private static boolean parseBoolean(String raw,boolean fallback){return raw==null?fallback:"true".equalsIgnoreCase(raw.trim())?true:"false".equalsIgnoreCase(raw.trim())?false:fallback;}

    public String currencySymbol() {
        return currencySymbol;
    }

    public String datePattern() {
        return datePattern;
    }

    public int dashboardMonths() {
        return dashboardMonths;
    }

    public boolean rentReminders() { return rentReminders; }
    public boolean leaseReminders() { return leaseReminders; }
    public boolean workOrderReminders() { return workOrderReminders; }

    public void update(String currency, String datePattern, int months) {
        if (!CURRENCY_SYMBOLS.contains(currency) || !DATE_PATTERNS.contains(datePattern) || !DASHBOARD_MONTHS.contains(months)) {
            throw new IllegalArgumentException("Unsupported preference value");
        }
        this.currencySymbol = currency;
        this.datePattern = datePattern;
        this.dashboardMonths = months;
    }

    public void updateReminderPreferences(boolean rent,boolean leases,boolean workOrders){rentReminders=rent;leaseReminders=leases;workOrderReminders=workOrders;}

    /** Writes the preferences file; a no-op for in-memory preferences. */
    public void save() throws IOException {
        if (file == null) {
            return;
        }
        Properties p = new Properties();
        p.setProperty("currency.symbol", currencySymbol);
        p.setProperty("date.pattern", datePattern);
        p.setProperty("dashboard.months", String.valueOf(dashboardMonths));
        p.setProperty("reminders.rent",String.valueOf(rentReminders));
        p.setProperty("reminders.leases",String.valueOf(leaseReminders));
        p.setProperty("reminders.work-orders",String.valueOf(workOrderReminders));
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "EstateFlow display preferences");
        }
    }
}
