public class AdultPassenger extends Passenger {
    public AdultPassenger(String name) {
        super(name);
    }

    @Override
    public long applyDiscount(long fareCents) {
        return fareCents;
    }
}
