package de.schrell.fx;

import de.schrell.helper.ExceptionHelper;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class FxHelper {


    public static Alert createMessageDialog(final AlertType type, final String header, final String text) {
        return FxHelper.createMessageDialog(type, header, text, null);
    }

    public static Alert createMessageDialog(final AlertType type, final String header, final String text, final Throwable e) {
        final Alert alert = new Alert(type, text, ButtonType.OK);
        alert.setHeaderText(header);
        if (e != null) {
            addExceptionToAlert(alert, e);
        }
        return alert;
    }

    private static void addExceptionToAlert(final Alert alert, final Throwable e) {

        final TextArea textArea = new TextArea(ExceptionHelper.getStackTrace(e));

        textArea.setEditable(false);
        textArea.setWrapText(true);

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        final GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        final Label label = new Label("Stacktrace der Execption:");
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);

        alert.getDialogPane().setExpandableContent(expContent);

        alert.setResizable(true);
        alert.getDialogPane().setMinWidth(800);

    }

}
