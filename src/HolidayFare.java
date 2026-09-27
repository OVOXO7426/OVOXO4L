public class HolidayFare implements FarePolicy {
    public HolidayFare() {
    }

    @Override
    public long dayFare(long baseCents, int earlierTripsToday) {
        if (earlierTripsToday == 0) {
            return baseCents / 2;
        }
        return 0;
    }
}
