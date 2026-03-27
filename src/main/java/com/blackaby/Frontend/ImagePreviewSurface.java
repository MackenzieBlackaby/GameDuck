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

    ImagePreviewSurface(String placeholderText, int preferredWidth, int preferredHeight) {
        this.placeholderText = placeholderText == null ? "" : placeholderText;
        setOpaque(true);
        setBackground(new Color(234, 241, 248));
        setPreferredSize(new Dimension(preferredWidth, preferredHeight));
    }

    void setImage(BufferedImage image) {
        this.image = image;
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

        double scale = Math.min(getWidth() / (double) image.getWidth(), getHeight() / (double) image.getHeight());
        int scaledWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int scaledHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        int x = (getWidth() - scaledWidth) / 2;
        int y = (getHeight() - scaledHeight) / 2;
        graphics.drawImage(image, x, y, scaledWidth, scaledHeight, null);
        graphics.dispose();
    }
}
