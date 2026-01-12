import java.util.*;

public class BPlusTreeDemo {

    // ================= NODE =================
    static class BPlusTreeNode {
        boolean isLeaf;
        List<Integer> keys = new ArrayList<>();
        List<BPlusTreeNode> children = new ArrayList<>();
        BPlusTreeNode next; // leaf chain

        BPlusTreeNode(boolean isLeaf) {
            this.isLeaf = isLeaf;
        }
    }

    // ================= TREE =================
    static class BPlusTree {
        private final int order;
        private BPlusTreeNode root;

        public BPlusTree(int order) {
            this.order = order;
            this.root = new BPlusTreeNode(true);
            log("Created B+ Tree with order = " + order);
        }

        // ---------- INSERT ----------
        public void insert(int key) {
            log("\n==============================");
            log("INSERT KEY: " + key);

            BPlusTreeNode splitNode = insertInternal(root, key);

            if (splitNode != null) {
                int promotedKey = splitNode.keys.get(0);
                BPlusTreeNode newRoot = new BPlusTreeNode(false);
                newRoot.keys.add(promotedKey);
                newRoot.children.add(root);
                
                if (!splitNode.isLeaf) {
                    splitNode.keys.remove(0);
                }
                
                newRoot.children.add(splitNode);
                root = newRoot;
                log("🌱 Root split → New root created with key [" + promotedKey + "]");
            }

            //logTree();
            logTreeFixed();
            logFragmentationNice();
        }

        private BPlusTreeNode insertInternal(BPlusTreeNode node, int key) {
            if (node.isLeaf) {
                log("Reached LEAF node: " + node.keys);
                insertIntoLeaf(node, key);

                if (node.keys.size() >= order) {
                    log("⚠ Leaf overflow → splitting leaf");
                    return splitLeaf(node);
                }
                return null;
            }

            int idx = findChildIndex(node, key);
            log("At INTERNAL node " + node.keys + " → going to child " + idx);

            BPlusTreeNode splitNode =
                    insertInternal(node.children.get(idx), key);

            if (splitNode != null) {
                int promotedKey = splitNode.keys.get(0);
                log("⬆ Promoting key " + promotedKey + " to internal node");

                node.keys.add(idx, promotedKey);
                
                if (!splitNode.isLeaf) {
                    splitNode.keys.remove(0);
                }
                
                node.children.add(idx + 1, splitNode);
            }

            if (node.keys.size() >= order) {
                log("⚠ Internal node overflow → splitting internal node");
                return splitInternal(node);
            }

            return null;
        }

        // ---------- LEAF OPS ----------
        private void insertIntoLeaf(BPlusTreeNode leaf, int key) {
            int idx = 0;
            while (idx < leaf.keys.size() && key > leaf.keys.get(idx)) {
                idx++;
            }
            leaf.keys.add(idx, key);
            log("Inserted key " + key + " into leaf at position " + idx);
        }

        private BPlusTreeNode splitLeaf(BPlusTreeNode leaf) {
            int mid = leaf.keys.size() / 2;

            BPlusTreeNode newLeaf = new BPlusTreeNode(true);
            newLeaf.keys.addAll(leaf.keys.subList(mid, leaf.keys.size()));

            leaf.keys = new ArrayList<>(leaf.keys.subList(0, mid));

            newLeaf.next = leaf.next;
            leaf.next = newLeaf;

            log("Leaf split:");
            log("  Left leaf : " + leaf.keys);
            log("  Right leaf: " + newLeaf.keys);

            return newLeaf;
        }

        // ---------- INTERNAL OPS ----------
        private BPlusTreeNode splitInternal(BPlusTreeNode node) {
            int mid = node.keys.size() / 2;
            int promoteKey = node.keys.get(mid);

            BPlusTreeNode right = new BPlusTreeNode(false);
            right.keys.addAll(node.keys.subList(mid + 1, node.keys.size()));
            right.children.addAll(node.children.subList(mid + 1, node.children.size()));

            node.keys = new ArrayList<>(node.keys.subList(0, mid));
            node.children = new ArrayList<>(node.children.subList(0, mid + 1));

            right.keys.add(0, promoteKey);

            log("Internal split:");
            log("  Promote key : " + promoteKey);
            log("  Left node   : " + node.keys);
            log("  Right node  : " + right.keys);

            return right;
        }

        // ---------- HELPERS ----------
        private int findChildIndex(BPlusTreeNode node, int key) {
            int idx = 0;
            while (idx < node.keys.size() && key >= node.keys.get(idx)) {
                idx++;
            }
            return idx;
        }

        // ---------- LOGGING ----------
        private void logTree() {
            log("\nTREE STRUCTURE:");
            Queue<BPlusTreeNode> q = new LinkedList<>();
            q.add(root);

            while (!q.isEmpty()) {
                int size = q.size();
                for (int i = 0; i < size; i++) {
                    BPlusTreeNode n = q.poll();
                    System.out.print(n.keys + " ");
                    if (!n.isLeaf) {
                        q.addAll(n.children);
                    }
                }
                System.out.println();
            }

            log("Leaf chain:");
            BPlusTreeNode leaf = root;
            while (!leaf.isLeaf) {
                leaf = leaf.children.get(0);
            }
            while (leaf != null) {
                System.out.print(leaf.keys + " → ");
                leaf = leaf.next;
            }
            System.out.println("NULL");
        }

