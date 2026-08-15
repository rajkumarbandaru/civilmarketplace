package com.civileng.marketplace.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The reports screen: a catalogue of the exports an admin can take off the platform, and the
 * generator behind them.
 *
 * <p>A report here is a <em>view over data another service already owns</em>, never a second copy
 * of it — each one names the call that produces its rows and the columns to lift out of them. That
 * is what keeps a report honest: an export of bookings is the booking list, not a parallel
 * calculation of it that can drift.
 *
 * <p>Reports are generated on demand rather than scheduled and stored. At this platform's size the
 * generation cost is one upstream call, and a stored report is a number that was true once.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminReportService {

    private final AdminRevenueService adminRevenueService;
    private final AdminAnalyticsService adminAnalyticsService;
    private final AdminUserService adminUserService;
    private final AdminBookingService adminBookingService;
    private final AdminCategoryService adminCategoryService;

    /** How many rows an export pulls from a paginated source. */
    private static final int EXPORT_PAGE_SIZE = 1000;

    /**
     * One report: what it is called, where its rows come from, and which fields become columns.
     *
     * @param columns field name -> column heading, in the order the CSV writes them
     */
    private record ReportDefinition(
            String key,
            String label,
            String description,
            String category,
            Map<String, String> columns,
            Supplier<List<Map<String, Object>>> rows) {
    }

    private List<ReportDefinition> definitions() {
        return List.of(
                new ReportDefinition("revenue-monthly", "Monthly revenue",
                        "Revenue, platform fees, payouts and profit for each of the last twelve months.",
                        "Finance",
                        columns("month", "Month", "revenue", "Revenue", "fees", "Platform fees",
                                "payouts", "Payouts", "profit", "Profit"),
                        () -> rowsOf(adminRevenueService.getMonthlyRevenue())),

                new ReportDefinition("transactions", "Transactions",
                        "Every recorded payment, refund and payout with its booking and customer.",
                        "Finance",
                        columns("transactionId", "Transaction", "bookingCode", "Booking",
                                "customerName", "Customer", "amount", "Amount", "type", "Type",
                                "status", "Status", "date", "Date"),
                        () -> rowsOf(adminRevenueService.getRecentTransactions(0, EXPORT_PAGE_SIZE))),

                new ReportDefinition("revenue-breakdown", "Revenue breakdown",
                        "Where the platform's revenue comes from, by source.",
                        "Finance",
                        columns("label", "Source", "value", "Revenue", "percentage", "Share %"),
                        () -> rowsOf(adminRevenueService.getRevenueBreakdown())),

                new ReportDefinition("users", "Users",
                        "The full user list with role, status, city and verification state.",
                        "Platform",
                        columns("id", "ID", "name", "Name", "email", "Email", "phone", "Phone",
                                "role", "Role", "status", "Status", "city", "City",
                                "emailVerified", "Email verified", "phoneVerified", "Phone verified",
                                "joinedAt", "Joined"),
                        () -> rowsOf(adminUserService.getUsers(0, EXPORT_PAGE_SIZE, null, null, null))),

                new ReportDefinition("bookings", "Bookings",
                        "Every booking with its customer, worker, amount and payment state.",
                        "Operations",
                        columns("bookingCode", "Booking", "customerName", "Customer",
                                "workerName", "Worker", "serviceName", "Service", "status", "Status",
                                "amount", "Amount", "city", "City", "paymentStatus", "Payment",
                                "createdAt", "Created"),
                        () -> rowsOf(adminBookingService.getBookings(0, EXPORT_PAGE_SIZE, null, null, null))),

                new ReportDefinition("categories", "Categories",
                        "The service catalogue with how many services sit under each category.",
                        "Operations",
                        columns("id", "ID", "name", "Name", "slug", "Slug", "parentName", "Parent",
                                "servicesCount", "Services", "active", "Active"),
                        () -> rowsOf(adminCategoryService.getCategories())),

                new ReportDefinition("city-performance", "City performance",
                        "Users, bookings and revenue per city, with growth.",
                        "Growth",
                        columns("city", "City", "users", "Users", "bookings", "Bookings",
                                "revenue", "Revenue", "growth", "Growth"),
                        () -> rowsOf(adminAnalyticsService.getCityPerformance())),

                new ReportDefinition("top-categories", "Top categories",
                        "The most booked categories and how fast each is growing.",
                        "Growth",
                        columns("name", "Category", "bookings", "Bookings", "growth", "Growth"),
                        () -> rowsOf(adminAnalyticsService.getTopCategories())));
    }

    // ------------------------------------------------------------------ catalogue

    /** The catalogue the screen lists. Row counts are omitted — see {@link #catalogueEntry}. */
    public Map<String, Object> catalogue() {
        List<Map<String, Object>> reports = definitions().stream()
                .map(AdminReportService::catalogueEntry)
                .toList();
        return Map.of("reports", reports, "generatedAt", LocalDate.now().toString());
    }

    /**
     * A catalogue row describes the report without running it: listing eight reports would
     * otherwise mean eight upstream calls every time the screen opens, to show a count nobody
     * asked for.
     */
    private static Map<String, Object> catalogueEntry(ReportDefinition definition) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("key", definition.key());
        entry.put("label", definition.label());
        entry.put("description", definition.description());
        entry.put("category", definition.category());
        entry.put("columns", List.copyOf(definition.columns().values()));
        return entry;
    }

    // ------------------------------------------------------------------ generation

    /** The report's rows as JSON, for the preview table. */
    public Map<String, Object> preview(String key, int limit) {
        ReportDefinition definition = require(key);
        List<Map<String, Object>> rows = safeRows(definition);
        List<Map<String, Object>> projected = rows.stream()
                .limit(Math.max(limit, 0))
                .map(row -> project(row, definition.columns()))
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", definition.key());
        data.put("label", definition.label());
        data.put("columns", List.copyOf(definition.columns().values()));
        data.put("rows", projected);
        data.put("totalRows", rows.size());
        data.put("generatedAt", LocalDate.now().toString());
        return data;
    }

    /** The whole report as CSV — what the download button fetches. */
    public String csv(String key) {
        ReportDefinition definition = require(key);
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", definition.columns().values().stream().map(AdminReportService::escape).toList()))
                .append('\n');
        for (Map<String, Object> row : safeRows(definition)) {
            List<String> cells = definition.columns().keySet().stream()
                    .map(field -> escape(stringify(row.get(field))))
                    .toList();
            csv.append(String.join(",", cells)).append('\n');
        }
        return csv.toString();
    }

    /** {@code revenue-monthly} -> {@code revenue-monthly-2026-08-15.csv}. */
    public String fileName(String key) {
        return require(key).key() + "-" + LocalDate.now() + ".csv";
    }

    // ------------------------------------------------------------------ helpers

    private ReportDefinition require(String key) {
        return definitions().stream()
                .filter(definition -> definition.key().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such report: " + key));
    }

    /**
     * An upstream service that is down must not turn a report into a 500 — the screen lists eight
     * reports and one broken source should cost one empty table, not the page.
     */
    private List<Map<String, Object>> safeRows(ReportDefinition definition) {
        try {
            return definition.rows().get();
        } catch (Exception e) {
            log.warn("Report {} could not be generated: {}", definition.key(), e.getMessage());
            return List.of();
        }
    }

    /** Only the fields the report declares, in the declared order. */
    private static Map<String, Object> project(Map<String, Object> row, Map<String, String> columns) {
        Map<String, Object> projected = new LinkedHashMap<>();
        columns.forEach((field, heading) -> projected.put(heading, stringify(row.get(field))));
        return projected;
    }

    /**
     * The row list inside an upstream response. The services here answer in two shapes — a list
     * under {@code data}, or an object under {@code data} holding the list under {@code items} —
     * so both are unwrapped rather than making every caller know which one it gets.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowsOf(Map<String, Object> response) {
        if (response == null) return List.of();
        Object data = response.get("data");
        if (data instanceof List<?> list) return castRows(list);
        if (data instanceof Map<?, ?> map) {
            Object items = ((Map<String, Object>) map).get("items");
            if (items instanceof List<?> list) return castRows(list);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castRows(List<?> list) {
        List<Map<String, Object>> rows = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element instanceof Map<?, ?> map) rows.add((Map<String, Object>) map);
        }
        return rows;
    }

    private static Map<String, String> columns(String... fieldsAndHeadings) {
        Map<String, String> columns = new LinkedHashMap<>();
        for (int i = 0; i + 1 < fieldsAndHeadings.length; i += 2) {
            columns.put(fieldsAndHeadings[i], fieldsAndHeadings[i + 1]);
        }
        return columns;
    }

    private static String stringify(Object value) {
        return value == null ? "" : value.toString();
    }

    /** RFC 4180: quote when the cell contains a comma, a quote or a newline, doubling quotes. */
    private static String escape(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
