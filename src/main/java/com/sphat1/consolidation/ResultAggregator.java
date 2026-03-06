package com.sphat1.consolidation;

import java.util.LinkedHashMap;
import java.util.Map;

public class ResultAggregator {

    private static final Map<String, SimulationResult> scoreboard = new LinkedHashMap<>();

    private record SimulationResult(double energy, int migrations, double sla) {}

    public static void collect(String scenarioName, double energy,
                               int migrations, double sla) {
        scoreboard.put(scenarioName, new SimulationResult(energy, migrations, sla));
    }

    public static void printReport() {
        if (scoreboard.isEmpty()) {
            System.out.println("\n[ResultAggregator] No results collected yet.");
            return;
        }

        System.out.println("\n\n" + "=".repeat(75));
        System.out.println("              FINAL CLOUD CONSOLIDATION PERFORMANCE REPORT");
        System.out.println("=".repeat(75));

        System.out.printf("%-25s | %-12s | %-12s | %-10s%n",
            "Scenario-Strategy", "Energy(kWh)", "Migrations", "SLA Viol.");
        System.out.println("-".repeat(75));

        scoreboard.forEach((name, data) -> {
            String slaStr = data.sla() < 0 ? "N/A" : String.format("%.0f", data.sla());
            System.out.printf("%-25s | %-12.6f | %-12d | %-10s%n",
                name, data.energy(), data.migrations(), slaStr);
        });

        System.out.println("=".repeat(75));
        System.out.println("Note: Lower Energy and SLA Violations = better performance.\n");
    }
}