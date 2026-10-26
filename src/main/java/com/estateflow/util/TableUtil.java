package com.estateflow.util;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Builds read-only text columns so controllers stay short. Columns whose title names an amount or a count
 * are right-aligned, and a column titled "Status" renders as a coloured badge.
 */
public final class TableUtil {

    private static final List<String> NUMERIC_TITLES = List.of("rent", "amount", "paid", "balance", "cost", "total", "days",
            "due", "deposit", "requests");

    private TableUtil() {
    }

    public static <T> TableColumn<T, String> column(TableView<T> table, String title, double width,
                                                    Function<T, Object> value) {
        if (table.getColumnResizePolicy() != TableView.CONSTRAINED_RESIZE_POLICY) {
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        }
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setPrefWidth(width);
        col.setCellValueFactory(cell -> {
            Object v = value.apply(cell.getValue());
            return new ReadOnlyStringWrapper(v == null ? "" : String.valueOf(v));
        });
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.equals("status")) {
            col.setCellFactory(c -> new BadgeCell<>());
        } else if (isNumericTitle(lower)) {
            col.setCellFactory(c -> new NumericCell<>());
        }
        table.getColumns().add(col);
        return col;
    }

    static boolean isNumericTitle(String lowerTitle) {
        if (lowerTitle.contains("date") || lowerTitle.contains("due date")) {
            return false;
        }
        return NUMERIC_TITLES.stream().anyMatch(lowerTitle::contains);
    }

    /** Maps a status word to a badge style class. Unknown words are neutral. */
    public static String badgeClass(String status) {
        String s = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "ACTIVE", "PAID", "VALID", "RESOLVED", "OCCUPIED", "DONE" -> "badge-success";
            case "OVERDUE", "VOID", "TERMINATED", "URGENT", "INACTIVE", "CANCELLED", "EXPIRED", "DUE TODAY" -> "badge-danger";
            case "PENDING", "PARTIALLY_PAID", "EXPIRING_SOON", "OPEN", "HIGH", "VACANT" -> "badge-warning";
            case "UPCOMING", "IN_PROGRESS", "MEDIUM" -> "badge-info";
            default -> "badge-neutral";
        };
    }

    private static final class NumericCell<T> extends TableCell<T, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty ? null : item);
            getStyleClass().remove("numeric");
            if (!empty) {
                getStyleClass().add("numeric");
            }
        }
    }

    private static final class BadgeCell<T> extends TableCell<T, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null || item.isEmpty()) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label badge = new Label(item.replace('_', ' '));
            badge.getStyleClass().addAll("badge", badgeClass(item));
            setGraphic(badge);
            setText(null);
        }
    }
}
