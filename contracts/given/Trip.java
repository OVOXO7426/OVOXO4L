// SUPPLIED - do not modify or submit.
public final class Trip {
    private final String origin;
    private final String destination;
    private final int distance;

    public Trip(String origin, String destination, int distance) {
        if (origin == null || origin.isBlank() || destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("origin and destination are required");
        }
        if (origin.trim().equals(destination.trim())) {
            throw new IllegalArgumentException("origin and destination must differ");
        }
        if (distance < 1) {
            throw new IllegalArgumentException("distance must be at least 1");
        }
        this.origin = origin.trim();
        this.destination = destination.trim();
        this.distance = distance;
    }

    public String origin() { return origin; }
    public String destination() { return destination; }
    public int distance() { return distance; }

    @Override public String toString() {
        return origin + " -> " + destination + " (" + distance + ")";
    }
}
