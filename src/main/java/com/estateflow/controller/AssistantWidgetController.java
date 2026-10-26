package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.exception.AuthorizationException;
import com.estateflow.exception.ValidationException;
import com.estateflow.security.Permission;
import com.estateflow.util.SceneNavigator;
import com.estateflow.util.UiTask;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.QuadCurve;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Floating chat UI for the existing local PropertyAssistantService. */
public final class AssistantWidgetController extends AbstractController {
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter.ofPattern("dd MMM, HH:mm");
    private static final List<QuickAction> QUICK_ACTIONS = List.of(
            new QuickAction("Rent & collection", "Summarize current rent arrears and collection rate"),
            new QuickAction("Lease expirations", "Which leases are ending soon?"),
            new QuickAction("Maintenance", "Summarize maintenance and overdue work orders"),
            new QuickAction("List tenants", "List my tenants"),
            new QuickAction("Vacancies", "Show all vacant properties"),
            new QuickAction("Property performance", "Summarize property occupancy and vacancies"),
            new QuickAction("Financial summary", "Summarize rental income, expenses and NOI")
    );

    @FXML private AnchorPane dock;
    @FXML private StackPane launcherWrap;
    @FXML private VBox chatPanel, history;
    @FXML private ScrollPane historyScroll;
    @FXML private FlowPane quickActions;
    @FXML private TextField questionField;
    @FXML private Button launcherButton, closeButton, sendButton, fullPageButton;
    @FXML private Circle notificationDot;
    @FXML private Label statusLabel;

    private boolean open;
    private boolean loading;
    private TranslateTransition floatAnimation;

    public AssistantWidgetController(AppContext ctx, SceneNavigator nav) { super(ctx, nav); }

    @FXML private void initialize() {
        launcherButton.setGraphic(buildMascot());
        launcherButton.setTooltip(new javafx.scene.control.Tooltip("Ask EstateFlow about your property records"));
        boolean canOpenReports = ctx.authorizer().can(Permission.VIEW_REPORTS);
        fullPageButton.setVisible(canOpenReports);
        fullPageButton.setManaged(canOpenReports);
        createQuickActions();
        addAssistantMessage("Hello. I can summarize current EstateFlow data for rent collection, lease expirations, maintenance, occupancy, and rental financials.", null);
        dock.widthProperty().addListener((o, oldWidth, width) -> {
            double available = Math.max(0, width.doubleValue() - 48);
            chatPanel.setMinWidth(Math.min(260, available));
            chatPanel.setMaxWidth(Math.min(380, available));
            chatPanel.setPrefWidth(Math.min(380, available));
        });
        dock.heightProperty().addListener((o, oldHeight, height) -> {
            double available = Math.max(0, height.doubleValue() - 104);
            chatPanel.setMinHeight(Math.min(300, available));
            chatPanel.setMaxHeight(Math.min(520, available));
        });
        floatAnimation = new TranslateTransition(Duration.seconds(2.8), launcherWrap);
        floatAnimation.setFromY(0);
        floatAnimation.setToY(-3);
        floatAnimation.setAutoReverse(true);
        floatAnimation.setCycleCount(TranslateTransition.INDEFINITE);
        floatAnimation.play();
        launcherButton.setOnMouseEntered(e -> animateLauncher(1.045));
        launcherButton.setOnMouseExited(e -> animateLauncher(1));
    }

    private void createQuickActions() {
        for (QuickAction action : QUICK_ACTIONS) {
            Button button = new Button(action.label());
            button.getStyleClass().add("assistant-chip");
            button.setAccessibleText("Ask: " + action.question());
            button.setOnAction(e -> ask(action.question()));
            quickActions.getChildren().add(button);
        }
    }

    @FXML private void onToggle() {
        if (open) closePanel();
        else openPanel();
    }

