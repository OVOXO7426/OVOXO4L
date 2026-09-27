public class NormalFare implements FarePolicy {
    public NormalFare() {
    }

    @Override
    public long dayFare(long baseCents, int earlierTripsToday) {
        return baseCents;
    }
}
