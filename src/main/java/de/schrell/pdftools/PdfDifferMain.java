package de.schrell.pdftools;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.Manifest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.schrell.fx.FxHelper;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

@SuppressWarnings("nls")
public class PdfDifferMain extends Application {

    private final static Logger LOGGER = LogManager.getLogger(PdfDifferMain.class);

    private PdfDiffer pdfDiffer = null;

    public PdfDifferMain() {
        Thread.currentThread().setName("PDFDiffer-Main");
    }

    @Override
    public void init() throws Exception {
        super.init();
    }

    @Override
    public void start(final Stage primaryStage) throws Exception {

        if (this.getParameters() == null || this.getParameters().getRaw().size() != 2) {
            LOGGER.error("Aufruf: java -jar PdfDiffer.jar alt.pdf neu.pdf");
            FxHelper.createMessageDialog(AlertType.ERROR, "Programm-Parameter vergessen?",
                "Es müssen zwei PDF-Dateien zum Vergleich angegeben werden.").showAndWait();
            System.exit(1);
        }

        final String version = getVersion();
        final String firstFile = this.getParameters().getRaw().get(0);
        final String secondFile = this.getParameters().getRaw().get(1);
        this.pdfDiffer = new PdfDiffer(firstFile, secondFile);

        final GridPane gridPane = new GridPane();
        final Scene scene = new Scene(gridPane);

        scene.getStylesheets().add(this.getClass().getResource("/application.css").toExternalForm());
        scene.getStylesheets().add(this.getClass().getResource("/styling.css").toExternalForm());

        this.pdfDiffer.setup(gridPane);

        primaryStage.setTitle("PDF-Differ FX v" + version);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private static String getVersion() {
        String version = null;
        try {
            @SuppressWarnings("resource")
            final URLClassLoader cl = (URLClassLoader) PdfDifferMain.class.getClassLoader();
                final URL url = cl.findResource("META-INF/MANIFEST.MF");
                try (InputStream stream = url.openStream()) {
                    final Manifest manifest = new Manifest(stream);
                    version = manifest.getMainAttributes().getValue("Implementation-Version");
                }
        } catch (final Exception e) {
            //nix
        }
        return (version == null) ? "0.0" : version;
    }

    public static void main(final String[] args) {
        launch(args);
    }

}
