package com.example.ngo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;

@SpringBootApplication
@RestController
@CrossOrigin("*")
public class NgoApplication {

    private static final String dburl = "jdbc:mysql://127.0.0.1:3306/ngo_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String user = "root";
    private static final String pwd = "Lishvik@25";

    public static void main(String[] args) {
        new Thread(new TcpNotificationServer()).start();
        SpringApplication.run(NgoApplication.class, args);
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dburl, user, pwd);
    }

    static abstract class User {
        public String id;
        public String name;

        public User(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static class Donor extends User {
        public Donor(String id, String name) {
            super(id, name);
        }
    }

    interface Notification {
        void sendAlert(String message);
    }

    static class DataFormat<T> {
        private T data;

        public DataFormat(T data) {
            this.data = data;
        }

        public T getData() {
            return data;
        }
    }

    static class Item {
        public int id;
        public String name;
        public double price;
        public String reqFrom;
        public String category;
        public int isUrgent;

        public Item(int id, String name, double price, String reqFrom,
                    String category, int isUrgent) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.reqFrom = reqFrom;
            this.category = category;
            this.isUrgent = isUrgent;
        }
    }

    static class Order {
        public int id;
        public String itemName;
        public double price;
        public String status;

        public Order(int id, String itemName, double price, String status) {
            this.id = id;
            this.itemName = itemName;
            this.price = price;
            this.status = status;
        }
    }

    static class TcpNotificationServer implements Runnable {

        @Override
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket(9999)) {

                System.out.println("TCP SERVER:Running on internal port 9999...");

                while (true) {
                    try (Socket socket = serverSocket.accept();
                         BufferedReader in = new BufferedReader(
                                 new InputStreamReader(socket.getInputStream()))) {

                        String alertMsg = in.readLine();
                        System.out.println("REAL-TIME DASHBOARD ALERT: " + alertMsg);

                    } catch (Exception e) {
                        System.err.println("TCP Session Error: " + e.getMessage());
                    }
                }

            } catch (IOException e) {
                System.err.println("TCP Server failed to start: " + e.getMessage());
            }
        }
    }

    static class NotificationService implements Notification {

        @Override
        public void sendAlert(String message) {

            DataFormat<String> packet = new DataFormat<>(message);

            try (Socket socket = new Socket("127.0.0.1", 9999);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                out.println(packet.getData());

            } catch (Exception e) {
                System.err.println("TCP Client link dropped: " + e.getMessage());
            }
        }
    }

    private final Notification notifier = new NotificationService();

    @GetMapping("/api/items")
    public List<Item> getItems() throws Exception {

        List<Item> items = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "select * from items order by is_urgent desc, id desc")) {

            while (rs.next()) {
                items.add(new Item(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("price"),
                        rs.getString("requested_by"),
                        rs.getString("category"),
                        rs.getInt("is_urgent")
                ));
            }
        }

        return items;
    }

    @GetMapping("/api/analytics")
    public Map<String, Object> getAnalytics() throws Exception {

        Map<String, Object> metrics = new HashMap<>();
        double totalFunds = 0;
        int completedDeliveries = 0;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(
                    "select sum(price) as total from orders")) {

                if (rs.next()) {
                    totalFunds = rs.getDouble("total");
                }
            }

            try (ResultSet rs = stmt.executeQuery(
                    "select count(*) as count from orders where status = 'delivered'")) {

                if (rs.next()) {
                    completedDeliveries = rs.getInt("count");
                }
            }
        }

        metrics.put("totalFunds", totalFunds);
        metrics.put("completedDeliveries", completedDeliveries);

        return metrics;
    }

    @PostMapping("/api/orders")
    public String placeOrder(@RequestBody Map<String, Object> payload) throws Exception {

        String itemName = (String) payload.get("itemName");
        double price = Double.parseDouble(payload.get("price").toString());

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "insert into orders (item_name, price, status) values (?, ?, 'ordered')")) {

            ps.setString(1, itemName);
            ps.setDouble(2, price);
            ps.executeUpdate();
        }

        notifier.sendAlert("NEW ORDER: '" + itemName + "' purchased by Donor!");

        return "Order complete!";
    }

    @GetMapping("/api/orders")
    public List<Order> getOrders() throws Exception {

        List<Order> orders = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "select * from orders order by id desc")) {

            while (rs.next()) {
                orders.add(new Order(
                        rs.getInt("id"),
                        rs.getString("item_name"),
                        rs.getDouble("price"),
                        rs.getString("status")
                ));
            }
        }

        return orders;
    }

    @PostMapping("/api/orders/update")
    public String updateOrderStatus(@RequestBody Map<String, Object> payload) throws Exception {

        int orderId = Integer.parseInt(payload.get("id").toString());
        String nextStatus = (String) payload.get("status");

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "update orders set status = ? where id = ?")) {

            ps.setString(1, nextStatus);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }

        notifier.sendAlert(
                "TRACKING UPDATE: Order #" + orderId + " is now [" + nextStatus + "]"
        );

        return "Status updated!";
    }

    @PostMapping("/api/items/request")
    public String requestItem(@RequestBody Map<String, Object> payload) throws Exception {

        String name = (String) payload.get("name");
        double price = Double.parseDouble(payload.get("price").toString());
        String orphanage = (String) payload.get("orphanage");
        String category = (String) payload.get("category");
        int isUrgent = Boolean.parseBoolean(
                payload.get("isUrgent").toString()) ? 1 : 0;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "insert into items (name, price, requested_by, category, is_urgent) values (?, ?, ?, ?, ?)")) {

            ps.setString(1, name);
            ps.setDouble(2, price);
            ps.setString(3, orphanage);
            ps.setString(4, category);
            ps.setInt(5, isUrgent);
            ps.executeUpdate();
        }

        notifier.sendAlert("ORPHANAGE REQUEST: '" + name + "' added to pool.");

        return "Wishlist request recorded!";
    }
}