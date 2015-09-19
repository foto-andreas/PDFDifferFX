package de.schrell.pdftools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.schrell.fx.FxHelper;
import de.schrell.fx.RadioButtonGroup;
import de.schrell.fx.ZoomableScrollPane;
import de.schrell.image.ImageDiffer;
import de.schrell.tools.TempDir;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.HPos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Adapted from some examples to implement a Diff for PDF files. It uses
 * ghostscript and imagemagick as external tools.
 *
 * @author joshua.marinacci@sun.com
 */
@SuppressWarnings("nls")
public class PdfDiffer {

    /**
     * Logger for this class
     */
    private final static Logger LOGGER = LogManager.getLogger(PdfDiffer.class);

    /**
     * How to display the files
     */
    enum DisplayType {

        DIFF("DIFF"),
        OLD("ALT"),
        NEW("NEU");

        String text;

        DisplayType(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return this.text;
        }
    }

    /**
     * Bild-Ansicht.
     */
    private final ImageView image = new ImageView();

    /**
     * Temporary directory to store image files
     */
    private volatile File tmpdir = null;

    /**
     * The actual page number
     */
    private volatile int pageNo;

    /**
     * infoline at the top
     */
    private final Label info = new Label("INFOZEILE");

    /**
     * Fortschrittsbalken.
     */
    private volatile ProgressIndicator progress;

    private volatile RadioButtonGroup<DisplayType> radioButtonGroup;

    private static final double INIT_ZOOM = 0.5;
    private volatile static DoubleProperty factorX = new SimpleDoubleProperty(INIT_ZOOM);
    private volatile static DoubleProperty factorY = new SimpleDoubleProperty(INIT_ZOOM);

    private volatile ScrollPane scrollPane;

    private final PdfImager imagerForOldPdf;
    private final PdfImager imagerForNewPdf;

    /**
     * Konstruktor.
     */
    public PdfDiffer(final String pdf1, final String pdf2) throws IOException {
        this.imagerForOldPdf = new PdfImager(pdf1);
        this.imagerForNewPdf = new PdfImager(pdf2);
        try {
            this.tmpdir = TempDir.createTempDir("pdfDiffer-");
            this.tmpdir.mkdir();
        } catch (final IOException e) {
            LOGGER.error("Temporäres Verzeichnis konnte nicht erzeugt werden.", e);
            FxHelper.createMessageDialog(AlertType.ERROR, "Verzeichnisproblem...", "Das temporäre Verzeichnis konnte nicht angelegt werden.", e).showAndWait();
            System.exit(1);
        }
        this.image.setSmooth(true);
        this.image.setPreserveRatio(true);
        this.image.setCache(true);
    }

    private Stage getStage() {
        final Scene scene = this.image.getScene();
        if (scene == null) {
            return null;
        }
        return ((Stage)(scene.getWindow()));
    }

    /**
     * this method calls the external tool and displays the image. Mainly to
     * have the exception handling bound here.
     */
    private boolean display() {
        return this.updateDisplayedImage(this.pageNo);
    }

    /**
     * calculates the number of pages regarding display type
     */
    private int maxPage() {
        switch (this.radioButtonGroup.getValue()) {
            case OLD:
                return this.imagerForOldPdf.getNumberOfPages();
            case NEW:
                return this.imagerForNewPdf.getNumberOfPages();
            case DIFF:
                return Math.min(this.imagerForOldPdf.getNumberOfPages(), this.imagerForNewPdf.getNumberOfPages());
            default:
                return 0;
        }

    }

    private void searchNextDifference() {
        if (this.radioButtonGroup.getValue() != DisplayType.DIFF) {
            final Alert alert = FxHelper.createMessageDialog(
                AlertType.ERROR,
                "Das geht nicht...",
                "Es wird nun die DIFF-Darstellung eingeschaltet");
            alert.showAndWait();
            this.setDisplayType(DisplayType.DIFF);
        }
        final Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() {
                while(PdfDiffer.this.pageNo < PdfDiffer.this.maxPage() - 1) {
                    PdfDiffer.this.pageNo++;
                    if (PdfDiffer.this.display()) {
                        break;
                    }
                }
                PdfDiffer.this.display();
                return null;
            }
        };
        final Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private boolean updateDisplayedImage(final int n) {

        if (n >= this.maxPage() || n < 0 ) {
            return false;
        }

        try {
            final boolean hasRed = this.displayImage(n);
            this.setProgress(this.pageNo);
            return hasRed;
        } catch (final Throwable e) {
            LOGGER.error("Fehler beim Einlesen eines Seiten-Bildes", e);
            FxHelper.createMessageDialog(
                AlertType.ERROR,
                "Einlesefehler",
                "Fehler beim Einlesen eines Seiten-Bildes", e).showAndWait();
            System.exit(1);
        }
        return false;
    }

