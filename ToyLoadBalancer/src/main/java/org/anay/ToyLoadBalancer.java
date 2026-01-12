package org.anay;

import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ToyLoadBalancer<T> {
    private final SortedMap<Long, T> ring = new TreeMap<>();
    private final int numberOfReplicas;

    // Statistics Trackers
    private final Map<T, AtomicInteger> nodeRequestCount = new ConcurrentHashMap<>();
    private final AtomicInteger totalRequests = new AtomicInteger(0);

    // Initialized at the top of the class
    private final Map<String, AtomicInteger> nodeStats = new ConcurrentHashMap<>();

    public ToyLoadBalancer(int numberOfReplicas, Collection<T> initialNodes) {
        this.numberOfReplicas = numberOfReplicas;
        for (T node : initialNodes) {
            addNode(node);
        }
    }

    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes());
            long res = 0;
            for (int i = 0; i < 8; i++) {
                res <<= 8;
                res |= (digest[i] & 0xFF);
            }
            return res;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void addNode(T node) {
        System.out.println("[SYSTEM] Adding Node: " + node);
        nodeRequestCount.putIfAbsent(node, new AtomicInteger(0));
        nodeStats.putIfAbsent(node.toString(), new AtomicInteger(0));
        for (int i = 0; i < numberOfReplicas; i++) {
            ring.put(hash(node.toString() + "_" + i), node);

        }
    }

    public synchronized void removeNode(T node) {
        System.out.println("[SYSTEM] Removing Node: " + node);
        for (int i = 0; i < numberOfReplicas; i++) {
            ring.remove(hash(node.toString() + "_" + i));
        }
        // We keep the stats for the removed node so we can see its history
    }

    public T routeRequest(String clientIp) {
        if (ring.isEmpty()) return null;

        // Create a unique key using IP and current epoch
        String requestKey = clientIp + "_" + System.currentTimeMillis();
        long hash = hash(requestKey);

        SortedMap<Long, T> tailMap = ring.tailMap(hash);
        long nodeHash = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();

        T selectedNode = ring.get(nodeHash);

        // Update Stats
        totalRequests.incrementAndGet();
        nodeRequestCount.get(selectedNode).incrementAndGet();
        nodeStats.get(selectedNode.toString()).incrementAndGet();

        System.out.printf("[LOG] Request [%s] -> Hash: %d -> Routed to: %s%n",
                requestKey, hash, selectedNode);

        return selectedNode;
    }

    /*public void printStats() {
        System.out.println("\n--- LOAD DISTRIBUTION REPORT ---");
        System.out.println("Total Requests Processed: " + totalRequests.get());

        nodeRequestCount.forEach((node, count) -> {
            double percentage = (totalRequests.get() == 0) ? 0 :
                    (count.get() * 100.0 / totalRequests.get());
            System.out.printf("Node: %-10s | Requests: %-5d | Share: %.2f%%%n",
                    node, count.get(), percentage);
        });
        System.out.println("--------------------------------\n");
    }*/

    public void printStats() {
        // ANSI Color Codes for the terminal
        final String RESET = "\u001B[0m";
        final String CYAN = "\u001B[36m";
        final String GREEN = "\u001B[32m";
        final String YELLOW = "\u001B[33m";
        final String BLUE = "\u001B[34m";

        int total = totalRequests.get();

        System.out.println(CYAN + "╔══════════════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(CYAN + "║                  LOAD BALANCER DISTRIBUTION REPORT                   ║" + RESET);
        System.out.println(CYAN + "╠══════════════════════════════════════════════════════════════════════╣" + RESET);
        System.out.printf(CYAN + "║  " + RESET + "Total Requests Processed: %-41d " + CYAN + "║%n", total);
        System.out.printf(CYAN + "║  " + RESET + "Active Server Nodes:      %-41d " + CYAN + "║%n", nodeStats.size());
        System.out.println(CYAN + "╠══════════════════╦══════════════╦══════════╦═════════════════════════╣" + RESET);
        System.out.printf(CYAN + "║" + YELLOW + "  %-14s  " + CYAN + "║" + YELLOW + "  %-10s  " + CYAN + "║" + YELLOW + "  %-6s  " + CYAN + "║" + YELLOW + "  %-21s  " + CYAN + "║%n",
                "NODE NAME", "REQUESTS", "SHARE", "LOAD VISUALIZATION");
        System.out.println(CYAN + "╠══════════════════╬══════════════╬══════════╬═════════════════════════╣" + RESET);

        // Sort by name for consistent display
        List<String> sortedNodes = new ArrayList<>(nodeStats.keySet());
        Collections.sort(sortedNodes);

        for (String node : sortedNodes) {
            int count = nodeStats.get(node).get();
            double percentage = (total == 0) ? 0 : (count * 100.0 / total);

            // Create a visual bar (20 characters wide)
            String progressBar = generateProgressBar(percentage, 20);

            System.out.printf(CYAN + "║" + RESET + "  %-14s  " + CYAN + "║" + RESET + "  %-10d  " + CYAN + "║" + RESET + "  %5.1f%%  " + CYAN + "║" + GREEN + " %-23s " + CYAN + "║%n",
                    node, count, percentage, progressBar);
        }

        System.out.println(CYAN + "╚══════════════════╩══════════════╩══════════╩═════════════════════════╝" + RESET + "\n");
    }

    /**
     * Generates a string like [■■■■■□□□□□] based on percentage
     */
    private String generateProgressBar(double percentage, int width) {
        int filledLength = (int) (width * (percentage / 100.0));
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            if (i < filledLength) bar.append("■");
            else bar.append(" ");
        }
        bar.append("]");
        return bar.toString();
    }

    // --- Simulation Runner ---
    public static void main(String[] args) throws InterruptedException {
        List<String> initialServers = new ArrayList<>(Arrays.asList("SERVER_A", "SERVER_B", "SERVER_C"));

        // 10 virtual nodes per real node to help balance
        ToyLoadBalancer<String> lb = new ToyLoadBalancer<>(10, initialServers);

        // Simulate traffic and scaling
        for (int i = 1; i <= 3; i++) {
            System.out.println("Starting Simulation Round " + i);

            // Mock traffic
            for (int r = 0; r < 20; r++) {
                lb.routeRequest("192.168.1." + r);
                Thread.sleep(100); // Small delay for epoch change
            }

            lb.printStats();

            // Mock scaling (In a real app, this would be a background thread/ScheduledExecutor)
            if (i == 1) lb.addNode("SERVER_D");
            if (i == 2) lb.removeNode("SERVER_A");
        }
    }
}