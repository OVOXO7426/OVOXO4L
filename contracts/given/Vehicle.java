// SUPPLIED - do not modify or submit.
public abstract class Vehicle {
    protected final String id;
    protected double position;
    protected VehicleStatus status = VehicleStatus.IN_SERVICE;

    protected Vehicle(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("vehicle id required");
        }
        this.id = id.trim();
    }

    public final String id() { return id; }
    public final double position() { return position; }
    public final VehicleStatus status() { return status; }

    public abstract void advance();
}
