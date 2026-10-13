package com.estateflow.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** An amount aggregated for one calendar month (chart data). */
public record MonthlyAmount(YearMonth month, BigDecimal amount) {

    /** Returns exactly {@code months} entries ending at {@code last}, with zero for months that have no data. */
    public static List<MonthlyAmount> fill(List<MonthlyAmount> sparse, YearMonth last, int months) {
        Map<YearMonth, BigDecimal> byMonth = sparse.stream()
                .collect(Collectors.toMap(MonthlyAmount::month, MonthlyAmount::amount, BigDecimal::add));
        List<MonthlyAmount> out = new ArrayList<>(months);
        for (int i = months - 1; i >= 0; i--) {
            YearMonth m = last.minusMonths(i);
            out.add(new MonthlyAmount(m, byMonth.getOrDefault(m, BigDecimal.ZERO)));
        }
        return out;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public static boolean allZero(List<MonthlyAmount> list) {
        return list.stream().allMatch(MonthlyAmount::isZero);
    }
}
