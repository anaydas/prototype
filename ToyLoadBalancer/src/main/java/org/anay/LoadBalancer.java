package org.anay;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancer<T> {

    private final long numberOfReplicas; // It is for the virtual nodes

    private final SortedMap<Long, T> hashRing = new TreeMap<>();

    private final Map<String, AtomicInteger> nodeRequestCount = new ConcurrentHashMap<>();

    private final AtomicInteger totalRequestCount = new AtomicInteger(0);

    private final List<String> activeNodes = new ArrayList<>();

    public LoadBalancer(long numberOfReplicas, Collection<T> initialNodes){

        this.numberOfReplicas = numberOfReplicas;
        for(T node: initialNodes){
            addNode(node);
        }
    }

    public synchronized void addNode(T node){
        System.out.println("[SYSTEM] Adding Node: " + node);
        nodeRequestCount.putIfAbsent(node.toString(), new AtomicInteger(0));
        // Assigning virtual nodes to prevent hot space in the ring. Same node will be distributed across the ring
        for(int i=0; i< numberOfReplicas; i++){
            hashRing.put(hash(node.toString()+"_"+i),node);
        }
        activeNodes.add(node.toString());
    }

    public synchronized void removeNode(T node){
        System.out.println("[SYSTEM] Removing Node: " + node);
        for(int i=0; i< numberOfReplicas; i++){
            hashRing.remove(hash(node.toString()+"_"+i));
            activeNodes.remove(node.toString());
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

    public T routeRequest(String clientIp){

        if(hashRing.isEmpty())
            return null;

        String requestKey = clientIp+ "_" + System.currentTimeMillis();
        long hash = hash(requestKey);

        long nextNodeHash = getNextNodeHash(hash);

        T selectedNode = hashRing.get(nextNodeHash);

        totalRequestCount.getAndIncrement();
        nodeRequestCount.get(selectedNode.toString()).getAndIncrement();

        System.out.printf("[LOG] Request [%s] -> Hash: %d -> Routed to: %s%n",
                requestKey, hash, selectedNode);

        return selectedNode;
    }

    private long getNextNodeHash(long hash) {
        SortedMap<Long, T> tailMap = hashRing.tailMap(hash);
        return tailMap.isEmpty() ? hashRing.firstKey() : tailMap.firstKey();
    }

    public void printStats() {
        // ANSI Color Codes for the terminal
        final String RESET = "\u001B[0m";
        final String CYAN = "\u001B[36m";
        final String GREEN = "\u001B[32m";
        final String YELLOW = "\u001B[33m";
        final String BLUE = "\u001B[34m";

        int total = totalRequestCount.get();

        System.out.println(CYAN + "╔══════════════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(CYAN + "║                  LOAD BALANCER DISTRIBUTION REPORT                   ║" + RESET);
        System.out.println(CYAN + "╠══════════════════════════════════════════════════════════════════════╣" + RESET);
        System.out.printf(CYAN + "║  " + RESET + "Total Requests Processed: %-41d " + CYAN + "║%n", total);
        System.out.printf(CYAN + "║  " + RESET + "Active Server Count:      %-41d " + CYAN + "║%n", nodeRequestCount.size());
        System.out.printf(CYAN + "║  " + RESET + "Active Server Nodes Names:      %-35s " + CYAN + "║%n", activeNodes.toString());
        System.out.println(CYAN + "╠══════════════════╦══════════════╦══════════╦═════════════════════════╣" + RESET);
        System.out.printf(CYAN + "║" + YELLOW + "  %-14s  " + CYAN + "║" + YELLOW + "  %-10s  " + CYAN + "║" + YELLOW + "  %-6s  " + CYAN + "║" + YELLOW + "  %-21s  " + CYAN + "║%n",
                "NODE NAME", "REQUESTS", "SHARE", "LOAD VISUALIZATION");
        System.out.println(CYAN + "╠══════════════════╬══════════════╬══════════╬═════════════════════════╣" + RESET);

        // Sort by name for consistent display
        List<String> sortedNodes = new ArrayList<>(nodeRequestCount.keySet());
        Collections.sort(sortedNodes);

        for (String node : sortedNodes) {
            int count = nodeRequestCount.get(node).get();
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

    public static void main(String[] args) throws InterruptedException{

        List<String> initialServers = new ArrayList<>(Arrays.asList("NODE_A","NODE_B","NODE_C"));

        LoadBalancer<String> loadBalancer = new LoadBalancer<>(10,initialServers);
        startSimulation(loadBalancer);

    }

    private static void startSimulation(LoadBalancer<String> loadBalancer) throws InterruptedException {

        for(int i=1; i<=3; i++){
            System.out.println("Starting Simulation Round "+i);

            //Mock traffic
            for(int request=0; request<10; request++){
                loadBalancer.routeRequest("192.168.7."+request);
                Thread.sleep(100);
            }

            loadBalancer.printStats();

            if(i==1)
                loadBalancer.addNode("NODE_D");
            if(i==2)
                loadBalancer.removeNode("NODE_A");
        }
    }


}
