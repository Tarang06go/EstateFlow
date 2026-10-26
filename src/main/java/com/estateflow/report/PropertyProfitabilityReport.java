package com.estateflow.report;

import com.estateflow.dto.ProfitabilityReport.PropertyProfit;

import java.util.List;

/** Property-wise rent received, expenses and NOI for a period (a TOTAL row is appended by the service). */
public class PropertyProfitabilityReport implements ReportDefinition<PropertyProfit> {

    @Override
    public String fileName() {
        return "property-profitability.csv";
    }

    @Override
    public List<String> headers() {
        return List.of("Property", "Rent charged", "Rent received", "Expenses", "NOI");
    }

    @Override
    public List<String> toRow(PropertyProfit p) {
        return List.of(p.propertyName(), p.rentCharged().toPlainString(), p.rentReceived().toPlainString(),
                p.expenses().toPlainString(), p.noi().toPlainString());
    }
}
