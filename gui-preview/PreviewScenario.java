import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable presentation-only data for one scaffold preview layout. */
public final class PreviewScenario {
    private final String id;
    private final String name;
    private final String description;
    private final List<StopPlacement> stops;
    private final List<VehiclePlacement> vehicles;
    private final List<Route> routes;
    private final Map<String, StopPlacement> stopsByLabel;

    public PreviewScenario(String id, String name, String description,
            List<StopPlacement> stops, List<VehiclePlacement> vehicles,
            List<Route> routes) {
        if (id == null || id.isBlank() || name == null || name.isBlank()
                || description == null || description.isBlank()) {
            throw new IllegalArgumentException("preview scenario metadata is required");
        }
        if (stops == null || vehicles == null || routes == null) {
            throw new IllegalArgumentException("preview scenario data is required");
        }
        this.id = id.trim();
        this.name = name.trim();
        this.description = description.trim();
        this.stops = List.copyOf(stops);
        this.vehicles = List.copyOf(vehicles);
        this.routes = List.copyOf(routes);
        this.stopsByLabel = validate();
    }

    private Map<String, StopPlacement> validate() {
        if (stops.size() < 2) {
            throw new IllegalArgumentException("a preview scenario needs at least two stops");
        }
        Map<String, StopPlacement> byLabel = new HashMap<>();
        Set<Point> points = new HashSet<>();
        for (StopPlacement stop : stops) {
            if (stop == null) throw new IllegalArgumentException("preview stop required");
            if (byLabel.put(stop.label(), stop) != null) {
                throw new IllegalArgumentException("duplicate preview stop label: " + stop.label());
            }
            if (!points.add(stop.point())) {
                throw new IllegalArgumentException("duplicate preview stop coordinate");
            }
        }

        for (VehiclePlacement vehicle : vehicles) {
            if (vehicle == null) throw new IllegalArgumentException("preview vehicle required");
            StopPlacement stop = byLabel.get(vehicle.stopLabel());
            if (stop == null) {
                throw new IllegalArgumentException(
                    "preview vehicle uses an unknown stop: " + vehicle.stopLabel());
            }
            if (stop.mode() != vehicle.mode()) {
                throw new IllegalArgumentException(
                    "preview vehicle mode does not match stop: " + vehicle.stopLabel());
            }
        }

        for (Route route : routes) {
            if (route == null) throw new IllegalArgumentException("preview route required");
            for (String label : route.stopLabels()) {
                StopPlacement stop = byLabel.get(label);
                if (stop == null) {
                    throw new IllegalArgumentException(
                        "preview route uses an unknown stop: " + label);
                }
                if (stop.mode() != route.mode()) {
                    throw new IllegalArgumentException(
                        "preview route mode does not match stop: " + label);
                }
            }
        }
        return Map.copyOf(byLabel);
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public List<StopPlacement> stops() { return stops; }
    public List<VehiclePlacement> vehicles() { return vehicles; }
    public List<Route> routes() { return routes; }

    public StopPlacement stop(String label) {
        if (label == null || !stopsByLabel.containsKey(label)) {
            throw new IllegalArgumentException("unknown preview stop: " + label);
        }
        return stopsByLabel.get(label);
    }

    public static final class Point {
        private final int x;
        private final int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int x() { return x; }
        public int y() { return y; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Point point = (Point) other;
            return x == point.x && y == point.y;
        }

        @Override public int hashCode() { return 31 * x + y; }
    }

    public static final class StopPlacement {
        private final String label;
        private final Point point;
        private final ServiceMode mode;

        public StopPlacement(String label, Point point, ServiceMode mode) {
            if (label == null || label.isBlank() || point == null || mode == null) {
                throw new IllegalArgumentException("preview stop label, point and mode required");
            }
            this.label = label.trim();
            this.point = point;
            this.mode = mode;
        }

        public String label() { return label; }
        public Point point() { return point; }
        public ServiceMode mode() { return mode; }
    }

    public static final class VehiclePlacement {
        private final String label;
        private final String stopLabel;
        private final ServiceMode mode;

        public VehiclePlacement(String label, String stopLabel, ServiceMode mode) {
            if (label == null || label.isBlank() || stopLabel == null || stopLabel.isBlank()
                    || mode == null) {
                throw new IllegalArgumentException(
                    "preview vehicle label, stop label and mode required");
            }
            this.label = label.trim();
            this.stopLabel = stopLabel.trim();
            this.mode = mode;
        }

        public String label() { return label; }
        public String stopLabel() { return stopLabel; }
        public ServiceMode mode() { return mode; }
    }

    public static final class Route {
        private final List<String> stopLabels;
        private final ServiceMode mode;

        public Route(List<String> stopLabels, ServiceMode mode) {
            if (stopLabels == null || mode == null || stopLabels.size() < 2) {
                throw new IllegalArgumentException(
                    "a preview route needs at least two stop labels and a mode");
            }
            List<String> normalized = new ArrayList<>();
            for (String label : stopLabels) {
                if (label == null || label.isBlank()) {
                    throw new IllegalArgumentException("preview route stop label required");
                }
                normalized.add(label.trim());
            }
            this.stopLabels = List.copyOf(normalized);
            this.mode = mode;
        }

        public List<String> stopLabels() { return stopLabels; }
        public ServiceMode mode() { return mode; }
    }
}