    private boolean displayImage(final int n) throws IOException {
        final BufferedImage biOld = this.imagerForOldPdf.convertToImage(n);
        final BufferedImage biNew = this.imagerForNewPdf.convertToImage(n);
        final Image imageOld = SwingFXUtils.toFXImage(biOld, null);
        final Image imageNew = SwingFXUtils.toFXImage(biNew, null);
        final ImageDiffer differ = new ImageDiffer(biOld, biNew);

        final Image imageDiff = SwingFXUtils.toFXImage(differ.getDiff(), null);

        if (differ.hasDiffs()) {
            LOGGER.info("ROT auf Seite: " + (n + 1));
        }

        switch (this.radioButtonGroup.getValue()) {
        case OLD:
            this.image.setImage(imageOld);
            break;
        case NEW:
            this.image.setImage(imageNew);
            break;
        case DIFF:
            this.image.setImage(imageDiff);
            break;
        }
        LOGGER.debug("image updated.");

        Platform.runLater(()
            -> {
                this.info.setText(String.format("Seite %d/%d [%d,%d]", n + 1, this.maxPage(),
                    this.imagerForOldPdf.getNumberOfPages(), this.imagerForNewPdf.getNumberOfPages()));
            });
        return differ.hasDiffs();
    }

    /**
     * setup everything to start and display the user interface
     */
    public void setup(final GridPane root) {

        final ColumnConstraints fillConstraintC = new ColumnConstraints();
        fillConstraintC.setFillWidth(true);
        fillConstraintC.setHgrow(Priority.ALWAYS);
        final RowConstraints fillConstraintR = new RowConstraints();
        fillConstraintR.setFillHeight(true);
        fillConstraintR.setVgrow(Priority.ALWAYS);
        final ColumnConstraints staticConstraint = new ColumnConstraints(200);
        staticConstraint.setHalignment(HPos.RIGHT);
        staticConstraint.setHgrow(Priority.NEVER);
        final RowConstraints staticConstraintR = new RowConstraints();
        staticConstraintR.setFillHeight(false);
        staticConstraintR.setVgrow(Priority.NEVER);
        root.getColumnConstraints().addAll(fillConstraintC, staticConstraint);
        root.getRowConstraints().addAll(staticConstraintR, fillConstraintR);

        String home = System.getenv("DIFFER_HOME");
        if (home == null) {
            home = ".";
        }

        final Pane buttons = this.createButtons();
        root.add(buttons, 1, 1);

        this.scrollPane = new ZoomableScrollPane(this.image, factorX, factorY);
        this.scrollPane.setManaged(true);
        this.scrollPane.setVbarPolicy(ScrollBarPolicy.ALWAYS);
        this.scrollPane.setHbarPolicy(ScrollBarPolicy.ALWAYS);
        this.scrollPane.setPannable(true);
        root.add(this.scrollPane, 0, 1);

        root.add(this.info, 0, 0);
        root.setManaged(true);

        this.registerKeys(root);

        this.display();

    }

