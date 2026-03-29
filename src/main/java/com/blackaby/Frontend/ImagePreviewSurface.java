package com.blackaby.Frontend;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class ImagePreviewSurface extends JPanel {

    private final String placeholderText;
    private BufferedImage image;
    private BufferedImage scaledImage;
    private int scaledImageWidth = -1;
    private int scaledImageHeight = -1;
    private int scaledImageSourceWidth = -1;
    private int scaledImageSourceHeight = -1;

    ImagePreviewSurface(String placeholderText, int preferredWidth, int preferredHeight) {
        this.placeholderText = placeholderText == null ? "" : placeholderText;
        setOpaque(true);
        setBackground(new Color(234, 241, 248));
        setPreferredSize(new Dimension(preferredWidth, preferredHeight));
    }

    void setImage(BufferedImage image) {
        if (this.image == image) {
            return;
        }
        this.image = image;
        scaledImage = null;
        scaledImageWidth = -1;
        scaledImageHeight = -1;
        scaledImageSourceWidth = -1;
        scaledImageSourceHeight = -1;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D graphics = (Graphics2D) g.create();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        if (image == null) {
            graphics.setColor(Styling.mutedTextColour);
            int textWidth = graphics.getFontMetrics().stringWidth(placeholderText);
            graphics.drawString(placeholderText, Math.max(8, (getWidth() - textWidth) / 2), getHeight() / 2);
            graphics.dispose();
            return;
        }

        BufferedImage previewImage = scaledPreviewImage();
        if (previewImage != null) {
            int x = (getWidth() - previewImage.getWidth()) / 2;
            int y = (getHeight() - previewImage.getHeight()) / 2;
            graphics.drawImage(previewImage, x, y, null);
        }
        graphics.dispose();
    }

    private BufferedImage scaledPreviewImage() {
        if (image == null || getWidth() <= 0 || getHeight() <= 0) {
            return null;
        }

        if (scaledImage != null
                && scaledImageWidth == getWidth()
                && scaledImageHeight == getHeight()
                && scaledImageSourceWidth == image.getWidth()
                && scaledImageSourceHeight == image.getHeight()) {
            return scaledImage;
        }

        double scale = Math.min(getWidth() / (double) image.getWidth(), getHeight() / (double) image.getHeight());
        int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));

        BufferedImage nextScaledImage = new BufferedImage(
                targetWidth,
                targetHeight,
                image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D scaledGraphics = nextScaledImage.createGraphics();
        scaledGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        scaledGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        scaledGraphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        scaledGraphics.dispose();

        scaledImage = nextScaledImage;
        scaledImageWidth = getWidth();
        scaledImageHeight = getHeight();
        scaledImageSourceWidth = image.getWidth();
        scaledImageSourceHeight = image.getHeight();
        return scaledImage;
    }
}
