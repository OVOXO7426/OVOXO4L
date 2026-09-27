import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/** Static, presentation-only renderer for the scaffold's Sydney layouts. */
public final class PreviewCanvas extends JPanel {
    private static final int MARGIN = 52;
    private PreviewScenario scenario;

    public PreviewCanvas(PreviewScenario scenario) {
        setScenario(scenario);
        setBackground(new Color(0xF5F7FA));
        setPreferredSize(new Dimension(820, 620));
        setMinimumSize(new Dimension(520, 380));
    }

    public void setScenario(PreviewScenario scenario) {
        if (scenario == null) throw new IllegalArgumentException("scenario required");
        this.scenario = scenario;
        repaint();
    }

    PreviewScenario scenario() { return scenario; }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            Bounds bounds = computeBounds();
            drawGrid(g, bounds);
            drawRoutes(g, bounds);
            drawStops(g, bounds);
            drawVehicles(g, bounds);
            drawLegend(g);
        } finally {
            g.dispose();
        }
    }

    private void drawGrid(Graphics2D g, Bounds bounds) {
        g.setColor(new Color(0xE5E9F0));
        g.setStroke(new BasicStroke(1f));
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            int px = screenX(x, bounds);
            g.drawLine(px, MARGIN, px, getHeight() - MARGIN);
        }
        for (int y = bounds.minY; y <= bounds.maxY; y++) {
            int py = screenY(y, bounds);
            g.drawLine(MARGIN, py, getWidth() - MARGIN, py);
        }
    }

    private void drawRoutes(Graphics2D g, Bounds bounds) {
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
        for (PreviewScenario.Route route : scenario.routes()) {
            g.setColor(route.mode() == ServiceMode.TRAIN ? new Color(0x7B1FA2)
                : route.mode() == ServiceMode.TRAM ? new Color(0xE64A19) : new Color(0x00897B));
            for (int i = 1; i < route.stopLabels().size(); i++) {
                PreviewScenario.Point from = scenario.stop(route.stopLabels().get(i - 1)).point();
                PreviewScenario.Point to = scenario.stop(route.stopLabels().get(i)).point();
                int x1 = screenX(from.x(), bounds);
                int y1 = screenY(from.y(), bounds);
                int x2 = screenX(to.x(), bounds);
                int y2 = screenY(to.y(), bounds);
                g.drawLine(x1, y1, x2, y1);
                g.drawLine(x2, y1, x2, y2);
            }
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    private void drawStops(Graphics2D g, Bounds bounds) {
        boolean dense = scenario.stops().size() > 10;
        g.setFont(getFont().deriveFont(Font.BOLD, dense ? 10f : 13f));
        int index = 0;
        for (PreviewScenario.StopPlacement stop : scenario.stops()) {
            int x = screenX(stop.point().x(), bounds);
            int y = screenY(stop.point().y(), bounds);
            if (stop.mode() == ServiceMode.TRAIN) {
                g.setColor(new Color(0xEDE7F6));
                g.fillRect(x - 10, y - 10, 20, 20);
                g.setColor(new Color(0x6A1B9A));
                g.setStroke(new BasicStroke(3f));
                g.drawRect(x - 10, y - 10, 20, 20);
                g.drawLine(x - 6, y + 5, x + 6, y + 5);
            } else if (stop.mode() == ServiceMode.TRAM) {
                int[] xs = { x, x + 11, x, x - 11 };
                int[] ys = { y - 11, y, y + 11, y };
                g.setColor(new Color(0xFBE9E7));
                g.fillPolygon(xs, ys, 4);
                g.setColor(new Color(0xD84315));
                g.setStroke(new BasicStroke(3f));
                g.drawPolygon(xs, ys, 4);
            } else {
                g.setColor(new Color(0xE0F2F1));
                g.fillOval(x - 10, y - 10, 20, 20);
                g.setColor(new Color(0x00796B));
                g.setStroke(new BasicStroke(3f));
                g.drawOval(x - 10, y - 10, 20, 20);
                g.drawLine(x, y + 10, x, y + 16);
            }
            drawLabel(g, stop.label(), x, y,
                dense ? (index++ % 2 == 0 ? -13 : 24) : -13, 14);
        }
    }

    private void drawVehicles(Graphics2D g, Bounds bounds) {
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        int index = 0;
        for (PreviewScenario.VehiclePlacement vehicle : scenario.vehicles()) {
            PreviewScenario.Point point = scenario.stop(vehicle.stopLabel()).point();
            int x = screenX(point.x(), bounds);
            int stopY = screenY(point.y(), bounds);
            int offset = 26 + (index++ % 2) * 30;
            int y = stopY + offset;
            if (y + 16 > getHeight() - 4) y = stopY - offset;
            if (vehicle.mode() == ServiceMode.TRAIN) {
                g.setColor(new Color(0x7B1FA2));
                g.fillRect(x - 22, y - 10, 44, 20);
                g.setColor(new Color(0xD1C4E9));
                for (int windowX = x - 16; windowX <= x + 10; windowX += 13) {
                    g.fillRect(windowX, y - 6, 8, 7);
                }
                g.setColor(new Color(0x311B92));
                g.drawRect(x - 22, y - 10, 44, 20);
            } else if (vehicle.mode() == ServiceMode.TRAM) {
                g.setColor(new Color(0xE64A19));
                g.fillRoundRect(x - 20, y - 9, 40, 18, 12, 12);
                g.setColor(new Color(0xBF360C));
                g.drawRoundRect(x - 20, y - 9, 40, 18, 12, 12);
            } else {
                g.setColor(new Color(0x00897B));
                g.fillRoundRect(x - 15, y - 13, 30, 26, 9, 9);
                g.setColor(new Color(0xB2DFDB));
                g.fillRect(x - 9, y - 8, 18, 8);
                g.setColor(new Color(0x004D40));
                g.drawRoundRect(x - 15, y - 13, 30, 26, 9, 9);
                g.fillOval(x - 10, y + 9, 6, 6);
                g.fillOval(x + 4, y + 9, 6, 6);
            }
            drawLabel(g, vehicle.label(), x, y, 5, 24);
        }
    }

    private void drawLabel(Graphics2D g, String label, int x, int y,
                           int baselineOffset, int horizontalOffset) {
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(label);
        int labelX = x + horizontalOffset;
        if (labelX + width + 5 > getWidth()) labelX = x - width - horizontalOffset;
        int labelY = y + baselineOffset;
        int top = labelY - metrics.getAscent();
        g.setColor(new Color(255, 255, 255, 220));
        g.fillRoundRect(labelX - 3, top - 2, width + 6, metrics.getHeight() + 3, 6, 6);
        g.setColor(new Color(0x263238));
        g.drawString(label, labelX, labelY);
    }

    private void drawLegend(Graphics2D g) {
        g.setFont(getFont().deriveFont(12f));
        g.setColor(new Color(0x00796B));
        g.drawString("○ bus stop", 18, 22);
        g.setColor(new Color(0x6A1B9A));
        g.drawString("□ train station", 96, 22);
        g.setColor(new Color(0xD84315));
        g.drawString("◇ light rail stop", 196, 22);
        g.setColor(new Color(0x1F2933));
        g.drawString("PREVIEW: layout only, nothing moves", 318, 22);
    }

    private Bounds computeBounds() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (PreviewScenario.StopPlacement stop : scenario.stops()) {
            minX = Math.min(minX, stop.point().x());
            minY = Math.min(minY, stop.point().y());
            maxX = Math.max(maxX, stop.point().x());
            maxY = Math.max(maxY, stop.point().y());
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private int screenX(double x, Bounds bounds) {
        double width = Math.max(1, getWidth() - 2.0 * MARGIN);
        return MARGIN + (int) Math.round((x - bounds.minX) * width
            / Math.max(1, bounds.maxX - bounds.minX));
    }

    private int screenY(double y, Bounds bounds) {
        double height = Math.max(1, getHeight() - 2.0 * MARGIN);
        return MARGIN + (int) Math.round((y - bounds.minY) * height
            / Math.max(1, bounds.maxY - bounds.minY));
    }

    private static final class Bounds {
        private final int minX, minY, maxX, maxY;

        Bounds(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }
}
