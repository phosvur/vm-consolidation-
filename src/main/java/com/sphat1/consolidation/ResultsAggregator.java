package com.sphat1.consolidation;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ResultsAggregator {

    private static final String CSV_FILE = "simulation_results.csv";
    private final List<ResultEntry> records = new ArrayList<>();

    private record ResultEntry(
        String scenario,
        String type,
        double energy,
        int    migrations,
        int    slaViolations,
        double slaViolationTime   // total VM-seconds where demand > supply
    ) {}

    public void addResult(String scenario, String type,
                          double energy, int migrations,
                          int slaViolations, double slaViolationTime) {
        records.add(new ResultEntry(scenario, type, energy,
                                    migrations, slaViolations, slaViolationTime));
    }

    public void exportToCsv() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE))) {
            // Header — SLAViolationTime_s is the richer metric for graphing
            writer.println("Scenario,PolicyType,Energy_kWh,Migrations,SLAViolationEvents,SLAViolationTime_s");

            for (ResultEntry r : records) {
                writer.printf("%s,%s,%.6f,%d,%d,%.4f%n",
                    r.scenario(), r.type(), r.energy(),
                    r.migrations(), r.slaViolations(), r.slaViolationTime());
            }
            System.out.println("\n[Success] Results exported to: " + CSV_FILE);
        } catch (IOException e) {
            System.err.println("Error writing CSV: " + e.getMessage());
        }
    }
}