// SUPPLIED - do not modify or submit.
public abstract class Passenger {
    protected final String name;

    protected Passenger(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("passenger name required");
        }
        this.name = name.trim();
    }

    public final String name() { return name; }

    public abstract long applyDiscount(long fareCents);
}
