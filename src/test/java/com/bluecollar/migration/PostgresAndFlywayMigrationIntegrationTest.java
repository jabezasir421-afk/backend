package com.bluecollar.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.net.Socket;
import java.sql.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PostgresAndFlywayMigrationIntegrationTest {

    private DataSource dataSource;
    private boolean isPostgres;

    @BeforeEach
    void setUp() {
        dataSource = createDataSource();
        assumeTrue(isPostgres, "PostgreSQL instance not reachable on localhost:5433. Skipping PostgreSQL migration test.");
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private DataSource createDataSource() {
        // Try connecting to local PostgreSQL on port 5433 first
        try (Socket socket = new Socket("localhost", 5433)) {
            PGSimpleDataSource pgDs = new PGSimpleDataSource();
            pgDs.setServerNames(new String[]{"localhost"});
            pgDs.setPortNumbers(new int[]{5433});
            pgDs.setDatabaseName("bluecollar_test");
            pgDs.setUser("postgres");
            pgDs.setPassword("");
            try (Connection conn = pgDs.getConnection()) {
                if (conn.isValid(2)) {
                    isPostgres = true;
                    return pgDs;
                }
            }
        } catch (Exception ignored) {
            // PostgreSQL not reachable
        }

        isPostgres = false;
        return null;
    }

    private void cleanDatabase() {
        if (!isPostgres || dataSource == null) {
            return;
        }
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS public CASCADE");
            stmt.execute("CREATE SCHEMA public");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clean database", e);
        }
    }

    @Test
    void freshDatabaseMigratesFromStartToLatestVersion() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        int applied = flyway.migrate().migrationsExecuted;
        assertTrue(applied >= 24, "Should apply all migrations from V2 to V26");

        // Verify key tables exist
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            assertTrue(tableExists(metaData, "category"));
            assertTrue(tableExists(metaData, "worker"));
            assertTrue(tableExists(metaData, "customer"));
            assertTrue(tableExists(metaData, "booking"));
            assertTrue(tableExists(metaData, "stored_file"));
            assertTrue(tableExists(metaData, "audit_log"));
        } catch (SQLException e) {
            fail("Database inspection failed: " + e.getMessage());
        }
    }

    @Test
    void populatedPreV22DatabaseUpgradesWithoutLosingAvatarData() throws SQLException {
        // 1. Migrate only up to V21 (pre-V22 schema where avatar_url column exists)
        Flyway flywayPre22 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("21")
                .load();
        flywayPre22.migrate();

        UUID userAccountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String legacyAvatarUrl = "https://cdn.bluecollar.in/avatars/alice_legacy_photo.png";

        // 2. Insert pre-existing user and customer with legacy avatar_url
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement psUser = conn.prepareStatement(
                    "INSERT INTO user_account (id, email, phone_number, password_hash, role, active, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, 'CUSTOMER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                psUser.setObject(1, userAccountId);
                psUser.setString(2, "alice.pre22@example.com");
                psUser.setString(3, "+1555000111");
                psUser.setString(4, "hashed_pw");
                psUser.executeUpdate();
            }

            try (PreparedStatement psCust = conn.prepareStatement(
                    "INSERT INTO customer (id, user_account_id, first_name, last_name, avatar_url, active, created_at, updated_at) " +
                            "VALUES (?, ?, 'Alice', 'Legacy', ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                psCust.setObject(1, customerId);
                psCust.setObject(2, userAccountId);
                psCust.setString(3, legacyAvatarUrl);
                psCust.executeUpdate();
            }
        }

        // 3. Upgrade to latest version (applies V22, V23, V24, V25, V26)
        Flyway flywayLatest = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flywayLatest.migrate();

        // 4. Verify customer profile photo was safely migrated to stored_file
        try (Connection conn = dataSource.getConnection()) {
            UUID profilePhotoFileId = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT profile_photo_file_id FROM customer WHERE id = ?")) {
                ps.setObject(1, customerId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Customer must exist after migration");
                    profilePhotoFileId = (UUID) rs.getObject("profile_photo_file_id");
                    assertNotNull(profilePhotoFileId, "profile_photo_file_id must not be null after V22 migration");
                }
            }

            // Verify stored_file record
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT entity_type, file_category, storage_key, owner_user_id, active FROM stored_file WHERE id = ?")) {
                ps.setObject(1, profilePhotoFileId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Stored file record must be created for migrated avatar");
                    assertEquals("CUSTOMER", rs.getString("entity_type"));
                    assertEquals("PROFILE_PHOTO", rs.getString("file_category"));
                    assertEquals(legacyAvatarUrl, rs.getString("storage_key"));
                    assertEquals(userAccountId, rs.getObject("owner_user_id"));
                    assertTrue(rs.getBoolean("active"));
                }
            }
        }
    }

    @Test
    void foreignKeyConstraintsRejectInvalidFileReferences() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        UUID userAccountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID nonExistentFileId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement psUser = conn.prepareStatement(
                    "INSERT INTO user_account (id, email, phone_number, password_hash, role, active, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, 'CUSTOMER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                psUser.setObject(1, userAccountId);
                psUser.setString(2, "fktest@example.com");
                psUser.setString(3, "+1555999000");
                psUser.setString(4, "hashed_pw");
                psUser.executeUpdate();
            }

            // Attempting to insert customer with non-existent profile_photo_file_id must fail FK check
            try (PreparedStatement psCust = conn.prepareStatement(
                    "INSERT INTO customer (id, user_account_id, first_name, last_name, profile_photo_file_id, active, created_at, updated_at) " +
                            "VALUES (?, ?, 'FK', 'Test', ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                psCust.setObject(1, customerId);
                psCust.setObject(2, userAccountId);
                psCust.setObject(3, nonExistentFileId);

                assertThrows(SQLException.class, psCust::executeUpdate,
                        "Foreign key fk_customer_profile_photo must reject invalid file reference");
            }
        }
    }

    @Test
    void existingProductionLikeDataSurvivesMigration() throws SQLException {
        // Migrate to V23 (before V24 added start_time and end_time to booking)
        Flyway flywayPre24 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("23")
                .load();
        flywayPre24.migrate();

        UUID userAccCust = UUID.randomUUID();
        UUID userAccWorker = UUID.randomUUID();
        UUID custId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        UUID addrId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            // Customer user
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO user_account (id, email, phone_number, password_hash, role, active, created_at, updated_at) " +
                            "VALUES (?, 'cust24@example.com', '+1555111222', 'pw', 'CUSTOMER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                ps.setObject(1, userAccCust);
                ps.executeUpdate();
            }

            // Worker user
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO user_account (id, email, phone_number, password_hash, role, active, created_at, updated_at) " +
                            "VALUES (?, 'work24@example.com', '+1555333444', 'pw', 'WORKER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                ps.setObject(1, userAccWorker);
                ps.executeUpdate();
            }

            // Customer
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO customer (id, user_account_id, first_name, last_name, active, created_at, updated_at) " +
                            "VALUES (?, ?, 'Prod', 'Customer', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                ps.setObject(1, custId);
                ps.setObject(2, userAccCust);
                ps.executeUpdate();
            }

            // Category
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO category (id, name, description, active, created_at, updated_at) " +
                            "VALUES (?, 'Electrical-Prod', 'Wiring', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                ps.setObject(1, catId);
                ps.executeUpdate();
            }

            // Worker
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO worker (id, user_account_id, first_name, last_name, phone_number, email, category_id, hourly_rate, active, created_at, updated_at) " +
                            "VALUES (?, ?, 'Electrician', 'Bob', '+1555333444', 'work24@example.com', ?, 50.00, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                ps.setObject(1, workerId);
                ps.setObject(2, userAccWorker);
                ps.setObject(3, catId);
                ps.executeUpdate();
            }

            // Address
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO address (id, customer_id, label, address_type, line1, city, state, pincode, active, created_at, updated_at) " +
                            "VALUES (?, ?, 'Home', 'HOME', '100 Main St', 'Dallas', 'TX', '75001', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                ps.setObject(1, addrId);
                ps.setObject(2, custId);
                ps.executeUpdate();
            }

            // Booking with time_slot "14:00-16:00"
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO booking (id, customer_id, worker_id, category_id, address_id, status, scheduled_date, time_slot, description, quoted_amount, address_line1, address_city, address_state, address_pincode, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, 'PENDING', CURRENT_DATE, '14:00-16:00', 'Fix fuse', 100.00, '100 Main St', 'Dallas', 'TX', '75001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                ps.setObject(1, bookingId);
                ps.setObject(2, custId);
                ps.setObject(3, workerId);
                ps.setObject(4, catId);
                ps.setObject(5, addrId);
                ps.executeUpdate();
            }
        }

        // Migrate to latest (V26)
        Flyway flywayLatest = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flywayLatest.migrate();

        // Verify booking parsed start_time and end_time correctly from time_slot in V24
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT start_time, end_time, description, quoted_amount FROM booking WHERE id = ?")) {
            ps.setObject(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Booking must survive migration");
                Time startTime = rs.getTime("start_time");
                Time endTime = rs.getTime("end_time");
                assertNotNull(startTime, "start_time must be backfilled");
                assertNotNull(endTime, "end_time must be backfilled");
                assertEquals("14:00:00", startTime.toString());
                assertEquals("16:00:00", endTime.toString());
                assertEquals("Fix fuse", rs.getString("description"));
                assertEquals(100.00, rs.getDouble("quoted_amount"), 0.001);
            }
        }
    }

    @Test
    void businessInvariantConstraintsRejectInvalidData() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // 1. Invalid booking status check
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO booking (id, customer_id, worker_id, category_id, address_id, status, scheduled_date, time_slot, description, quoted_amount, address_line1, address_city, address_state, address_pincode, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, 'BOGUS_STATUS', CURRENT_DATE, '10:00-11:00', 'desc', 50.00, 'line1', 'city', 'state', '12345', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, UUID.randomUUID());
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, UUID.randomUUID());
                ps.setObject(5, UUID.randomUUID());

                assertThrows(SQLException.class, ps::executeUpdate,
                        "chk_booking_status must reject invalid booking status");
            }

            // 2. Blank customer name check
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO customer (id, user_account_id, first_name, last_name, active, created_at, updated_at) " +
                            "VALUES (?, ?, '   ', 'Valid', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, UUID.randomUUID());

                assertThrows(SQLException.class, ps::executeUpdate,
                        "chk_customer_name must reject empty/whitespace-only first_name");
            }
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String tableName) throws SQLException {
        try (ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }
        // Fallback for case-sensitivity
        try (ResultSet rs = metaData.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
