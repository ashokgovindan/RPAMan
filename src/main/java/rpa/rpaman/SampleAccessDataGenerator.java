package rpa.rpaman;

import com.healthmarketscience.jackcess.ColumnBuilder;
import com.healthmarketscience.jackcess.DataType;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Table;
import com.healthmarketscience.jackcess.TableBuilder;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds three throw-away MS Access databases so the Received, Pending and
 * Processed queries can be exercised end to end.
 * <p>
 * Run it once — from the IDE, or with
 * {@code mvn compile exec:java -Dexec.mainClass=rpa.rpaman.SampleAccessDataGenerator} —
 * and it writes into a {@code sample-data} folder beside the project, then
 * prints the DB Path and the three queries to paste into each project's
 * Details View.
 * <p>
 * Row counts are fixed rather than random, so the numbers the Bot Run Status
 * report shows can be checked against the summary printed here.
 */
public final class SampleAccessDataGenerator {

    private static final String TABLE = "BotQueue";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** One machine's workload: the split decides the expected query results. */
    private static final class MachineLoad {
        final String machine;
        final String user;
        final int pending;
        final int processed;
        final int failed;

        MachineLoad(String machine, String user, int pending, int processed, int failed) {
            this.machine = machine;
            this.user = user;
            this.pending = pending;
            this.processed = processed;
            this.failed = failed;
        }

        int received() {
            return pending + processed + failed;
        }
    }

    /** One sample database, named after the RPA it belongs to. */
    private static final class BotDatabase {
        final String fileName;
        final String rpaName;
        final String requestPrefix;
        final List<MachineLoad> machines = new ArrayList<>();

        BotDatabase(String fileName, String rpaName, String requestPrefix, MachineLoad... loads) {
            this.fileName = fileName;
            this.rpaName = rpaName;
            this.requestPrefix = requestPrefix;
            for (MachineLoad load : loads) {
                machines.add(load);
            }
        }
    }

    private static final BotDatabase[] DATABASES = {
            new BotDatabase("TaxAgent_Invoices.accdb", "TaxAgent_Invoices", "INV",
                    new MachineLoad("Mach1", "User1", 33, 6, 14),
                    new MachineLoad("Mach2", "User2", 12, 27, 2)),

            new BotDatabase("NAR_Umbrella.accdb", "NAR Umbrella", "NAR",
                    new MachineLoad("LP-214846003", "chamala.haritha", 8, 61, 3),
                    new MachineLoad("Mach3", "User3", 0, 19, 0)),

            new BotDatabase("VendorOnboarding.accdb", "VendorOnboarding", "VND",
                    new MachineLoad("VM-RPA-01", "svc_rpa", 21, 44, 5),
                    new MachineLoad("VM-RPA-02", "svc_rpa2", 4, 8, 1))
    };

    public static void main(String[] args) throws Exception {
        File folder = new File(args.length > 0 ? args[0] : "sample-data");
        if (!folder.exists() && !folder.mkdirs()) {
            System.err.println("Could not create " + folder.getAbsolutePath());
            return;
        }

        System.out.println("Writing sample Access databases to " + folder.getAbsolutePath());
        System.out.println();

        for (BotDatabase spec : DATABASES) {
            File file = new File(folder, spec.fileName);
            if (file.exists() && !file.delete()) {
                System.err.println("Could not overwrite " + file.getAbsolutePath() + ", skipping.");
                continue;
            }
            build(file, spec);
            report(file, spec);
        }

        System.out.println("Done. Point each project's DB Path at the file above,");
        System.out.println("add its machines under Machines, then use View > Bot Run Status.");
    }

    // ----------------------------------------------------------------- build