        private void logTreeFixed() {
            log("\nTREE STRUCTURE (FIXED):");
            Queue<BPlusTreeNode> q = new LinkedList<>();
            q.add(root);
            int level = 0;

            while (!q.isEmpty()) {
                int size = q.size();
                System.out.print("Level " + level + ": ");
                for (int i = 0; i < size; i++) {
                    BPlusTreeNode n = q.poll();
                    System.out.print(n.keys + (n.isLeaf ? "(L) " : "(I) "));
                    if (!n.isLeaf) {
                        q.addAll(n.children);
                    }
                }
                System.out.println();
                level++;
            }

            log("Leaf chain:");
            BPlusTreeNode leaf = root;
            while (!leaf.isLeaf) {
                leaf = leaf.children.get(0);
            }
            while (leaf != null) {
                System.out.print(leaf.keys + " → ");
                leaf = leaf.next;
            }
            System.out.println("NULL");
        }

        private void logFragmentation() {
            log("\nFRAGMENTATION REPORT:");
            logFragmentation(root);
        }

        private void logFragmentation(BPlusTreeNode node) {
            double frag = (double) (order - node.keys.size()) / order;
            log((node.isLeaf ? "Leaf" : "Internal")
                    + " " + node.keys
                    + " | utilization=" + node.keys.size() + "/" + order
                    + " | fragmentation=" + String.format("%.2f", frag));

            if (!node.isLeaf) {
                for (BPlusTreeNode c : node.children) {
                    logFragmentation(c);
                }
            }
        }

        private void logFragmentationNice() {
            logReport("\n╔════════════════════════════════════════════════════════════╗");
            logReport("║              FRAGMENTATION ANALYSIS REPORT                 ║");
            logReport("╠════════════════════════════════════════════════════════════╣");
            
            List<String> internalNodes = new ArrayList<>();
            List<String> leafNodes = new ArrayList<>();
            int[] stats = new int[4];
            collectFragmentationData(root, internalNodes, leafNodes, stats);
            
            int totalKeys = stats[0];
            int totalCapacity = stats[1];
            int internalKeys = stats[2];
            int leafKeys = stats[3];
            
            if (!internalNodes.isEmpty()) {
                logReport("║ INTERNAL NODES:                                            ║");
                logReport("╟────────────────────────────────────────────────────────────╢");
                for (String line : internalNodes) {
                    logReport(String.format("║ %-58s ║", line));
                }
            }
            
            if (!leafNodes.isEmpty()) {
                logReport("╟────────────────────────────────────────────────────────────╢");
                logReport("║ LEAF NODES:                                                ║");
                logReport("╟────────────────────────────────────────────────────────────╢");
                for (String line : leafNodes) {
                    logReport(String.format("║ %-58s ║", line));
                }
            }
            
            double totalUtil = (totalKeys * 100.0) / totalCapacity;
            double totalFrag = 100.0 - totalUtil;
            String totalBar = createUtilizationBar(totalUtil);
            
            logReport("╠════════════════════════════════════════════════════════════╣");
            logReport("║ OVERALL STATISTICS:                                        ║");
            logReport("╟────────────────────────────────────────────────────────────╢");
            
            String totalKeysLine = String.format("║ Total Keys:        %d (%d internal, %d leaf)", 
                totalKeys, internalKeys, leafKeys);
            int totalKeysPad = 61 - totalKeysLine.length();
            logReport(totalKeysLine + repeat(' ',Math.max(0, totalKeysPad)) + "║");
            
            logReport(String.format("║ Data Records:      %-39d ║", leafKeys));
            logReport(String.format("║ Total Capacity:    %-39d ║", totalCapacity));
            
            String utilLine = String.format("║ Utilization:       %s %.0f%%", totalBar, totalUtil);
            int utilPad = 61 - utilLine.length();
            logReport(utilLine + repeat(' ',Math.max(0, utilPad)) + "║");
            
            String fragLine = String.format("║ Fragmentation:     %.0f%%", totalFrag);
            int fragPad = 61 - fragLine.length();
            logReport(fragLine + repeat(' ',Math.max(0, fragPad)) + "║");
            
            logReport("╚════════════════════════════════════════════════════════════╝");
        }

        private static String repeat(char ch, int count) {
            if (count <= 0) return "";
            char[] arr = new char[count];
            Arrays.fill(arr, ch);
            return new String(arr);
        }

        private void collectFragmentationData(BPlusTreeNode node, List<String> internalNodes, List<String> leafNodes, int[] stats) {
            int utilized = node.keys.size();
            double utilPct = (utilized * 100.0) / order;
            
            stats[0] += utilized;
            stats[1] += order;
            
            if (node.isLeaf) {
                stats[3] += utilized;
            } else {
                stats[2] += utilized;
            }
            
            String bar = createUtilizationBar(utilPct);
            String line = String.format("%-12s %s [%d/%d] %.0f%% used",
                    node.keys.toString(),
                    bar,
                    utilized,
                    order,
                    utilPct);
            
            if (node.isLeaf) {
                leafNodes.add(line);
            } else {
                internalNodes.add(line);
                for (BPlusTreeNode c : node.children) {
                    collectFragmentationData(c, internalNodes, leafNodes, stats);
                }
            }
        }

        private String createUtilizationBar(double percent) {
            int barLength = 20;
            int filled = (int) ((percent / 100.0) * barLength);
            StringBuilder bar = new StringBuilder("[");
            for (int i = 0; i < barLength; i++) {
                bar.append(i < filled ? "█" : "░");
            }
            bar.append("]");
            return bar.toString();
        }

        private static void log(String msg) {
            System.out.println("[LOG] " + msg);
        }

        private static void logReport(String msg) {
            System.out.println(msg);
        }
    }

    // ================= MAIN =================
    public static void main(String[] args) {
        BPlusTree tree = new BPlusTree(3);

        //int[] keys = {10, 20, 5, 6, 12, 30, 7, 17, 3, 25};
        int[] keys = {10, 20, 5, 6, 12, 30};

        for (int k : keys) {
            tree.insert(k);
        }
    }
}
