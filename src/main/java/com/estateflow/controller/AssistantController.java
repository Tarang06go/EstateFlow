package com.estateflow.controller;

import com.estateflow.config.AppContext;
import com.estateflow.dto.AssistantReply;
import com.estateflow.util.SceneNavigator;
import com.estateflow.util.UiTask;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

/** A local assistant panel that summarizes data through existing authorized services. */
public final class AssistantController extends AbstractController {
    @FXML private TextArea questionArea, answerArea;
    @FXML private Label sourceLabel, timestampLabel;
    @FXML private Button askButton;

    public AssistantController(AppContext ctx, SceneNavigator nav) { super(ctx, nav); }

    @FXML private void initialize() {
        answerArea.setEditable(false);
        answerArea.setWrapText(true);
    }

    @FXML private void onAsk() {
        String question = questionArea.getText();
        UiTask.run(() -> ctx.assistant().answer(question), this::showReply, askButton);
    }

    @FXML private void onRentPrompt() { questionArea.setText("Summarize current rent arrears and collection rate"); onAsk(); }
    @FXML private void onLeasePrompt() { questionArea.setText("Which leases are ending soon?"); onAsk(); }
    @FXML private void onMaintenancePrompt() { questionArea.setText("Summarize maintenance and overdue work orders"); onAsk(); }
    @FXML private void onTrendPrompt() { questionArea.setText("Explain the recent cash flow trend and forecast"); onAsk(); }
    @FXML private void onPortfolioPrompt() { questionArea.setText("Summarize my portfolio"); onAsk(); }
    @FXML private void onTenantPrompt() { questionArea.setText("List my tenants"); onAsk(); }
    @FXML private void onVacancyPrompt() { questionArea.setText("Show all vacant properties"); onAsk(); }

    private void showReply(AssistantReply reply) {
        String prior = answerArea.getText();
        String exchange = "You: " + reply.question() + "\n\nEstateFlow: " + reply.answer() + "\n\n";
        answerArea.setText((prior + exchange).substring(Math.max(0, prior.length() + exchange.length() - 50_000)));
        sourceLabel.setText("Source: " + reply.source());
        timestampLabel.setText("Generated " + reply.generatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")));
        if (reply.navigationTarget() != null) {
            try { nav.show(reply.navigationTarget()); }
            catch (com.estateflow.exception.AuthorizationException denied) {
                answerArea.setText("Your current role cannot open that screen. Ask an administrator about the required permission.");
            }
        }
    }
}
