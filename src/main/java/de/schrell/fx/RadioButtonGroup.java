package de.schrell.fx;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;

import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;

/**
 * Gruppe von Radio-Buttons.
 */
public class RadioButtonGroup<T extends Enum<T>> extends Observable {

    private T value;
    private final ToggleGroup toggleGroup = new ToggleGroup();

    private final Map<T, RadioButton> buttons = new HashMap<>();

    public RadioButtonGroup(final Class<T> clazz, final Pane parent, final T startValue) {
        this.value = startValue;
        for (final T t : clazz.getEnumConstants()) {
            final String text = t.toString();
            final RadioButton rb = new RadioButton(text);
            rb.setToggleGroup(this.toggleGroup);
            rb.setPrefSize(300, 30);
            if (t.equals(startValue)) {
                rb.selectedProperty().set(true);
            }
            rb.setOnAction(event -> {
                if (!t.equals(this.value)) {
                    this.value = t;
                    this.setChanged();
                    this.notifyObservers(this.value);
                }
            });
            parent.getChildren().add(rb);
            this.buttons.put(t, rb);
        }
    }

    public T getValue() {
        return this.value;
    }

    public void setValue(final T value) {
        if (!value.equals(this.value)) {
            this.value = value;
            this.setChanged();
            this.buttons.get(value).selectedProperty().set(true);
            this.notifyObservers(this.value);
        }
    }

}
