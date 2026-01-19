package org.anay.util;

import org.anay.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BookingManager {

    public static final String DB_URL = "jdbc:mysql://localhost:3306/prototype";
    public static final String USER = "devuser1";
    public static final String PASS = "MyNewPass1!";

    public static void resetSeats() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             PreparedStatement st = conn.prepareStatement("UPDATE seats SET user_id = NULL")) {
            st.executeUpdate();
            System.out.println("--- DB Reset Complete ---");
        }
    }

    public static List<User> getUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name FROM users")) {
            while (rs.next()) {
                users.add(new User(rs.getInt("id"), rs.getString("name")));
            }
        }
        return users;
    }

    public static void printFinalStatus() throws SQLException {
        List<String[]> results = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT s.name, u.name as uname FROM seats s LEFT JOIN users u ON s.user_id = u.id ORDER BY s.id")) {

            System.out.println("\n--- Final Seat Assignments ---");
            while (rs.next()) {
                String seatName = rs.getString("name");
                String userName = rs.getString("uname");
                results.add(new String[]{seatName, userName});
                System.out.printf("Seat: %s | User: %s%n", seatName, (userName == null ? "EMPTY" : userName));
            }
        }

        // --- Visual Grid Representation ---
        System.out.println("\n--- Flight Seat Map ---");
        System.out.println("   A  B  C   D  E  F"); // Header

        int count = 0;
        for (String[] row : results) {
            String seatName = row[0];
            String userName = row[1];

            // Print row number at the start of every 6 seats
            if (count % 6 == 0) {
                System.out.printf("%2d ", (count / 6) + 1);
            }

            // Show tick (✓) if booked, otherwise (-)
            String status = (userName != null) ? "✓" : "-";
            System.out.print("[" + status + "]");

            // Add a gap for the aisle between C and D
            if (count % 6 == 2) {
                System.out.print(" ");
            }

            count++;
            // New line after every 6 seats (A-F)
            if (count % 6 == 0) {
                System.out.println();
            }
        }
    }
}
