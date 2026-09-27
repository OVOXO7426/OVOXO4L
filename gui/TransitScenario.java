import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable data for one GUI demonstration. Stops, coordinates, modes, and routes are
 * frontend-only; the backend only ever sees Trip objects and Passenger objects.
 */
public final class TransitScenario {
    private final String id;
    private final String name;
    private final String focus;
    private final int startTick;
    private final TransitSystem system;
    private final List<StopPlacement> stops;
    private final List<VehicleStart> vehicles;
    private final List<BookingCommand> bookings;
    private final Map<String, StopPlacement> stopsByName = new HashMap<>();
    private final String checkCode;

    public TransitScenario(String id, String name, String focus, int startTick,
                           TransitSystem system, List<StopPlacement> stops,
                           List<VehicleStart> vehicles, List<BookingCommand> bookings) {
        if (id == null || id.isBlank() || name == null || name.isBlank()
                || focus == null || focus.isBlank()) {
            throw new IllegalArgumentException("scenario metadata is required");
        }
        if (startTick < 0) throw new IllegalArgumentException("start tick must be >= 0");
        if (system == null || stops == null || vehicles == null || bookings == null) {
            throw new IllegalArgumentException("scenario data is required");
        }
        this.id = id.trim();
        this.name = name.trim();
        this.focus = focus.trim();
        this.startTick = startTick;
        this.system = system;
        this.stops = List.copyOf(stops);
        this.vehicles = List.copyOf(vehicles);
        this.bookings = List.copyOf(bookings);
        validate();
        this.checkCode = computeCheckCode();
    }

    private void validate() {
        if (stops.size() < 2) throw new IllegalArgumentException("a scenario needs at least two stops");
        if (vehicles.isEmpty()) throw new IllegalArgumentException("a scenario needs a vehicle");
        Set<Point> coordinates = new HashSet<>();
        for (StopPlacement placement : stops) {
            if (placement == null) throw new IllegalArgumentException("stop placement required");
            if (stopsByName.put(placement.name(), placement) != null) {
                throw new IllegalArgumentException("duplicate stop name: " + placement.name());
            }
            if (!coordinates.add(placement.point())) {
                throw new IllegalArgumentException("duplicate stop coordinate");
            }
        }
        Set<String> vehicleIds = new HashSet<>();
        Set<ServiceMode> vehicleModes = new HashSet<>();
        for (VehicleStart start : vehicles) {
            StopPlacement placement = start == null ? null : stopsByName.get(start.stopName());
            if (placement == null) {
                throw new IllegalArgumentException("vehicle must start at a configured stop");
            }
            if (start.mode() != placement.mode()) {
                throw new IllegalArgumentException("vehicle mode does not match its stop");
            }
            if (!vehicleIds.add(start.vehicle().id())) {
                throw new IllegalArgumentException("duplicate vehicle id: " + start.vehicle().id());
            }
            vehicleModes.add(start.mode());
        }
        for (BookingCommand booking : bookings) {
            if (booking == null) throw new IllegalArgumentException("booking command required");
            List<String> route = booking.route();
            ServiceMode mode = null;
            int distance = 0;
            for (int i = 0; i < route.size(); i++) {
                StopPlacement stop = stopsByName.get(route.get(i));
                if (stop == null) throw new IllegalArgumentException("route uses an unknown stop");
                if (mode == null) mode = stop.mode();
                if (stop.mode() != mode) {
                    throw new IllegalArgumentException("a route may not mix modes");
                }
                if (i > 0) {
                    distance += stopsByName.get(route.get(i - 1)).point().manhattanDistance(stop.point());
                }
            }
            if (!vehicleModes.contains(mode)) {
                throw new IllegalArgumentException("booking has no " + mode + " vehicle");
            }
            Trip trip = booking.trip();
            if (!trip.origin().equals(route.get(0))
                    || !trip.destination().equals(route.get(route.size() - 1))
                    || trip.distance() != distance) {
                throw new IllegalArgumentException("trip must match its route: " + trip);
            }
        }
    }

