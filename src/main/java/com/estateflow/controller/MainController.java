package com.estateflow.controller;

import com.estateflow.security.Permission;
import com.estateflow.config.AppContext;
import com.estateflow.util.Icons;
import com.estateflow.util.LogoMark;
import com.estateflow.util.SceneNavigator;
import com.estateflow.util.UiTask;
import com.estateflow.util.AccessUi;
import javafx.fxml.FXMLLoader;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Application shell: branded sidebar with grouped navigation, global search, busy indicator and toasts. */
public class MainController extends AbstractController {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private record Item(String key, String label, String icon, String fxml, Permission required) {
        Item(String key, String label, String icon, String fxml) {
            this(key, label, icon, fxml, null);
        }
    }

    private record Group(String title, List<Item> items) {
    }

    private static final List<Group> MENU = List.of(
            new Group("OVERVIEW", List.of(new Item("dashboard", "Dashboard", "dashboard", "dashboard.fxml"))),
            new Group("PORTFOLIO", List.of(
                    new Item("properties", "Properties", "properties", "properties.fxml"),
                    new Item("owners", "Owners", "owners", "owners.fxml"),
                    new Item("tenants", "Tenants", "tenants", "tenants.fxml"),
                    new Item("agreements", "Agreements", "agreements", "agreements.fxml"))),
            new Group("FINANCIALS", List.of(
                    new Item("ledger", "Rent Ledger", "ledger", "ledger.fxml"),
                    new Item("payments", "Payments", "payments", "payments.fxml"),
                    new Item("expenses", "Expenses", "ledger", "expenses.fxml"),
                    new Item("deposits", "Deposits", "ledger", "deposits.fxml"),
                    new Item("overdue", "Overdue Rent", "overdue", "overdue.fxml"))),
            new Group("OPERATIONS", List.of(
                    new Item("documents", "Documents", "reports", "documents.fxml"),
                    new Item("inspections", "Inspections", "maintenance", "inspections.fxml"),
                    new Item("work-orders", "Work Orders", "maintenance", "work-orders.fxml"),
                    new Item("vendors", "Vendors", "owners", "vendors.fxml"),
                    new Item("maintenance", "Maintenance", "maintenance", "maintenance.fxml"),
                    new Item("notifications", "Notifications", "reminders", "notifications.fxml"),
                    new Item("expiring", "Expiring Leases", "expiring", "expiring.fxml"),
                    new Item("audit", "Activity History", "activity", "audit.fxml", Permission.VIEW_AUDIT))),
            new Group("INSIGHTS", List.of(
                    new Item("assistant", "Property Assistant", "reports", "assistant.fxml", Permission.VIEW_REPORTS),
                    new Item("analytics", "Advanced Analytics", "reports", "analytics.fxml", Permission.VIEW_REPORTS),
                    new Item("profitability", "Profitability", "reports", "profitability.fxml", Permission.VIEW_REPORTS),
                    new Item("reports", "Reports", "reports", "reports.fxml", Permission.VIEW_REPORTS))),
            new Group("SYSTEM", List.of(
                    new Item("users", "Users", "owners", "users.fxml", Permission.MANAGE_USERS),
                    new Item("settings", "Settings", "settings", "settings.fxml"))));

    /** Screens that belong to a menu entry without being its own entry. */
    private static final Map<String, String> ALIASES = Map.of("maintenance-analytics.fxml", "maintenance",
            "search.fxml", "");

    @FXML private ScrollPane contentScroll;
    @FXML private StackPane brandHolder, assistantLayer;
    @FXML private VBox navBox, toastBox;
    @FXML private TextField globalSearch;
    @FXML private ProgressBar busyBar;
    @FXML private Label versionLabel, userLabel, roleLabel;
    @FXML private Button logoutButton;

    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private final Map<String, String> keyByFxml = new LinkedHashMap<>();

    public MainController(AppContext ctx, SceneNavigator nav) {
        super(ctx, nav);
    }

    @FXML
    private void initialize() {
        brandHolder.getChildren().add(LogoMark.lockup(34));
        versionLabel.setText("EstateFlow 1.2  |  Desktop edition");
        busyBar.visibleProperty().bind(UiTask.pendingProperty().greaterThan(0));
        ctx.session().currentUser().ifPresent(u -> {
            userLabel.setText(u.username());
            roleLabel.setText(u.role().label());
        });
        for (Group group : MENU) {
            List<Item> visible = group.items().stream()
                    .filter(i -> i.required() == null || ctx.authorizer().can(i.required())).toList();
            if (visible.isEmpty()) {
                continue;
            }
            Label title = new Label(group.title());
            title.getStyleClass().add("sidebar-section");
            navBox.getChildren().add(title);
            for (Item item : visible) {
                Button button = new Button(item.label(), Icons.icon(item.icon(), 18));
                button.getStyleClass().add("nav-item");
                button.setMaxWidth(Double.MAX_VALUE);
                button.setOnAction(e -> nav.show(item.fxml()));
                buttons.put(item.key(), button);
                keyByFxml.put(item.fxml(), item.key());
                navBox.getChildren().add(button);
            }
        }
        nav.setContentArea(contentScroll);
        nav.setToastBox(toastBox);
        nav.setScreenListener(this::markActive);
        installAssistantWidget();
        nav.show("dashboard.fxml");
    }

    private void installAssistantWidget() {
        if (!ctx.authorizer().can(Permission.VIEW_DATA)) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/assistant-widget.fxml"));
            loader.setControllerFactory(type -> {
                if (type == AssistantWidgetController.class) return new AssistantWidgetController(ctx, nav);
                throw new IllegalArgumentException("Unexpected assistant widget controller: " + type.getName());
            });
            Parent widget = loader.load();
            AccessUi.apply(widget, ctx.authorizer());
            assistantLayer.getChildren().setAll(widget);
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not load the optional floating property assistant; the rest of the application remains available.", failure);
        }
    }

    private void markActive(String fxml) {
        String key = keyByFxml.getOrDefault(fxml, ALIASES.getOrDefault(fxml, ""));
        buttons.forEach((k, b) -> {
            b.getStyleClass().remove("selected");
            if (k.equals(key)) {
                b.getStyleClass().add("selected");
            }
        });
    }

    @FXML
    private void onLogout() {
        ctx.assistant().clearConversation();
        nav.signOut();
    }

    @FXML
    private void onGlobalSearch() {
        SearchController results = nav.show("search.fxml");
        results.search(globalSearch.getText());
    }
}
