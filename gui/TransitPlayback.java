import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Headless controller for the GUI. The backend is authoritative for bookings, fares, the clock,
 * vehicle positions and statuses, tickets, and revenue. This class only turns those results into
 * 2D presentation state: which vehicle carries which passenger, and where things are drawn.
 */
public final class TransitPlayback {
    public enum PassengerState { WAITING, RIDING, ARRIVED }

    private static final long ROAD_FADE_NANOS = 20_000_000_000L;
    private static final double ROAD_RESTING_OPACITY = 0.2;
    private static final double EPSILON = 0.000_001;

    private final TransitScenario scenario;
    private final List<StopView> stopViews = new ArrayList<>();
    private final List<VehicleState> vehicleStates = new ArrayList<>();
    private final List<Journey> journeys = new ArrayList<>();
    private final List<String> logLines = new ArrayList<>();
    private final Map<RoadKey, Long> roadLastUsed = new LinkedHashMap<>();
    private final boolean[] bookingProcessed;

    private boolean running;
    private String errorMessage = "";
    private int stepNumber;
    private int processedBookings;
    private List<Ticket> tickets = List.of();
    private long revenueCents;
    private int currentTick;
    private int day;
    private boolean holiday;

    public TransitPlayback(TransitScenario scenario) {
        if (scenario == null) throw new IllegalArgumentException("scenario required");
        this.scenario = scenario;
        bookingProcessed = new boolean[scenario.bookings().size()];
        for (TransitScenario.StopPlacement placement : scenario.stops()) {
            stopViews.add(new StopView(placement.name(), placement.point().x(),
                    placement.point().y(), placement.mode()));
        }
        for (TransitScenario.VehicleStart start : scenario.vehicles()) {
            TransitScenario.Point point = scenario.pointOf(start.stopName());
            vehicleStates.add(new VehicleState(start.vehicle(), point.x(), point.y(), start.mode()));
        }
        logLines.add("Scenario " + scenario.name() + " (check " + scenario.checkCode() + ")");
        refreshBackendView();
    }

    public void start() {
        if (errorMessage.isEmpty() && !complete()) running = true;
    }

    public void pause() { running = false; }
    public boolean running() { return running; }

    /** One playback step: release due bookings, call tick() once, then animate. */
    public void step(long nowNanos) {
        if (!running || complete()) return;
        try {
            assignWaitingJourneys();
            releaseBookings();
            scenario.system().tick();
            for (VehicleState vehicle : vehicleStates) {
                double position = vehicle.vehicle.position();
                double distance = position - vehicle.lastPosition;
                vehicle.lastPosition = position;
                if (distance < -EPSILON) {
                    fail("Vehicle " + vehicle.label + " moved backwards. Playback paused.");
                    return;
                }
                if (vehicle.run != null) {
                    vehicle.movementCredit += distance;
                    moveVehicle(vehicle, nowNanos);
                } else {
                    vehicle.movementCredit = 0;
                }
            }
        } catch (RuntimeException backendError) {
            fail("Backend error: " + describe(backendError) + ". Playback paused.");
            return;
        }
        stepNumber++;
        refreshBackendView();
        if (complete()) running = false;
    }

    private void refreshBackendView() {
        try {
            TransitSystem system = scenario.system();
            tickets = List.copyOf(system.tickets());
            revenueCents = system.revenueCents();
            currentTick = system.currentTick();
            day = system.day();
            holiday = system.isHoliday();
        } catch (RuntimeException backendError) {
            fail("Backend error: " + describe(backendError) + ". Playback paused.");
        }
    }

    private void assignWaitingJourneys() {
        for (Journey first : journeys) {
            if (first.state != PassengerState.WAITING || first.assigned != null) continue;
            VehicleState vehicle = nearestIdleVehicle(first.origin(), first.mode);
            if (vehicle == null) continue;
            List<Journey> group = new ArrayList<>();
            for (Journey candidate : journeys) {
                if (candidate.state == PassengerState.WAITING && candidate.assigned == null
                        && candidate.command.route().equals(first.command.route())) {
                    group.add(candidate);
                }
            }
            assign(vehicle, group);
        }
    }

