package com.blackaby.Frontend;

import java.awt.*;
import java.awt.image.*;
import javax.swing.*;
import com.blackaby.Backend.Emulation.Misc.Specifics;
import com.blackaby.Backend.Emulation.Graphics.GBImage;

public class DuckDisplay extends JPanel {
    private BufferedImage image;

    public DuckDisplay() {
        super();
        setBackground(Color.BLACK);
        // Initialize image with default size
        image = new BufferedImage(Specifics.GB_DISPLAY_WIDTH, Specifics.GB_DISPLAY_HEIGHT, BufferedImage.TYPE_INT_RGB);
    }

    /**
     * Sets a pixel at the specified coordinates
     * 
     * @param x       X coordinate
     * @param y       Y coordinate
     * @param color   Color to set
     * @param repaint Whether to repaint the component after setting the pixel
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
     * Sets a pixel at the specified coordinates
     * 
     * @param x     X coordinate
     * @param y     Y coordinate
     * @param color Color to set
     */
    public void setPixel(int x, int y, Color color) {
        setPixel(x, y, color, true);
    }

    /**
     * Sets a pixel at the specified coordinates
     * 
     * @param x        X coordinate
     * @param y        Y coordinate
     * @param hexColor Hexadecimal color to set
     * @param repaint  Whether to repaint the component after setting the pixel
     */
    public void setPixel(int x, int y, String hexColor, boolean repaint) {
        setPixel(x, y, Color.decode(hexColor), repaint);
    }

    /**
     * Sets a pixel at the specified coordinates
     * 
     * @param x        X coordinate
     * @param y        Y coordinate
     * @param hexColor Hexadecimal color to set
     */
    public void setPixel(int x, int y, String hexColor) {
        setPixel(x, y, hexColor, true);
    }

    /**
     * Sets the image to the specified GBImage
     * 
     * @param image GBImage to set
     */
    public void setImage(GBImage image) {
        for (int x = 0; x < Specifics.GB_DISPLAY_WIDTH; x++) {
            for (int y = 0; y < Specifics.GB_DISPLAY_HEIGHT; y++) {
                setPixel(x, y, image.getPixelColor(x, y), false);
            }
        }
    }

    /**
     * Clears the display
     */
    public void clear() {
        for (int x = 0; x < Specifics.GB_DISPLAY_WIDTH; x++) {
            for (int y = 0; y < Specifics.GB_DISPLAY_HEIGHT; y++) {
                setPixel(x, y, Color.BLACK, false);
            }
        }
        repaint();
    }

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
     * Resizes the image buffer when the component is resized
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

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(100, 100);
    }

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