    /** A short code derived from the scenario data. Tutors compare it with the published value. */
    private String computeCheckCode() {
        StringBuilder text = new StringBuilder();
        text.append(id).append('|').append(startTick);
        for (StopPlacement stop : stops) {
            text.append("|S:").append(stop.name()).append(',').append(stop.point().x())
                .append(',').append(stop.point().y()).append(',').append(stop.mode());
        }
        for (VehicleStart start : vehicles) {
            text.append("|V:").append(start.vehicle().id()).append(',')
                .append(start.vehicle().getClass().getSimpleName()).append(',')
                .append(start.stopName());
        }
        for (BookingCommand booking : bookings) {
            text.append("|B:").append(booking.releaseStep()).append(',')
                .append(booking.passenger().name()).append(',')
                .append(booking.label()).append(',')
                .append(String.join(">", booking.route()));
        }
        int hash = 0x811C9DC5;
        String data = text.toString();
        for (int i = 0; i < data.length(); i++) {
            hash ^= data.charAt(i);
            hash *= 0x01000193;
        }
        return String.format("%08X", hash);
    }

    public String id() { return id; }
    public String name() { return name; }
    public String focus() { return focus; }
    public int startTick() { return startTick; }
    public TransitSystem system() { return system; }
    public List<StopPlacement> stops() { return stops; }
    public List<VehicleStart> vehicles() { return vehicles; }
    public List<BookingCommand> bookings() { return bookings; }
    public String checkCode() { return checkCode; }

    public Point pointOf(String stopName) {
        StopPlacement placement = stopsByName.get(stopName);
        if (placement == null) throw new IllegalArgumentException("unknown stop: " + stopName);
        return placement.point();
    }

    public ServiceMode modeOf(BookingCommand booking) {
        return stopsByName.get(booking.route().get(0)).mode();
    }

    public static final class Point {
        private final int x;
        private final int y;

        public Point(int x, int y) { this.x = x; this.y = y; }
        public int x() { return x; }
        public int y() { return y; }
        public int manhattanDistance(Point other) {
            if (other == null) throw new IllegalArgumentException("point required");
            return Math.abs(x - other.x) + Math.abs(y - other.y);
        }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Point point = (Point) other;
            return x == point.x && y == point.y;
        }
        @Override public int hashCode() { return 31 * x + y; }
    }

    public static final class StopPlacement {
        private final String name;
        private final Point point;
        private final ServiceMode mode;
        public StopPlacement(String name, Point point, ServiceMode mode) {
            if (name == null || name.isBlank() || point == null || mode == null) {
                throw new IllegalArgumentException("stop name, point and mode required");
            }
            this.name = name.trim();
            this.point = point;
            this.mode = mode;
        }
        public String name() { return name; }
        public Point point() { return point; }
        public ServiceMode mode() { return mode; }
    }

    public static final class VehicleStart {
        private final Vehicle vehicle;
        private final String stopName;
        private final ServiceMode mode;
        public VehicleStart(Vehicle vehicle, String stopName, ServiceMode mode) {
            if (vehicle == null || stopName == null || mode == null) {
                throw new IllegalArgumentException("vehicle, stop and mode required");
            }
            this.vehicle = vehicle;
            this.stopName = stopName;
            this.mode = mode;
        }
        public Vehicle vehicle() { return vehicle; }
        public String stopName() { return stopName; }
        public ServiceMode mode() { return mode; }
    }

    /**
     * One booking released at a playback step. The route is the GUI's drawing path only. The
     * label describes the passenger as the scenario data created it, e.g. "concession A, age 58",
     * so the GUI never needs to ask the Passenger object what kind it is.
     */
    public static final class BookingCommand {
        private final int releaseStep;
        private final Passenger passenger;
        private final String label;
        private final List<String> route;
        private final Trip trip;
        public BookingCommand(int releaseStep, Passenger passenger, List<String> route, Trip trip) {
            this(releaseStep, passenger, defaultLabel(passenger), route, trip);
        }
        private static String defaultLabel(Passenger passenger) {
            if (passenger == null) return null;
            String kind = passenger.getClass().getSimpleName().replace("Passenger", "").toLowerCase();
            return kind.isEmpty() ? "passenger" : kind;
        }
        public BookingCommand(int releaseStep, Passenger passenger, String label, List<String> route,
                              Trip trip) {
            if (releaseStep < 0) throw new IllegalArgumentException("release step must be >= 0");
            if (passenger == null || label == null || label.isBlank() || route == null
                    || route.size() < 2 || trip == null) {
                throw new IllegalArgumentException("passenger, label, route and trip required");
            }
            this.releaseStep = releaseStep;
            this.passenger = passenger;
            this.label = label;
            this.route = List.copyOf(new ArrayList<>(route));
            this.trip = trip;
        }
        public int releaseStep() { return releaseStep; }
        public Passenger passenger() { return passenger; }
        public String label() { return label; }
        public List<String> route() { return route; }
        public Trip trip() { return trip; }
    }
}
