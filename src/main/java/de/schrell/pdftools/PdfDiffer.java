package de.schrell.pdftools;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.schrell.fx.FxHelper;
import de.schrell.fx.RadioButtonGroup;
import de.schrell.fx.ZoomableScrollPane;
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
import javafx.scene.control.Spinner;
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
     * the old pdf file
     */
    private final String pdf1;

    /**
     * the new pdf file
     */
    private final String pdf2;

    /**
     * The actual page number
     */
    private volatile int pageNo;

    /**
     * the number of pages in the first file(s)
     */
    private final int maxPages1;

    /**
     * the number of pages in the second file
     */
    private final int maxPages2;

    /**
     * infoline at the top
     */
    private final Label info = new Label("INFOZEILE");

    /**
     * Folder with ImageMagick
     */
    private final String imhome = System.getenv("MAGICK_HOME");

    /**
     * Seiten in Arbeit
     */
    private static volatile Collection<Integer> workin = new ConcurrentLinkedDeque<>();

    /**
     * Returncodes von compare.
     */
    private static volatile ConcurrentHashMap<String, Integer> retCodes = new ConcurrentHashMap<>();

    /**
     * im vor ausbearbeitete Seiten
     */
    private static final int PREFETCH = 20;

    /**
     * Fortschrittsbalken.
     */
    private volatile ProgressIndicator progress;

    /**
     * Schnelles Berechnen der Seiten um die aktuelle herum.
     */
    ExecutorService exer = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors(),
        r -> {
            final Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });

    /**
     * Vorausberechnung aller Seiten mit halber Kraft.
     */
    ExecutorService miniExer = Executors.newFixedThreadPool(
        Math.max(1, Runtime.getRuntime().availableProcessors() - 2),
        r -> {
            final Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });

    private volatile RadioButtonGroup<DisplayType> radioButtonGroup;

    private static final double INIT_ZOOM = 0.5;
    private volatile static DoubleProperty factorX = new SimpleDoubleProperty(INIT_ZOOM);
    private volatile static DoubleProperty factorY = new SimpleDoubleProperty(INIT_ZOOM);

    private volatile ScrollPane scrollPane;

    private final Spinner<Double> spinner = new Spinner<>(0.1, 2.0, INIT_ZOOM, 0.05);

    private final PdfImager imager1;
    private final PdfImager imager2;

    /**
     * Konstruktor.
     */
    public PdfDiffer(final String pdf1, final String pdf2) throws IOException {
        this.imager1 = new PdfImager(pdf1);
        this.imager2 = new PdfImager(pdf2);
        this.pdf1 = pdf1;
        this.pdf2 = pdf2;
        this.maxPages1 = PdfProperties.numberOfPages(this.pdf1);
        this.maxPages2 = PdfProperties.numberOfPages(this.pdf2);
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
        for (int x = -PREFETCH; x <= PREFETCH; x++) {
            if (x == 0) {
                continue;
            }
            final int pre = x;
            this.exer.execute(() -> this.doubledImage(this.pageNo + pre, true));
        }
        return this.doubledImage(this.pageNo, false);
    }

    /**
     * calculates the number of pages regarding display type
     */
    private int maxPage() {
        switch (this.radioButtonGroup.getValue()) {
            case OLD:
                return this.maxPages1;
            case NEW:
                return this.maxPages2;
            case DIFF:
                return Math.min(this.maxPages1, this.maxPages2);
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

    /**
     * Generate the temp file names fo the picture files, generate rthe external
     * commands and start them.
     *
     * @param n
     *            the page number to display
     */
    private boolean doubledImage(final int n, final boolean backGround) {

        if (n >= this.maxPage() || n < 0 ) {
            return false;
        }

        String cmdOld[];
        String cmdNew[];
        String cmdDiff[];
        final String path = this.tmpdir.getAbsolutePath() + File.separator;
        final String imBin = (this.imhome == null || this.imhome.isEmpty()) ? "" : this.imhome + File.separator;
        String nameOld;
        String nameNew;
        String nameDiff;
        //final String geometry = calcGeometry();
        nameOld = String.format("OLD%08d.png", n);
        cmdOld = new String[] { imBin + "convert",
                "-density", "150",
                "-quality", "100",
                //"-geometry", geometry,
                String.format("%s[%d]", this.pdf1, n),
                "-channel", "rgba",
                "-alpha", "background",
                path + "tmp" + nameOld };
        nameNew = String.format("NEW%08d.png", n);
        cmdNew = new String[] { imBin + "convert",
                "-density", "150",
                "-quality", "100",
                //"-geometry", geometry,
                String.format("%s[%d]", this.pdf2, n),
                "-channel", "rgba",
                "-alpha", "background",
                path + "tmp" + nameNew };
        nameDiff = String.format("DIF%08d.png", n);
        cmdDiff = new String[] { imBin + "compare",
                "-density", "150",
                "-quality", "100",
                //"-size", geometry,
                "-colorspace", "gray",
                "-compose", "src",
                String.format("%s[%d]", this.pdf1, n),
                String.format("%s[%d]", this.pdf2, n),
                "-compose", "blend",
                String.format("%s[%d]", this.pdf1, n),
                path + "tmp" + nameDiff };
        switch (this.radioButtonGroup.getValue()) {
        case OLD:
            createFile(n, cmdOld, path, nameOld);
            break;
        case NEW:
            createFile(n, cmdNew, path, nameNew);
            break;
        case DIFF:
            createFile(n, cmdDiff, path, nameDiff);
            break;
        default:
        }
        if (!backGround) {
            return this.updateDisplayedImage(n, path, nameOld, nameNew, nameDiff);
        }
        return false;
    }

    private boolean updateDisplayedImage(final int n, final String path, final String nameOld, final String nameNew, final String nameDiff) {
        try {
            String fileName;
            switch (this.radioButtonGroup.getValue()) {
            case OLD:
                fileName = path + nameOld;
                break;
            case NEW:
                fileName = path + nameNew;
                break;
            case DIFF:
                fileName = path + nameDiff;
                break;
            default:
                fileName = "???";
            }
            final boolean hasRed = this.displayImage(n, fileName);
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

    private boolean displayImage(final int n, final String fileName) throws IOException {
        final BufferedImage bi = this.imager1.convertToImage(n);
        boolean hasRed = false;
        //TEST bi = ImageIO.read(new File(fileName));
//        final double scaleX = (double)calcImageWidth() / bi.getWidth();
//        final double scaleY = (double)calcImageHeight() / bi.getHeight();
//        factorX.set(Math.min(scaleX, scaleY));
//        factorY.set(Math.min(scaleX, scaleY));

//        spinner.valueFactoryProperty().getValue().setValue(0.3);

//        LOGGER.debug("SIZE BufferedImage: " + bi.getWidth() + "x" + bi.getHeight() + " scaled " + scaleX);
        final Image image = SwingFXUtils.toFXImage(bi, null);
        this.image.setImage(image);
        //                );
        if (hasRed(bi)) {
            hasRed = true;
            LOGGER.info("ROT auf Seite: " + (n + 1));
        }
        LOGGER.debug("image updated.");
        //Platform.runLater(() -> this.info.setText(
        String.format("Seite %d/%d [%d,%d]", n + 1, this.maxPage(), this.maxPages1, this.maxPages2);
        //        ));
        return hasRed;
    }

//    private static String calcGeometry() {
//        final String geometry = calcImageWidth() + "x" + calcImageHeight();
//        return geometry;
//    }

    private static int createFile(final int n, final String[] cmd, final String path, final String name) {
        if (!new File(path + name).exists() && !workin.contains(n)) {
            return PdfDiffer.createPage(n, cmd, path, name);
        } else {
            waitForChain(n);
            LOGGER.debug("reusing File=" + path + name);
            return retCodes.get(name);
        }
    }

    private static void waitForChain(final int n) {
        while (workin.contains(n)) {
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static int createPage(final int n, final String[] cmd, final String path, final String name) {
        logCommand(cmd);
        workin.add(n);
        try {

            //final Process proc = Runtime.getRuntime().exec(cmd);
            final int rc = 0; //proc.waitFor();

//            if (rc == 2) {
//                String stdout = "\n";
//                String stderr = "\n";
//                try (final InputStream errors = proc.getErrorStream()) {
//                    stderr = logStream(errors, Level.ERROR);
//                }
//                try (final InputStream output = proc.getInputStream()) {
//                    stdout = logStream(output, Level.INFO);
//                }
//                LOGGER.error("Fehler beim Aufruf von ImageMagick-Compare");
//                FxHelper.createMessageDialog(
//                    AlertType.ERROR,
//                    "Einlesefehler",
//                    "Fehler beim Aufruf von ImageMagick-Compare" +
//                    ":\nSTDOUT:\n" + stdout + "STDERR:\n" + stderr).showAndWait();
//                System.exit(1);
//
//            }
            retCodes.put(name, rc);
            Files.move(Paths.get(path + "tmp" + name), Paths.get(path + name), StandardCopyOption.ATOMIC_MOVE);
            LOGGER.debug("renamed file: " + path + "tmp" + name + " -> " + path + name);
            workin.remove(n);
            LOGGER.debug("workin on " + workin.size() + " items.");
            LOGGER.debug("done. File=" + path + name);
            return rc;
//        } catch (final InterruptedException e) {
//            Thread.currentThread().interrupt();
        } catch (final IOException e) {
            LOGGER.error("Fehler beim Umwandeln oder Vergleichen einer Seite", e);
//            Platform.runLater(()
//                    -> {
//                        FxHelper.createMessageDialog(
//                            AlertType.ERROR,
//                            "Fehler beim Umwandeln",
//                            "Fehler beim Umwandeln oder Vergleichen einer Seite", e).showAndWait();
//                        System.exit(1);
//                    });
        }
        return 2; // error or interrupt
    }

    private static String logStream(final InputStream stream, final Level level) throws IOException {
        final StringBuilder ret = new StringBuilder();
        try (InputStreamReader ir = new InputStreamReader(stream); final BufferedReader r = new BufferedReader(ir)) {
            do {
                final String line = r.readLine();
                if (line == null) {
                    break;
                }
                ret.append(line).append("\n");
                LOGGER.log(level, line);
            } while (true);
        }
        return ret.toString();
    }

    private static void logCommand(final String[] cmd) {
        final StringBuilder asString = new StringBuilder("starting command:");
        for (final String element : cmd) {
            asString.append(" ").append(element);
        }
        LOGGER.debug(asString.toString());
    }

    static boolean hasRed(final BufferedImage bi) {
        for (int y = 0; y < bi.getHeight(); y++) {
            for (int x = 0; x < bi.getWidth(); x++) {

                final int c = bi.getRGB(x,y);
                final Color color = new Color(c);

                if (color.getRed() > color.getGreen() && color.getRed() != color.getBlue()) {
                    return true;
                }

            }
        }
        return false;
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

        if (PREFETCH > 0) {
            for (int i = 0; i < this.maxPage(); i++) {
                final int pre = i;
                this.miniExer.execute(() -> this.doubledImage(pre, true));
            }
        }

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
//        this.createZoomSpinner(buttons);
        return buttons;
    }

    void setProgress(final int value) {
        Platform.runLater(() -> this.progress.setProgress((double)value / this.maxPage()));
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

    private void createZoomSpinner(final VBox buttons) {
        this.spinner.setPrefWidth(Double.MAX_VALUE);
        this.spinner.valueProperty().addListener((observable, oldvalue, scale)
            -> {
                factorX.set(scale);
                factorY.set(scale);
            });
        buttons.getChildren().add(this.spinner);
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