    private VehicleState nearestIdleVehicle(String origin, ServiceMode mode) {
        TransitScenario.Point point = scenario.pointOf(origin);
        VehicleState best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (VehicleState candidate : vehicleStates) {
            if (candidate.run != null || candidate.mode != mode) continue;
            int distance = Math.abs(candidate.x - point.x()) + Math.abs(candidate.y - point.y());
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void assign(VehicleState vehicle, List<Journey> group) {
        Journey first = group.get(0);
        List<GridPoint> path = new ArrayList<>();
        GridPoint cursor = new GridPoint(vehicle.x, vehicle.y);
        TransitScenario.Point origin = scenario.pointOf(first.origin());
        cursor = appendManhattan(path, cursor, origin.x(), origin.y());
        int approachEdges = path.size();
        List<String> route = first.command.route();
        for (int i = 1; i < route.size(); i++) {
            TransitScenario.Point next = scenario.pointOf(route.get(i));
            cursor = appendManhattan(path, cursor, next.x(), next.y());
        }
        Run run = new Run(group, path, approachEdges);
        vehicle.run = run;
        for (Journey journey : group) journey.assigned = vehicle;
        if (approachEdges == 0) board(run);
        logLines.add(vehicle.label + " picks up " + group.size() + " passenger(s) at "
                + first.origin());
    }

    private GridPoint appendManhattan(List<GridPoint> path, GridPoint start, int endX, int endY) {
        int x = start.x;
        int y = start.y;
        boolean horizontalFirst = x < endX || x == endX && y <= endY;
        if (horizontalFirst) {
            while (x != endX) { x += Integer.compare(endX, x); path.add(new GridPoint(x, y)); }
            while (y != endY) { y += Integer.compare(endY, y); path.add(new GridPoint(x, y)); }
        } else {
            while (y != endY) { y += Integer.compare(endY, y); path.add(new GridPoint(x, y)); }
            while (x != endX) { x += Integer.compare(endX, x); path.add(new GridPoint(x, y)); }
        }
        return new GridPoint(x, y);
    }

    private void moveVehicle(VehicleState vehicle, long nowNanos) {
        Run run = vehicle.run;
        while (run != null && vehicle.movementCredit + EPSILON >= 1.0
                && run.nextEdge < run.path.size()) {
            GridPoint from = new GridPoint(vehicle.x, vehicle.y);
            GridPoint to = run.path.get(run.nextEdge++);
            vehicle.x = to.x;
            vehicle.y = to.y;
            vehicle.movementCredit -= 1.0;
            roadLastUsed.put(new RoadKey(from, to), nowNanos);
            if (!run.boarded && run.nextEdge >= run.approachEdges) board(run);
            if (run.nextEdge == run.path.size()) {
                arrive(run);
                vehicle.run = null;
                vehicle.movementCredit = 0;
                run = null;
            }
        }
        if (run != null && run.path.isEmpty()) {
            arrive(run);
            vehicle.run = null;
            vehicle.movementCredit = 0;
        }
    }

    private void board(Run run) {
        run.boarded = true;
        for (Journey journey : run.journeys) journey.state = PassengerState.RIDING;
    }

    private void arrive(Run run) {
        for (Journey journey : run.journeys) {
            journey.state = PassengerState.ARRIVED;
            logLines.add(journey.ticket.passenger().name() + " arrived at " + journey.destination());
        }
    }

    private void releaseBookings() {
        for (int i = 0; i < scenario.bookings().size(); i++) {
            TransitScenario.BookingCommand command = scenario.bookings().get(i);
            if (bookingProcessed[i] || command.releaseStep() > stepNumber) continue;
            bookingProcessed[i] = true;
            processedBookings++;
            try {
                Ticket ticket = scenario.system().book(command.passenger(), command.trip());
                journeys.add(new Journey(ticket, command, scenario.modeOf(command)));
                logLines.add(ticket.id() + " " + ticket.passenger().name() + " "
                        + ticket.trip().origin() + " -> " + ticket.trip().destination() + ": "
                        + money(ticket.finalFareCents()) + " (" + dayRule(ticket) + ")");
            } catch (IllegalArgumentException rejected) {
                logLines.add(command.passenger().name() + " booking rejected: " + describe(rejected));
            }
        }
    }

    /** Plain-language description of the day stage, derived from the ticket's own values. */
    public static String dayRule(Ticket ticket) {
        if (!ticket.holiday()) return "normal day, full fare";
        if (ticket.dayFareCents() == 0) return "holiday, later trip today, free";
        return "holiday, first trip today, half price";
    }

    public static String money(long cents) { return String.format("$%d.%02d", cents / 100, cents % 100); }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private void fail(String message) {
        errorMessage = message;
        logLines.add("ERROR: " + message);
        running = false;
    }

    public boolean complete() {
        if (processedBookings != scenario.bookings().size()) return false;
        for (Journey journey : journeys) {
            if (journey.state != PassengerState.ARRIVED) return false;
        }
        return true;
    }

    public TransitScenario scenario() { return scenario; }
    public String errorMessage() { return errorMessage; }
    public List<String> logLines() { return List.copyOf(logLines); }
    public List<StopView> stops() { return List.copyOf(stopViews); }
    public int stepNumber() { return stepNumber; }
    public int currentTick() { return currentTick; }
    public int day() { return day; }
    public boolean holiday() { return holiday; }
    public List<Ticket> tickets() { return tickets; }
    public int ticketCount() { return tickets.size(); }
    public long revenueCents() { return revenueCents; }

    public int arrivedCount() {
        int count = 0;
        for (Journey journey : journeys) if (journey.state == PassengerState.ARRIVED) count++;
        return count;
    }

    public long holidaySavingsCents() {
        long total = 0;
        for (Ticket ticket : tickets) total += ticket.dayDiscountCents();
        return total;
    }

    public long passengerSavingsCents() {
        long total = 0;
        for (Ticket ticket : tickets) total += ticket.passengerDiscountCents();
        return total;
    }

    /** One line per passenger, e.g. "Passenger 1 (adult, 2 trips on holiday): paid $4.50". */
    public List<String> passengerSummaries() {
        Map<Passenger, int[]> counts = new IdentityHashMap<>();
        Map<Passenger, Long> paid = new IdentityHashMap<>();
        List<Passenger> order = new ArrayList<>();
        for (Ticket ticket : tickets) {
            int[] c = counts.get(ticket.passenger());
            if (c == null) {
                c = new int[2];
                counts.put(ticket.passenger(), c);
                paid.put(ticket.passenger(), 0L);
                order.add(ticket.passenger());
            }
            if (ticket.holiday()) c[0]++; else c[1]++;
            paid.put(ticket.passenger(), paid.get(ticket.passenger()) + ticket.finalFareCents());
        }
        List<String> lines = new ArrayList<>();
        for (Passenger passenger : order) {
            int[] c = counts.get(passenger);
            lines.add(passenger.name() + " (" + labelOf(passenger) + ", " + tripText(c[0], c[1])
                    + "): paid " + money(paid.get(passenger)));
        }
        return lines;
    }

    /** How the scenario data described this passenger, e.g. "adult" or "concession A, age 58". */
    public String labelOf(Passenger passenger) {
        for (TransitScenario.BookingCommand command : scenario.bookings()) {
            if (command.passenger() == passenger) return command.label();
        }
        return passenger.name();
    }

    private static String tripText(int holidayTrips, int normalTrips) {
        String holidayPart = holidayTrips == 1 ? "single trip on holiday"
                : holidayTrips + " trips on holiday";
        String normalPart = normalTrips == 1 ? "single trip on a normal day"
                : normalTrips + " trips on normal days";
        if (normalTrips == 0) return holidayPart;
        if (holidayTrips == 0) return normalPart;
        return holidayTrips + (holidayTrips == 1 ? " trip" : " trips") + " on holiday, "
                + normalTrips + (normalTrips == 1 ? " on a normal day" : " on normal days");
    }

    public List<PassengerView> passengers() {
        List<PassengerView> result = new ArrayList<>();
        for (Journey journey : journeys) {
            double x;
            double y;
            if (journey.state == PassengerState.WAITING) {
                TransitScenario.Point origin = scenario.pointOf(journey.origin());
                x = origin.x();
                y = origin.y();
            } else if (journey.state == PassengerState.RIDING) {
                x = journey.assigned.x;
                y = journey.assigned.y;
            } else {
                TransitScenario.Point destination = scenario.pointOf(journey.destination());
                x = destination.x();
                y = destination.y();
            }
            result.add(new PassengerView(journey.ticket.passenger().name(), x, y,
                    journey.state, journey.ticket.id()));
        }
        return List.copyOf(result);
    }

    public List<VehicleView> vehicles() {
        List<VehicleView> result = new ArrayList<>();
        for (VehicleState vehicle : vehicleStates) {
            int riders = vehicle.run == null ? 0 : vehicle.run.journeys.size();
            result.add(new VehicleView(vehicle.label, vehicle.x, vehicle.y, vehicle.lastPosition,
                    vehicle.vehicle.status(), riders, vehicle.mode));
        }
        return List.copyOf(result);
    }

    public List<RoadView> roads(long nowNanos) {
        List<RoadView> result = new ArrayList<>();
        for (Map.Entry<RoadKey, Long> entry : roadLastUsed.entrySet()) {
            double age = Math.max(0, nowNanos - entry.getValue());
            double fadeProgress = Math.min(1.0, age / ROAD_FADE_NANOS);
            double opacity = 1.0 - (1.0 - ROAD_RESTING_OPACITY) * fadeProgress;
            RoadKey road = entry.getKey();
            result.add(new RoadView(road.a.x, road.a.y, road.b.x, road.b.y, opacity));
        }
        return List.copyOf(result);
    }

    boolean hasFadingRoads(long nowNanos) {
        for (long lastUsed : roadLastUsed.values()) {
            if (Math.max(0, nowNanos - lastUsed) < ROAD_FADE_NANOS) return true;
        }
        return false;
    }

    public static final class StopView {
        private final String label;
        private final double x;
        private final double y;
        private final ServiceMode mode;
        StopView(String label, double x, double y, ServiceMode mode) {
            this.label = label; this.x = x; this.y = y; this.mode = mode;
        }
        public String label() { return label; }
        public double x() { return x; }
        public double y() { return y; }
        public ServiceMode mode() { return mode; }
    }

    public static final class PassengerView {
        private final String name;
        private final double x;
        private final double y;
        private final PassengerState state;
        private final String ticketId;
        PassengerView(String name, double x, double y, PassengerState state, String ticketId) {
            this.name = name; this.x = x; this.y = y; this.state = state; this.ticketId = ticketId;
        }
        public String name() { return name; }
        public double x() { return x; }
        public double y() { return y; }
        public PassengerState state() { return state; }
        public String ticketId() { return ticketId; }
    }

    public static final class VehicleView {
        private final String label;
        private final double x;
        private final double y;
        private final double position;
        private final VehicleStatus status;
        private final int passengerCount;
        private final ServiceMode mode;
        VehicleView(String label, double x, double y, double position, VehicleStatus status,
                    int passengerCount, ServiceMode mode) {
            this.label = label; this.x = x; this.y = y; this.position = position;
            this.status = status; this.passengerCount = passengerCount; this.mode = mode;
        }
        public String label() { return label; }
        public double x() { return x; }
        public double y() { return y; }
        /** The backend's Vehicle.position(), unchanged. */
        public double position() { return position; }
        /** The backend's Vehicle.status(), unchanged. */
        public VehicleStatus status() { return status; }
        public int passengerCount() { return passengerCount; }
        public ServiceMode mode() { return mode; }
    }

    public static final class RoadView {
        private final double x1;
        private final double y1;
        private final double x2;
        private final double y2;
        private final double opacity;
        RoadView(double x1, double y1, double x2, double y2, double opacity) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.opacity = opacity;
        }
        public double x1() { return x1; }
        public double y1() { return y1; }
        public double x2() { return x2; }
        public double y2() { return y2; }
        public double opacity() { return opacity; }
    }

    private static final class Journey {
        private final Ticket ticket;
        private final TransitScenario.BookingCommand command;
        private final ServiceMode mode;
        private PassengerState state = PassengerState.WAITING;
        private VehicleState assigned;
        Journey(Ticket ticket, TransitScenario.BookingCommand command, ServiceMode mode) {
            this.ticket = ticket; this.command = command; this.mode = mode;
        }
        String origin() { return command.route().get(0); }
        String destination() { return command.route().get(command.route().size() - 1); }
    }

    private static final class VehicleState {
        private final Vehicle vehicle;
        private final String label;
        private final ServiceMode mode;
        private int x;
        private int y;
        private double lastPosition;
        private double movementCredit;
        private Run run;
        VehicleState(Vehicle vehicle, int x, int y, ServiceMode mode) {
            this.vehicle = vehicle;
            this.label = vehicle.id();
            this.x = x;
            this.y = y;
            this.mode = mode;
            this.lastPosition = vehicle.position();
        }
    }

    private static final class Run {
        private final List<Journey> journeys;
        private final List<GridPoint> path;
        private final int approachEdges;
        private int nextEdge;
        private boolean boarded;
        Run(List<Journey> journeys, List<GridPoint> path, int approachEdges) {
            this.journeys = List.copyOf(journeys);
            this.path = List.copyOf(path);
            this.approachEdges = approachEdges;
        }
    }

    private static final class GridPoint {
        private final int x;
        private final int y;
        GridPoint(int x, int y) { this.x = x; this.y = y; }
    }

    private static final class RoadKey {
        private final GridPoint a;
        private final GridPoint b;
        RoadKey(GridPoint first, GridPoint second) {
            if (first.x < second.x || first.x == second.x && first.y <= second.y) {
                a = first; b = second;
            } else {
                a = second; b = first;
            }
        }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            RoadKey road = (RoadKey) other;
            return a.x == road.a.x && a.y == road.a.y && b.x == road.b.x && b.y == road.b.y;
        }
        @Override public int hashCode() {
            int result = 31 * a.x + a.y;
            result = 31 * result + b.x;
            return 31 * result + b.y;
        }
    }
}
