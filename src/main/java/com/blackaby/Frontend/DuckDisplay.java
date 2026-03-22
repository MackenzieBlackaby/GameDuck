package com.blackaby.Frontend;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import javax.swing.*;

import com.blackaby.Backend.Emulation.Misc.Specifics;

/**
 * A custom JPanel for rendering Game Boy display output.
 * Handles pixel manipulation, image scaling, and drawing logic.
 */
public class DuckDisplay extends JPanel {
    private static final Dimension DEFAULT_DISPLAY_SIZE = new Dimension(640, 576);
    private static final Dimension MINIMUM_DISPLAY_SIZE = new Dimension(160, 144);

    public record FrameState(int[] frontBuffer, int[] backBuffer) implements java.io.Serializable {
    }

    private final Object frameLock = new Object();
    private BufferedImage image;
    private int[] frontBuffer;
    private int[] backBuffer;

    /**
     * Constructs a DuckDisplay with a black background and
     * initialises the image buffer to the standard Game Boy resolution.
     */
    public DuckDisplay() {
        super();
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
        initializeBuffers();
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
        if (color != null) {
            setPixel(x, y, color.getRGB(), repaint);
        }
    }

    /**
     * Sets the colour of a pixel using a packed RGB value.
     *
     * @param x       X coordinate of the pixel
     * @param y       Y coordinate of the pixel
     * @param rgb     Packed RGB value
     * @param repaint Whether to repaint the component afterwards
     */
    public void setPixel(int x, int y, int rgb, boolean repaint) {
        if (backBuffer != null && x >= 0 && x < Specifics.GB_DISPLAY_WIDTH && y >= 0 && y < Specifics.GB_DISPLAY_HEIGHT) {
            backBuffer[(y * Specifics.GB_DISPLAY_WIDTH) + x] = rgb;
            if (repaint) {
                presentFrame();
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
     * Sets the colour of a pixel and repaints the component.
     *
     * @param x   X coordinate of the pixel
     * @param y   Y coordinate of the pixel
     * @param rgb Packed RGB value
     */
    public void setPixel(int x, int y, int rgb) {
        setPixel(x, y, rgb, true);
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
        if (backBuffer == null || frontBuffer == null) {
            return;
        }

        Arrays.fill(backBuffer, Color.BLACK.getRGB());
        synchronized (frameLock) {
            Arrays.fill(frontBuffer, Color.BLACK.getRGB());
        }

        SwingUtilities.invokeLater(this::repaint);
    }

    /**
     * Copies the completed emulation back buffer to the image presented on the EDT.
     */
    public void presentFrame() {
        if (backBuffer == null || frontBuffer == null) {
            return;
        }

        synchronized (frameLock) {
            System.arraycopy(backBuffer, 0, frontBuffer, 0, backBuffer.length);
        }

        SwingUtilities.invokeLater(this::repaint);
    }

    /**
     * Returns a copy of the currently visible and in-progress frame buffers.
     *
     * @return frame snapshot
     */
    public FrameState SnapshotFrameState() {
        if (backBuffer == null || frontBuffer == null) {
            return new FrameState(new int[0], new int[0]);
        }

        synchronized (frameLock) {
            return new FrameState(
                    Arrays.copyOf(frontBuffer, frontBuffer.length),
                    Arrays.copyOf(backBuffer, backBuffer.length));
        }
    }

    /**
     * Restores the currently visible and in-progress frame buffers.
     *
     * @param frameState frame snapshot to restore
     */
    public void RestoreFrameState(FrameState frameState) {
        if (frameState == null || frontBuffer == null || backBuffer == null) {
            return;
        }
        if (frameState.frontBuffer() == null || frameState.backBuffer() == null
                || frameState.frontBuffer().length != frontBuffer.length
                || frameState.backBuffer().length != backBuffer.length) {
            throw new IllegalArgumentException("Quick state frame data is invalid for this display.");
        }

        synchronized (frameLock) {
            System.arraycopy(frameState.frontBuffer(), 0, frontBuffer, 0, frontBuffer.length);
            System.arraycopy(frameState.backBuffer(), 0, backBuffer, 0, backBuffer.length);
        }

        SwingUtilities.invokeLater(this::repaint);
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
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

            // Calculate scaled dimensions while maintaining aspect ratio
            double scale = Math.min(
                    getWidth() / (double) Specifics.GB_DISPLAY_WIDTH,
                    getHeight() / (double) Specifics.GB_DISPLAY_HEIGHT);
            int scaledWidth = (int) (Specifics.GB_DISPLAY_WIDTH * scale);
            int scaledHeight = (int) (Specifics.GB_DISPLAY_HEIGHT * scale);

            // Calculate position to center the scaled image
            int x = (getWidth() - scaledWidth) / 2;
            int y = (getHeight() - scaledHeight) / 2;

            synchronized (frameLock) {
                g2d.drawImage(image, x, y, scaledWidth, scaledHeight, null);
            }
            g2d.dispose();
        }
    }

    /**
     * Resizes the internal image buffer to match the
     * standard Game Boy resolution, preserving existing content if possible.
     */
    public void resizeImage() {
        initializeBuffers();
        SwingUtilities.invokeLater(this::repaint);
    }

    /**
     * Returns the minimum size for this component.
     *
     * @return Minimum dimension (100x100)
     */
    @Override
    public Dimension getMinimumSize() {
        return MINIMUM_DISPLAY_SIZE;
    }

    /**
     * Returns the preferred size of this component.
     * Based on the size of the parent container, maintaining a square shape.
     *
     * @return Preferred dimension
     */
    @Override
    public Dimension getPreferredSize() {
        return DEFAULT_DISPLAY_SIZE;
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private void initializeBuffers() {
        image = new BufferedImage(Specifics.GB_DISPLAY_WIDTH, Specifics.GB_DISPLAY_HEIGHT, BufferedImage.TYPE_INT_RGB);
        frontBuffer = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        backBuffer = new int[Specifics.GB_DISPLAY_WIDTH * Specifics.GB_DISPLAY_HEIGHT];
        Arrays.fill(frontBuffer, Color.BLACK.getRGB());
        Arrays.fill(backBuffer, Color.BLACK.getRGB());
    }
}
