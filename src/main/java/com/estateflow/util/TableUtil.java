package com.estateflow.util;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.function.Function;

/** Builds read-only text columns so controllers stay short. */
public final class TableUtil {

    private TableUtil() {
    }

    public static <T> TableColumn<T, String> column(TableView<T> table, String title, double width,
                                                    Function<T, Object> value) {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setPrefWidth(width);
        col.setCellValueFactory(cell -> {
            Object v = value.apply(cell.getValue());
            return new ReadOnlyStringWrapper(v == null ? "" : String.valueOf(v));
        });
        table.getColumns().add(col);
        return col;
    }
}