    private static void build(File file, BotDatabase spec) throws Exception {
        try (Database db = DatabaseBuilder.create(Database.FileFormat.V2010, file)) {

            // Timestamps are TEXT in ISO form: it keeps the generator independent
            // of the Jackcess date API and still sorts and compares correctly.
            Table table = new TableBuilder(TABLE)
                    .addColumn(new ColumnBuilder("ID", DataType.LONG).setAutoNumber(true))
                    .addColumn(new ColumnBuilder("RequestID", DataType.TEXT).setLength(chars(50)))
                    .addColumn(new ColumnBuilder("Status", DataType.TEXT).setLength(chars(20)))
                    .addColumn(new ColumnBuilder("Machine", DataType.TEXT).setLength(chars(60)))
                    .addColumn(new ColumnBuilder("UserName", DataType.TEXT).setLength(chars(60)))
                    .addColumn(new ColumnBuilder("ReceivedOn", DataType.TEXT).setLength(chars(25)))
                    .addColumn(new ColumnBuilder("ProcessedOn", DataType.TEXT).setLength(chars(25)))
                    .addColumn(new ColumnBuilder("Notes", DataType.MEMO))
                    .toTable(db);

            LocalDateTime start = LocalDateTime.now().minusDays(2);
            int sequence = 1000;

            for (MachineLoad load : spec.machines) {
                sequence = addRows(table, spec, load, "Pending", load.pending, start, sequence);
                sequence = addRows(table, spec, load, "Processed", load.processed, start, sequence);
                sequence = addRows(table, spec, load, "Failed", load.failed, start, sequence);
            }
        }
    }

    /**
     * Converts a character count into the byte length Jackcess expects.
     * Access stores text as Unicode, so a column declared 25 bytes only holds
     * 12 characters — not enough for a "yyyy-MM-dd HH:mm:ss" stamp.
     */
    private static int chars(int count) {
        return count * 2;
    }

    private static int addRows(Table table, BotDatabase spec, MachineLoad load, String status,
                               int count, LocalDateTime start, int sequence) throws Exception {
        for (int i = 0; i < count; i++) {
            LocalDateTime received = start.plusMinutes(sequence % 2880);
            boolean finished = !"Pending".equals(status);
            String processedOn = finished ? received.plusMinutes(3 + (i % 17)).format(STAMP) : "";

            String notes;
            if ("Failed".equals(status)) {
                notes = "Timed out waiting for the portal response.";
            } else if ("Pending".equals(status)) {
                notes = "Queued, awaiting a free session.";
            } else {
                notes = "Completed without exceptions.";
            }

            table.addRow(
                    null,                                        // ID autonumbers
                    spec.requestPrefix + "-" + sequence,
                    status,
                    load.machine,
                    load.user,
                    received.format(STAMP),
                    processedOn,
                    notes);
            sequence++;
        }
        return sequence;
    }

    // ---------------------------------------------------------------- report

    private static void report(File file, BotDatabase spec) {
        System.out.println("------------------------------------------------------------");
        System.out.println("RPA:      " + spec.rpaName);
        System.out.println("DB Path:  " + file.getAbsolutePath());
        System.out.println();
        System.out.println("  Machines to add under the project's Machines section:");
        for (MachineLoad load : spec.machines) {
            System.out.println("    " + load.machine + "\t" + load.user
                    + "   -> Received " + load.received()
                    + ", Pending " + load.pending
                    + ", Processed " + load.processed
                    + "  (Failed " + load.failed + ")");
        }
        System.out.println();
        System.out.println("  Received Query:");
        System.out.println("    SELECT COUNT(*) FROM " + TABLE + " WHERE Machine='{machine}'");
        System.out.println("  Pending Query:");
        System.out.println("    SELECT COUNT(*) FROM " + TABLE
                + " WHERE Machine='{machine}' AND Status='Pending'");
        System.out.println("  Processed Query:");
        System.out.println("    SELECT COUNT(*) FROM " + TABLE
                + " WHERE Machine='{machine}' AND Status='Processed'");
        System.out.println();
    }

    private SampleAccessDataGenerator() {
    }
}
