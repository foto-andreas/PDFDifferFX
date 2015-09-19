package de.schrell.fx;

import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Scale;

public class ZoomableScrollPane extends ScrollPane{

    Group zoomGroup;
    Scale scaleTransform;
    Node content;

    private static final double MAX_SCALE = 8;
    private static final double MIN_SCALE = 0.1;

    private class ZoomHandler implements EventHandler<ScrollEvent> {

        private final Node nodeToZoom;

        private ZoomHandler(final Node nodeToZoom) {
            this.nodeToZoom = nodeToZoom;
        }

        @Override
        public void handle(final ScrollEvent scrollEvent) {
            if (scrollEvent.isControlDown()) {
                final double scale = this.calculateScale(scrollEvent);
                this.nodeToZoom.setScaleX(scale);
                this.nodeToZoom.setScaleY(scale);
                scrollEvent.consume();
            }
        }

        private double calculateScale(final ScrollEvent scrollEvent) {
            double scale = this.nodeToZoom.getScaleX() + scrollEvent.getDeltaY() / 100;

            if (scale <= MIN_SCALE) {
                scale = MIN_SCALE;
            } else if (scale >= MAX_SCALE) {
                scale = MAX_SCALE;
            }
            return scale;
        }
    }

    public ZoomableScrollPane(final Node content, final double initZoom)
    {
      this.content = content;
//      this.zoomX = zoomX;
//      this.zoomY = zoomY;
      final Group contentGroup = new Group();
      this.zoomGroup = new Group();
      contentGroup.getChildren().add(this.zoomGroup);
      this.zoomGroup.getChildren().add(content);
      this.setContent(contentGroup);
      this.scaleTransform = new Scale(initZoom, initZoom);
      this.scaleTransform.setZ(0);
      this.scaleTransform.setPivotX(0);
      this.scaleTransform.setPivotY(0);
      this.scaleTransform.setPivotZ(0);
      this.zoomGroup.getTransforms().add(this.scaleTransform);
      this.addEventFilter(ScrollEvent.ANY, new ZoomHandler(this.zoomGroup));
    }

  }