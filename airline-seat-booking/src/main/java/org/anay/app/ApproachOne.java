package org.anay.app;

import org.anay.model.User;
import org.anay.util.BookingManager;

import java.sql.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ApproachOne {
        public static void main(String[] args) throws Exception {
            BookingManager.resetSeats();
            List<User> users = BookingManager.getUsers();
            ExecutorService executor = Executors.newFixedThreadPool(120);
            long startTime = System.currentTimeMillis();

            for (User user : users) {
                executor.execute(() -> bookSeat(user));
            }

            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES);
            long totalTime = (System.currentTimeMillis() - startTime);
            BookingManager.printFinalStatus();
            System.out.println("Total Time: " + totalTime + "ms");
        }

        private static void bookSeat(User user) {
            String selectSql = "SELECT id, name FROM seats WHERE trip_id=1 AND user_id IS NULL ORDER BY id LIMIT 1";
            String updateSql = "UPDATE seats SET user_id = ? WHERE id = ?";

            try (Connection conn = DriverManager.getConnection(BookingManager.DB_URL, BookingManager.USER, BookingManager.PASS)) {
                conn.setAutoCommit(false);
                try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(selectSql)) {
                    if (rs.next()) {
                        int seatId = rs.getInt("id");
                        String seatName = rs.getString("name");
                        try (PreparedStatement pst = conn.prepareStatement(updateSql)) {
                            pst.setInt(1, user.getId());
                            pst.setInt(2, seatId);
                            pst.executeUpdate();
                        }
                        conn.commit();
                        System.out.println("[Approach 1] User " + user.getName() + " booked seat name " + seatName);
                    }
                } catch (SQLException e) { conn.rollback(); }
            } catch (SQLException e) { e.printStackTrace(); }
        }
}
