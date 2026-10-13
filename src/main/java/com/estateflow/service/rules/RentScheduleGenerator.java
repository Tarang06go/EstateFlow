package com.estateflow.service.rules;

import com.estateflow.model.RentCharge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Pure function from agreement terms to rent charges (BR-11, BR-14). No I/O. */
public final class RentScheduleGenerator {

    private RentScheduleGenerator() {
    }

    /** BR-11: end_date = start_date + duration_months - 1 day. */
    public static LocalDate endDate(LocalDate startDate, int durationMonths) {
        return startDate.plusMonths(durationMonths).minusDays(1);
    }

    /**
     * BR-14: one charge per month. Period i starts at start.plusMonths(i), computed from the start
     * date each time to avoid month-end drift, ends the day before period i+1, and is due on its start.
     */
    public static List<RentCharge> generate(Long agreementId, LocalDate startDate, int durationMonths,
                                            BigDecimal monthlyRent) {
        List<RentCharge> charges = new ArrayList<>(durationMonths);
        for (int i = 0; i < durationMonths; i++) {
            LocalDate periodStart = startDate.plusMonths(i);
            LocalDate periodEnd = startDate.plusMonths(i + 1L).minusDays(1);
            charges.add(new RentCharge(agreementId, periodStart, periodEnd, periodStart, monthlyRent));
        }
        return charges;
    }
}
