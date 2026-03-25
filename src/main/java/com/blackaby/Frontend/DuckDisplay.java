package com.blackaby.Frontend;

import com.blackaby.Backend.Emulation.DuckBackend;
import com.blackaby.Backend.Platform.EmulatorDisplaySpec;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

/**
 * A custom JPanel for rendering Game Boy display output.
 * Handles pixel manipulation, image scaling, and drawing logic.
 */
public class DuckDisplay extends JPanel {
    public record FrameState(int[] frontBuffer, int[] backBuffer) implements java.io.Serializable {
    }

    private final EmulatorDisplaySpec displaySpec;
    private final Object frameLock = new Object();
    private final AtomicBoolean repaintQueued = new AtomicBoolean();
    private BufferedImage image;
    private int[] frontBuffer;
    private int[] backBuffer;

    /**
     * Constructs a DuckDisplay with a black background and
     * initialises the image buffer to the standard Game Boy resolution.
     */
    public DuckDisplay() {
        this(DuckBackend.instance.Profile().displaySpec());
    }

    /**
     * Constructs a display surface for the supplied backend display spec.
     *
     * @param displaySpec backend display geometry
     */
    public DuckDisplay(EmulatorDisplaySpec displaySpec) {
        super();
        this.displaySpec = displaySpec;
        setBackground(displaySpec == null ? Color.BLACK : displaySpec.backgroundColour());
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
        int frameWidth = frameWidth();
        int frameHeight = frameHeight();
        if (backBuffer != null && x >= 0 && x < frameWidth && y >= 0 && y < frameHeight) {
            backBuffer[(y * frameWidth) + x] = rgb;
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

        RequestRepaint();
    }

    /**
     * Copies the completed emulation back buffer to the image presented on the EDT.
     */
    public void presentFrame() {
        presentFrame(false);
    }

    /**
     * Copies the completed emulation back buffer to the image presented on the EDT.
     *
     * @param blendWithPreviousFrame whether to blend the new frame with the
     * previously displayed image to approximate LCD persistence
     */
    public void presentFrame(boolean blendWithPreviousFrame) {
        if (backBuffer == null || frontBuffer == null) {
            return;
        }

        synchronized (frameLock) {
            if (!blendWithPreviousFrame) {
                System.arraycopy(backBuffer, 0, frontBuffer, 0, backBuffer.length);
            } else {
                for (int index = 0; index < backBuffer.length; index++) {
                    frontBuffer[index] = BlendRgb(frontBuffer[index], backBuffer[index]);
                }
            }
        }

        RequestRepaint();
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

        RequestRepaint();
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
                    getWidth() / (double) frameWidth(),
                    getHeight() / (double) frameHeight());
            int scaledWidth = (int) (frameWidth() * scale);
            int scaledHeight = (int) (frameHeight() * scale);

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
        RequestRepaint();
    }

    /**
     * Returns the minimum size for this component.
     *
     * @return Minimum dimension (100x100)
     */
    @Override
    public Dimension getMinimumSize() {
        return displaySpec == null ? new Dimension(160, 144) : displaySpec.minimumSize();
    }

    /**
     * Returns the preferred size of this component.
     * Based on the size of the parent container, maintaining a square shape.
     *
     * @return Preferred dimension
     */
    @Override
    public Dimension getPreferredSize() {
        return displaySpec == null ? new Dimension(640, 576) : displaySpec.preferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private void initializeBuffers() {
        image = new BufferedImage(frameWidth(), frameHeight(), BufferedImage.TYPE_INT_RGB);
        frontBuffer = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        backBuffer = new int[frameWidth() * frameHeight()];
        Arrays.fill(frontBuffer, Color.BLACK.getRGB());
        Arrays.fill(backBuffer, Color.BLACK.getRGB());
    }

    private int frameWidth() {
        return displaySpec == null ? 160 : displaySpec.frameWidth();
    }

    private int frameHeight() {
        return displaySpec == null ? 144 : displaySpec.frameHeight();
    }

    private void RequestRepaint() {
        if (!repaintQueued.compareAndSet(false, true)) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            repaintQueued.set(false);
            repaint();
        });
    }

    private int BlendRgb(int previousRgb, int currentRgb) {
        int previousRed = (previousRgb >> 16) & 0xFF;
        int previousGreen = (previousRgb >> 8) & 0xFF;
        int previousBlue = previousRgb & 0xFF;

        int currentRed = (currentRgb >> 16) & 0xFF;
        int currentGreen = (currentRgb >> 8) & 0xFF;
        int currentBlue = currentRgb & 0xFF;

        int blendedRed = ((previousRed * 3) + (currentRed * 5)) / 8;
        int blendedGreen = ((previousGreen * 3) + (currentGreen * 5)) / 8;
        int blendedBlue = ((previousBlue * 3) + (currentBlue * 5)) / 8;
        return (blendedRed << 16) | (blendedGreen << 8) | blendedBlue;
    }
}