    private void openPanel() {
        open = true;
        notificationDot.setVisible(false);
        notificationDot.setManaged(false);
        chatPanel.setManaged(true);
        chatPanel.setVisible(true);
        chatPanel.setOpacity(0);
        chatPanel.setScaleX(.97);
        chatPanel.setScaleY(.97);
        FadeTransition fade = new FadeTransition(Duration.millis(170), chatPanel);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(170), chatPanel);
        scale.setToX(1);
        scale.setToY(1);
        ParallelTransition show = new ParallelTransition(fade, scale);
        show.play();
        launcherButton.setAccessibleText("Minimize EstateFlow property assistant");
        closeButton.setAccessibleText("Minimize assistant chat");
        if (!loading) questionField.requestFocus();
    }

    private void closePanel() {
        open = false;
        launcherButton.setAccessibleText("Open EstateFlow property assistant");
        FadeTransition hide = new FadeTransition(Duration.millis(130), chatPanel);
        hide.setToValue(0);
        hide.setOnFinished(e -> {
            if (!open) {
                chatPanel.setVisible(false);
                chatPanel.setManaged(false);
            }
        });
        hide.play();
    }

    @FXML private void onSend() { ask(questionField.getText()); }

    private void ask(String question) {
        if (loading) return;
        if (question == null || question.isBlank()) {
            statusLabel.setText("Type a question or choose a quick prompt.");
            questionField.requestFocus();
            return;
        }
        String submitted = question.trim();
        addUserMessage(submitted);
        questionField.clear();
        setLoading(true);
        UiTask.run(() -> ctx.assistant().answer(submitted), reply -> {
            setLoading(false);
            statusLabel.setText("Current data · generated " + reply.generatedAt().format(GENERATED_AT));
            addAssistantMessage(reply.answer(), reply.source() + " · " + reply.generatedAt().format(GENERATED_AT));
            openRequestedScreen(reply);
            if (!open) {
                notificationDot.setManaged(true);
                notificationDot.setVisible(true);
                launcherButton.setAccessibleText("EstateFlow assistant. A reply is ready. Open chat.");
            }
        }, failure -> {
            setLoading(false);
            if (failure instanceof AuthorizationException) {
                addAssistantMessage("Your current role does not allow access to these records. Contact an administrator if you need this view.", null);
                statusLabel.setText("Access is controlled by your EstateFlow role.");
            } else if (failure instanceof ValidationException validation) {
                addAssistantMessage(validation.getMessage(), null);
                statusLabel.setText("Please revise your question and try again.");
            } else {
                addAssistantMessage("I couldn't retrieve the current records just now. Please try again or open the relevant EstateFlow report. No records were changed.", null);
                statusLabel.setText("The local assistant is temporarily unavailable.");
            }
        }, questionField);
    }

    private void setLoading(boolean value) {
        loading = value;
        sendButton.setDisable(value);
        quickActions.getChildren().forEach(node -> node.setDisable(value));
        questionField.setDisable(value);
        statusLabel.setText(value ? "Checking your current EstateFlow data…" : "");
    }

    private void addUserMessage(String text) {
        Label label = messageLabel(text, "assistant-message-user");
        HBox row = new HBox(label);
        row.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        history.getChildren().add(row);
        scrollToLatest();
    }

    private void addAssistantMessage(String text, String source) {
        Label label = messageLabel(text, "assistant-message-answer");
        VBox message = new VBox(5, label);
        message.getStyleClass().add("assistant-message-wrap");
        if (source != null && !source.isBlank()) {
            Label sourceLabel = new Label("Source: " + source);
            sourceLabel.setWrapText(true);
            sourceLabel.getStyleClass().add("assistant-message-source");
            message.getChildren().add(sourceLabel);
        }
        HBox row = new HBox(message);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        history.getChildren().add(row);
        scrollToLatest();
    }

    private void openRequestedScreen(com.estateflow.dto.AssistantReply reply) {
        if (reply.navigationTarget() == null) return;
        try {
            nav.show(reply.navigationTarget());
            nav.toast(reply.answer());
        } catch (AuthorizationException denied) {
            addAssistantMessage("Your current role cannot open that screen. Ask an administrator about the required permission.", null);
            statusLabel.setText("Screen access is controlled by your EstateFlow role.");
        }
    }

    private Label messageLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(300);
        label.maxWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(180, chatPanel.getWidth() - 72), chatPanel.widthProperty()));
        label.getStyleClass().addAll("assistant-message", styleClass);
        return label;
    }

    private void scrollToLatest() {
        javafx.application.Platform.runLater(() -> historyScroll.setVvalue(1));
    }

    @FXML private void onOpenFullAssistant() { nav.show("assistant.fxml"); }

    private void animateLauncher(double scale) {
        ScaleTransition hover = new ScaleTransition(Duration.millis(150), launcherWrap);
        hover.setToX(scale);
        hover.setToY(scale);
        hover.play();
    }

    private Node buildMascot() {
        Group art = new Group();
        Color forest = Color.web("#173F32");
        Color green = Color.web("#245B45");
        Color cream = Color.web("#FFF9EA");
        Line antenna = new Line(0, -20, 0, -14);
        antenna.setStroke(cream); antenna.setStrokeWidth(2.2);
        Circle tip = new Circle(0, -22, 3.1, Color.web("#B8D7BE"));
        Rectangle head = new Rectangle(-14, -14, 28, 23);
        head.setArcWidth(10); head.setArcHeight(10); head.setFill(cream); head.setStroke(Color.web("#D6E5D8")); head.setStrokeWidth(1.2);
        Circle leftEye = new Circle(-6, -4, 2, forest);
        Circle rightEye = new Circle(6, -4, 2, forest);
        QuadCurve smile = new QuadCurve(-4, 3, 0, 6, 4, 3);
        smile.setFill(Color.TRANSPARENT); smile.setStroke(green); smile.setStrokeWidth(1.5);
        Line leftEar = new Line(-17, -7, -14, -7); leftEar.setStroke(cream); leftEar.setStrokeWidth(2.2);
        Line rightEar = new Line(14, -7, 17, -7); rightEar.setStroke(cream); rightEar.setStrokeWidth(2.2);
        Circle chest = new Circle(0, 14, 3.5, Color.web("#B8D7BE"));
        Line neck = new Line(0, 9, 0, 10.5); neck.setStroke(cream); neck.setStrokeWidth(2);
        art.getChildren().addAll(antenna, tip, leftEar, rightEar, head, leftEye, rightEye, smile, neck, chest);
        return art;
    }

    private record QuickAction(String label, String question) { }
}
