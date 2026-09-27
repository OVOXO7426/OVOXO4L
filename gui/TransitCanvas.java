import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;

/** Pure Swing renderer for an already-computed playback view. */
public final class TransitCanvas extends JPanel {
    private static final int MARGIN = 52;
    private TransitPlayback playback;

    public TransitCanvas(TransitPlayback playback) {
        setPlayback(playback);
        setBackground(new Color(0xF5F7FA));
        setPreferredSize(new java.awt.Dimension(820, 620));
        setMinimumSize(new java.awt.Dimension(520, 380));
    }

    public void setPlayback(TransitPlayback playback) {
        if (playback == null) throw new IllegalArgumentException("playback required");
        this.playback = playback;
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Bounds bounds = computeBounds();
            drawGrid(g, bounds);
            drawRoads(g, bounds);
            drawStops(g, bounds);
            drawStoppedPassengers(g, bounds);
            drawVehicles(g, bounds);
            drawRidingPassengers(g, bounds);
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

    private void drawRoads(Graphics2D g, Bounds bounds) {
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (TransitPlayback.RoadView road : playback.roads(System.nanoTime())) {
            float opacity = (float) Math.max(0, Math.min(1, road.opacity()));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            g.setColor(new Color(0x4C78A8));
            g.drawLine(screenX(road.x1(), bounds), screenY(road.y1(), bounds),
                    screenX(road.x2(), bounds), screenY(road.y2(), bounds));
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    private void drawStops(Graphics2D g, Bounds bounds) {
        boolean dense = playback.stops().size() > 10;
        g.setFont(getFont().deriveFont(Font.BOLD, dense ? 10f : 13f));
        int index = 0;
        for (TransitPlayback.StopView stop : playback.stops()) {
            int x = screenX(stop.x(), bounds);
            int y = screenY(stop.y(), bounds);
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
            drawStopLabel(g, stop.label(), x, y, index++, dense);
        }
    }

    private void drawStopLabel(Graphics2D g, String label, int x, int y,
                               int index, boolean dense) {
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(label);
        int labelX = x + 14;
        if (labelX + width + 5 > getWidth()) labelX = x - width - 14;
        int labelY = dense ? y + (index % 2 == 0 ? -13 : 24) : y - 13;
        int top = labelY - metrics.getAscent();
        g.setColor(new Color(255, 255, 255, 220));
        g.fillRoundRect(labelX - 3, top - 2, width + 6, metrics.getHeight() + 3, 6, 6);
        g.setColor(new Color(0x263238));
        g.drawString(label, labelX, labelY);
    }

    private void drawStoppedPassengers(Graphics2D g, Bounds bounds) {
        Map<String, Integer> stackCounts = new HashMap<>();
        for (TransitPlayback.PassengerView passenger : playback.passengers()) {
            if (passenger.state() == TransitPlayback.PassengerState.RIDING) continue;
            String key = passenger.state() + ":" + passenger.x() + ":" + passenger.y();
            int index = stackCounts.getOrDefault(key, 0);
            stackCounts.put(key, index + 1);
            int column = index % 5;
            int row = index / 5;
            int x = screenX(passenger.x(), bounds) - 16 + column * 8;
            int y = screenY(passenger.y(), bounds) + 18 + row * 8;
            g.setColor(passenger.state() == TransitPlayback.PassengerState.WAITING
                    ? new Color(0xF9A825) : new Color(0x2E7D32));
            g.fillOval(x - 4, y - 4, 8, 8);
            g.setColor(Color.WHITE);
            g.drawOval(x - 4, y - 4, 8, 8);
        }
    }

    private void drawVehicles(Graphics2D g, Bounds bounds) {
        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        for (TransitPlayback.VehicleView vehicle : playback.vehicles()) {
            int x = screenX(vehicle.x(), bounds);
            int y = screenY(vehicle.y(), bounds);
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
                g.setColor(new Color(0xFFCCBC));
                g.fillRect(x - 14, y - 5, 28, 6);
                g.setColor(new Color(0xBF360C));
                g.drawRoundRect(x - 20, y - 9, 40, 18, 12, 12);
                g.drawLine(x, y - 9, x, y - 15);
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
            g.setColor(statusColour(vehicle.status()));
            g.fillRect(x - 8, y + 3, 16, 3);
            String text = vehicle.label() + " (" + vehicle.passengerCount() + ") "
                    + vehicle.status() + " @" + (int) vehicle.position();
            FontMetrics metrics = g.getFontMetrics();
            int width = metrics.stringWidth(text);
            int textX = x + 24 + width + 4 > getWidth() ? x - 24 - width : x + 24;
            g.setColor(new Color(255, 255, 255, 210));
            g.fillRoundRect(textX - 3, y - metrics.getAscent() + 3, width + 6,
                    metrics.getHeight() + 2, 6, 6);
            g.setColor(new Color(0x1F2933));
            g.drawString(text, textX, y + 5);
        }
    }

    static Color statusColour(VehicleStatus status) {
        if (status == VehicleStatus.AT_STOP) return new Color(0x1565C0);
        if (status == VehicleStatus.APPROACHING) return new Color(0xEF6C00);
        return new Color(0x2E7D32);
    }

    private void drawRidingPassengers(Graphics2D g, Bounds bounds) {
        Map<String, Integer> riders = new HashMap<>();
        for (TransitPlayback.PassengerView passenger : playback.passengers()) {
            if (passenger.state() != TransitPlayback.PassengerState.RIDING) continue;
            String key = passenger.x() + ":" + passenger.y();
            int index = riders.getOrDefault(key, 0);
            riders.put(key, index + 1);
            double angle = index * 2.39996;
            int radius = 16 + index / 8 * 5;
            int x = screenX(passenger.x(), bounds) + (int) (Math.cos(angle) * radius);
            int y = screenY(passenger.y(), bounds) + (int) (Math.sin(angle) * radius);
            g.setColor(new Color(0x7E57C2));
            g.fillOval(x - 3, y - 3, 7, 7);
        }
    }

    private void drawLegend(Graphics2D g) {
        g.setFont(getFont().deriveFont(12f));
        g.setColor(new Color(0x00796B));
        g.drawString("○ bus stop", 18, 22);
        g.setColor(new Color(0x6A1B9A));
        g.drawString("□ train station", 96, 22);
        g.setColor(new Color(0xD84315));
        g.drawString("◇ light rail stop", 196, 22);
        g.setColor(new Color(0xF9A825));
        g.drawString("● waiting", 318, 22);
        g.setColor(new Color(0x7E57C2));
        g.drawString("● riding", 392, 22);
        g.setColor(new Color(0x2E7D32));
        g.drawString("● arrived", 460, 22);
        g.setColor(new Color(0x1F2933));
        g.drawString("vehicle: id (riders) status @position", 540, 22);
    }

    private Bounds computeBounds() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (TransitPlayback.StopView stop : playback.stops()) {
            minX = Math.min(minX, (int) stop.x());
            minY = Math.min(minY, (int) stop.y());
            maxX = Math.max(maxX, (int) stop.x());
            maxY = Math.max(maxY, (int) stop.y());
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
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
        }
    }
}
