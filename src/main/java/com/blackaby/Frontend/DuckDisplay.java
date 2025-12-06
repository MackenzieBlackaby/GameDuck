package com.blackaby.Frontend;

import java.awt.*;
import java.awt.image.*;
import javax.swing.*;

import com.blackaby.OldBackEnd.Emulation.Misc.Specifics;

/**
 * A custom JPanel for rendering Game Boy display output.
 * Handles pixel manipulation, image scaling, and drawing logic.
 */
public class DuckDisplay extends JPanel {
    private BufferedImage image;

    /**
     * Constructs a DuckDisplay with a black background and
     * initialises the image buffer to the standard Game Boy resolution.
     */
    public DuckDisplay() {
        super();
        setBackground(Color.BLACK);
        // Initialize image with default size
        image = new BufferedImage(Specifics.GB_DISPLAY_WIDTH, Specifics.GB_DISPLAY_HEIGHT, BufferedImage.TYPE_INT_RGB);
    }

    /**
     * Sets the colour of a pixel at the specified coordinates.
     * Optionally triggers a repaint of the component.
     *
     * @param x       X coordinate of the pixel
     * @param y       Y coordinate of the pixel
     * @param color   Colour to apply
     * @param repaint Whether to repaint the component afterwards
     */
    public void setPixel(int x, int y, Color color, boolean repaint) {
        if (image != null && x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
            image.setRGB(x, y, color.getRGB());
            if (repaint) {
                repaint();
            }
        }
    }

    /**
     * Sets the colour of a pixel and repaints the component.
     *
     * @param x     X coordinate of the pixel
     * @param y     Y coordinate of the pixel
     * @param color Colour to apply
     */
    public void setPixel(int x, int y, Color color) {
        setPixel(x, y, color, true);
    }

    /**
     * Sets the colour of a pixel using a hexadecimal string.
     * Optionally triggers a repaint of the component.
     *
     * @param x        X coordinate of the pixel
     * @param y        Y coordinate of the pixel
     * @param hexColor Colour in hexadecimal format (e.g., "#FFFFFF")
     * @param repaint  Whether to repaint the component afterwards
     */
    public void setPixel(int x, int y, String hexColor, boolean repaint) {
        setPixel(x, y, Color.decode(hexColor), repaint);
    }

    /**
     * Sets the colour of a pixel using a hexadecimal string
     * and repaints the component.
     *
     * @param x        X coordinate of the pixel
     * @param y        Y coordinate of the pixel
     * @param hexColor Colour in hexadecimal format (e.g., "#FFFFFF")
     */
    public void setPixel(int x, int y, String hexColor) {
        setPixel(x, y, hexColor, true);
    }

    /**
     * Clears the display by setting all pixels to black,
     * then repaints the component.
     */
    public void clear() {
        for (int x = 0; x < Specifics.GB_DISPLAY_WIDTH; x++) {
            for (int y = 0; y < Specifics.GB_DISPLAY_HEIGHT; y++) {
                setPixel(x, y, Color.BLACK, false);
            }
        }
        repaint();
    }

    /**
     * Repaints the component with the current image,
     * scaling it to fit the component while maintaining aspect ratio.
     *
     * @param g Graphics context used for rendering
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            // Calculate scaled dimensions while maintaining aspect ratio
            double scale = Math.min(
                    getWidth() / (double) Specifics.GB_DISPLAY_WIDTH,
                    getHeight() / (double) Specifics.GB_DISPLAY_HEIGHT);
            int scaledWidth = (int) (Specifics.GB_DISPLAY_WIDTH * scale);
            int scaledHeight = (int) (Specifics.GB_DISPLAY_HEIGHT * scale);

            // Calculate position to center the scaled image
            int x = (getWidth() - scaledWidth) / 2;
            int y = (getHeight() - scaledHeight) / 2;

            // Draw scaled image
            g.drawImage(image, x, y, scaledWidth, scaledHeight, null);
        }
    }

    /**
     * Resizes the internal image buffer to match the
     * standard Game Boy resolution, preserving existing content if possible.
     */
    public void resizeImage() {
        // Keep the original GB resolution
        if (image == null || image.getWidth() != Specifics.GB_DISPLAY_WIDTH ||
                image.getHeight() != Specifics.GB_DISPLAY_HEIGHT) {
            BufferedImage newImage = new BufferedImage(
                    Specifics.GB_DISPLAY_WIDTH,
                    Specifics.GB_DISPLAY_HEIGHT,
                    BufferedImage.TYPE_INT_RGB);
            if (image != null) {
                Graphics2D g2d = newImage.createGraphics();
                g2d.drawImage(image, 0, 0, null);
                g2d.dispose();
            }
            image = newImage;
            repaint();
        }
    }

    /**
     * Returns the minimum size for this component.
     *
     * @return Minimum dimension (100x100)
     */
    @Override
    public Dimension getMinimumSize() {
        return new Dimension(100, 100);
    }

    /**
     * Returns the preferred size of this component.
     * Based on the size of the parent container, maintaining a square shape.
     *
     * @return Preferred dimension
     */
    @Override
    public Dimension getPreferredSize() {
        Container parent = getParent();
        if (parent != null) {
            int size = Math.min(parent.getWidth(), parent.getHeight());
            size = Math.max(size - 20, 100);
            return new Dimension(size, size);
        }
        return new Dimension(400, 400);
    }
}