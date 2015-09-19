package de.schrell.pdftools;

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