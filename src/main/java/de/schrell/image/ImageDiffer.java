package de.schrell.image;

import java.awt.Color;
import java.awt.image.BufferedImage;

public class ImageDiffer {

    private final BufferedImage diffImage;

    private boolean hasDiffs;

    public ImageDiffer(final BufferedImage oldImage, final BufferedImage newImage) {
        this.diffImage = this.diffImage(oldImage, newImage);
    }

    private BufferedImage diffImage(final BufferedImage biOld, final BufferedImage biNew) {
        final BufferedImage biDiff = new BufferedImage(biOld.getWidth(), biOld.getHeight(), biOld.getType());
        final int red = Color.RED.getRGB();
        for (int x = 0; x < biOld.getWidth(); x++) {
            for (int y = 0; y < biOld.getHeight(); y++) {
                final int cOld = biOld.getRGB(x,y);
                final int cNew = biNew.getRGB(x,y);
                if (cOld == cNew) {
                    biDiff.setRGB(x, y, lighten(new Color(cOld), 0.5).getRGB());
                } else {
                    this.hasDiffs = true;
                    biDiff.setRGB(x, y, red);
                }
            }
        }
        return biDiff;
    }

    public static Color lighten(final Color inColor, final double inAmount)
    {
      return new Color(
        (int) Math.min(255, inColor.getRed() + 255 * inAmount),
        (int) Math.min(255, inColor.getGreen() + 255 * inAmount),
        (int) Math.min(255, inColor.getBlue() + 255 * inAmount),
        inColor.getAlpha());
    }

    public BufferedImage getDiff() {
        return this.diffImage;
    }

    public boolean hasDiffs() {
        return this.hasDiffs;
    }

}
