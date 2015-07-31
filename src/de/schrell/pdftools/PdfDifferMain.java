package de.schrell.pdftools;

import org.apache.log4j.Logger;

import de.schrell.fx.FxHelper;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class PdfDifferMain extends Application {

    private final static Logger LOGGER = Logger.getLogger(PdfDifferMain.class);

    private PdfDiffer pdfDiffer = null;

    public PdfDifferMain() {
    }

    @Override
    public void init() throws Exception {
        super.init();
        final String firstFile = this.getParameters().getRaw().get(0);
        final String secondFile = this.getParameters().getRaw().get(1);
        this.pdfDiffer = new PdfDiffer(firstFile, secondFile);
    }

    @Override
    public void start(final Stage primaryStage) throws Exception {

        if (this.getParameters().getRaw().size() != 2) {
            LOGGER.error("Aufruf: java -jar PdfDiffer.jar alt.pdf neu.pdf");
            FxHelper.createMessageDialog(AlertType.ERROR, "Programm-Parameter vergessn?",
                "Es müssen zwei PDF-Dateien zum Vergleich angegeben werden.").showAndWait();
            System.exit(1);
        }

        final GridPane gridPane = new GridPane();
        final Scene scene = new Scene(gridPane);

        scene.getStylesheets().add(this.getClass().getResource("application.css").toExternalForm());

        this.pdfDiffer.setup(gridPane);

        primaryStage.setTitle("PDF-Differ FX");
        primaryStage.setScene(scene);
        primaryStage.show();

    }

    public static void main(final String[] args) {
        launch(args);
    }


}
