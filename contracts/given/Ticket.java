// SUPPLIED - do not modify or submit.
public final class Ticket {
    private final String id;
    private final Passenger passenger;
    private final Trip trip;
    private final int day;
    private final boolean holiday;
    private final long baseFareCents;
    private final long dayFareCents;
    private final long finalFareCents;

    public Ticket(String id, Passenger passenger, Trip trip, int day, boolean holiday,
                  long baseFareCents, long dayFareCents, long finalFareCents) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("ticket id required");
        if (passenger == null || trip == null) {
            throw new IllegalArgumentException("passenger and trip required");
        }
        if (day < 0) throw new IllegalArgumentException("day must be >= 0");
        if (baseFareCents < 0 || dayFareCents < 0 || dayFareCents > baseFareCents
                || finalFareCents < 0 || finalFareCents > dayFareCents) {
            throw new IllegalArgumentException(
                "fares must satisfy 0 <= final <= day fare <= base fare");
        }
        this.id = id.trim();
        this.passenger = passenger;
        this.trip = trip;
        this.day = day;
        this.holiday = holiday;
        this.baseFareCents = baseFareCents;
        this.dayFareCents = dayFareCents;
        this.finalFareCents = finalFareCents;
    }

    public String id() { return id; }
    public Passenger passenger() { return passenger; }
    public Trip trip() { return trip; }
    public int day() { return day; }
    public boolean holiday() { return holiday; }
    public long baseFareCents() { return baseFareCents; }
    public long dayFareCents() { return dayFareCents; }
    public long finalFareCents() { return finalFareCents; }
    public long dayDiscountCents() { return baseFareCents - dayFareCents; }
    public long passengerDiscountCents() { return dayFareCents - finalFareCents; }
}
