package de.schrell.pdftools;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import de.schrell.fx.FxHelper;
import de.schrell.fx.RadioButtonGroup;
import de.schrell.tools.TempDir;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

/**
 * Adapted from some examples to implement a Diff for PDF files. It uses
 * ghostscript and imagemagick as external tools.
 *
 * @author joshua.marinacci@sun.com
 */
public class PdfDiffer {

    /**
     * Logger for this class
     */
    private final static Logger LOGGER = Logger.getLogger(PdfDiffer.class);

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
    private File tmpdir = null;

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
    private int pageNo;

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
     * im vor ausbearbeitete Seiten
     */
    private static final int PREFETCH = 20;

    /**
     * Fortschrittsbalken.
     */
    private ProgressIndicator progress;

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
        Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
        r -> {
            final Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });

    private RadioButtonGroup<DisplayType> radioButtonGroup;

    /**
     * Konstruktor.
     */
    public PdfDiffer(final String pdf1, final String pdf2) {
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

        String cmd[];
        final String path = this.tmpdir.getAbsolutePath() + File.separator;
        final String imBin = (this.imhome == null || this.imhome.isEmpty()) ? "" : this.imhome + File.separator;
        String name;
        switch (this.radioButtonGroup.getValue()) {
            case OLD:
                name = String.format("OLD%08d.png", n);
                cmd = new String[] { imBin + "convert",
                    String.format("%s[%d]", this.pdf1, n), path + "tmp" + name };
                break;
            case NEW:
                name = String.format("NEW%08d.png", n);
                cmd = new String[] { imBin + "convert",
                    String.format("%s[%d]", this.pdf2, n), path + "tmp" + name };
                break;
            case DIFF:
                name = String.format("DIF%08d.png", n);
                cmd = new String[] { imBin + "compare",
                    String.format("%s[%d]", this.pdf1, n),
                    String.format("%s[%d]", this.pdf2, n), path + "tmp" + name };
                break;
            default:
                return false;
        }
        final String filename = path + name;
        if (!new File(filename).exists() && !workin.contains(n)) {
            workin.add(n);
            System.out.println("starting command:");
            for (final String element : cmd) {
                System.out.print(element + " ");
            }
            System.out.println();
            try {
                final Process proc = Runtime.getRuntime().exec(cmd);
                proc.waitFor();
                Files.move(Paths.get(path + "tmp" + name), Paths.get(filename), StandardCopyOption.ATOMIC_MOVE);
                workin.remove(n);
                System.out.println("workin on " + workin.size() + " items.");
                System.out.println("done. File=" + filename);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final IOException e) {
                e.printStackTrace();
            }
        } else {
            while (workin.contains(n)) {
                try {
                    Thread.sleep(100);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("reusing File=" + filename);
        }
        boolean hasRed = false;
        if (!backGround) {
            BufferedImage bi;
            try {
                bi = ImageIO.read(new File(filename));
                final Image image = SwingFXUtils.toFXImage(bi, null);
                //                Platform.runLater(() ->
                this.image.setImage(image);
                //                );
                if (hasRed(bi)) {
                    hasRed = true;
                    System.out.println("ROT: " + (n + 1));
                }
                System.out.println("image updated.");
                Platform.runLater(() -> this.info.setText(
                    String.format("Seite %d/%d [%d,%d]", n + 1, this.maxPage(), this.maxPages1, this.maxPages2)));
            } catch (final IOException e) {
                LOGGER.error("Fehler beim Einlesen des Bildes", e);
                FxHelper.createMessageDialog(
                    AlertType.ERROR,
                    "Einlesefehler",
                    "Fehler beim Einlesen einer Seiten-Bildes", e).showAndWait();
            }
            this.setProgress(this.pageNo);
        }
        return hasRed;
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

        String home = System.getenv("DIFFER_HOME");
        if (home == null) {
            home = ".";
        }

        PropertyConfigurator.configure(home + File.separator + "log4j.properties");

        final Pane buttons = this.createButtons();
        root.add(buttons, 1, 1);

        root.add(this.image, 0, 1);

        root.add(this.info, 0, 0);

        for (int i = 0; i < this.maxPage(); i++) {
            final int pre = i;
            this.miniExer.execute(() -> this.doubledImage(pre, true));
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
                default:
                    break;
            }
        });
    }

    private VBox createButtons() {
        final VBox buttons = new VBox();
        buttons.setMaxWidth(200);
        this.createForwardButton(buttons);
        this.createBackButton(buttons);
        this.createFFButton(buttons);
        this.createBBButton(buttons);
        this.createSearchButton(buttons);
        this.createPageNumberField(buttons);
        this.createRadioButtons(buttons);
        this.createProgressBar(buttons);
        return buttons;
    }

    void setProgress(final int value) {
        Platform.runLater(() -> this.progress.setProgress((double)value / this.maxPage()));
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