    private void registerKeys(final Pane pane) {
        pane.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case RIGHT:
                    this.forwardOnePage();
                    event.consume();
                    break;
                case LEFT:
                    this.backwardOnePage();
                    event.consume();
                    break;
                case N:
                    this.searchNextDifference();
                    event.consume();
                    break;
                //$CASES-OMITTED$
            default:
                    break;
            }
        });
    }

    private VBox createButtons() {
        final VBox buttons = new VBox();
        buttons.setMaxWidth(200);
        buttons.setMinWidth(200);
        this.createForwardButton(buttons);
        this.createBackButton(buttons);
        this.createFFButton(buttons);
        this.createBBButton(buttons);
        this.createSearchButton(buttons);
        this.createPageNumberField(buttons);
        this.createRadioButtons(buttons);
        this.createProgressBar(buttons);
        this.createQuitButton(buttons);
        return buttons;
    }

    void setProgress(final int value) {
        Platform.runLater(() -> this.progress.setProgress(Math.round((double)value / this.maxPage())));
    }

    private void createQuitButton(final VBox buttons) {
        final Button buttonQuit = new Button("Quit");
        buttonQuit.setPrefWidth(Double.MAX_VALUE);
        buttons.getChildren().add(buttonQuit);
        buttonQuit.setOnAction(event -> {
            this.getStage().close();
        });
    }

    private void createForwardButton(final VBox buttons) {
        final Button buttonForward = new Button(">");
        buttonForward.setPrefWidth(Double.MAX_VALUE);
        buttons.getChildren().add(buttonForward);
        buttonForward.setOnAction(event -> {
            this.forwardOnePage();
        });
    }

    private void createBackButton(final VBox buttons) {
        final Button buttonBack = new Button("<");
        buttonBack.setPrefWidth(Double.MAX_VALUE);
        buttons.getChildren().add(buttonBack);
        buttonBack.setOnAction(event -> {
            this.backwardOnePage();
        });
    }

    private void createBBButton(final VBox buttons) {
        final Button buttonBBack = new Button("<<<<");
        buttons.getChildren().add(buttonBBack);
        buttonBBack.setPrefWidth(Double.MAX_VALUE);
        buttonBBack.setOnAction(event -> {
            this.firstPage();
        });
    }

    private void createFFButton(final VBox buttons) {
        final Button buttonFForward = new Button(">>>>");
        buttons.getChildren().add(buttonFForward);
        buttonFForward.setPrefWidth(Double.MAX_VALUE);
        buttonFForward.setOnAction(event -> {
            this.lastPage();
        });
    }

    private void createSearchButton(final VBox buttons) {
        final Button buttonSearch = new Button("Search next Diff");
        buttons.getChildren().add(buttonSearch);
        buttonSearch.setPrefWidth(Double.MAX_VALUE);
        buttonSearch.setOnAction(event -> {
            this.searchNextDifference();
        });
    }

    private void createPageNumberField(final Pane buttons) {
        final TextField textField = new TextField();
        textField.setEditable(true);
        buttons.getChildren().add(textField);
        textField.setOnAction(event -> {
            final String num = textField.getText();
            if (num.matches("[0-9]*")) {
                if (num.isEmpty()) {
                    this.pageNo++;
                    if (this.pageNo > this.maxPage() - 1) {
                        this.pageNo = this.maxPage() - 1;
                    }
                } else {
                    this.pageNo = new Integer(textField.getText()) - 1;
                }
                if (this.pageNo < 0) {
                    this.pageNo = 0;
                }
                this.display();
                textField.setText("");
            }
        });
    }

    private void createRadioButtons(final Pane buttons) {
        final HBox rb = new HBox();
        rb.setPrefWidth(Double.MAX_VALUE);
        buttons.getChildren().add(rb);
        this.radioButtonGroup = new RadioButtonGroup<DisplayType>(DisplayType.class, rb, DisplayType.DIFF);
        this.radioButtonGroup.addObserver((o, arg) -> this.display());
    }

    private void createProgressBar(final VBox buttons) {
        this.progress = new ProgressBar();
        this.progress.setMaxWidth(Double.MAX_VALUE);
        buttons.getChildren().add(this.progress);
        this.progress.setProgress(0);
    }

    private void forwardOnePage() {
        this.pageNo++;
        if (this.pageNo > this.maxPage() - 1) {
            this.pageNo = this.maxPage() - 1;
        }
        this.display();
    }

    private void backwardOnePage() {
        this.pageNo--;
        if (this.pageNo < 0) {
            this.pageNo = 0;
        }
        this.display();
    }

    private void firstPage() {
        this.pageNo = 0;
        this.display();
    }

    private void lastPage() {
        this.pageNo = this.maxPage() - 1;
        this.display();
    }

    private void setDisplayType(final DisplayType type) {
        this.radioButtonGroup.setValue(type);
    }

}
