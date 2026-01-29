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
                    return splitLeafFill(node);
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
                return splitInternalFill(node);
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

        private BPlusTreeNode splitLeafFill(BPlusTreeNode leaf) {
            // Determine the split point based on the last key in the leaf.
            // We use leaf.keys.size() - 1 because the overflow key was
            // already added to the list in insertIntoLeaf().
            int lastKeyIndex = leaf.keys.size() - 1;
            int secondToLastKey = leaf.keys.get(lastKeyIndex - 1);
            int newestKey = leaf.keys.get(lastKeyIndex);

            int mid;

            // ASYMMETRIC STRATEGY:
            // If the newest key is greater than the previous max,
            // keep the current node full and put only the newest key in the new node.
            if (newestKey > secondToLastKey) {
                log("✨ Asymmetric split detected (Sequential Insertion)");
                mid = lastKeyIndex;
            } else {
                // BALANCED STRATEGY:
                // Standard middle split for random data
                log("⚖ Balanced split detected (Random Insertion)");
                mid = leaf.keys.size() / 2;
            }

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
        private BPlusTreeNode splitInternalBalanced(BPlusTreeNode node) {
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

        private BPlusTreeNode splitInternalFill(BPlusTreeNode node) {
            // 1. Get the last key to see if we are inserting at the end
            int lastKey = node.keys.get(node.keys.size() - 1);
            int secondToLast = node.keys.get(node.keys.size() - 2);

            int mid;
            int promoteKey;

            // 2. ASYMMETRIC LOGIC: If newest promoted key is at the right edge
            if (lastKey > secondToLast) {
                log("✨ Internal Asymmetric Split (Right-Heavy)");
                // We keep the original node full (all keys except the last one)
                mid = node.keys.size() - 1;
                promoteKey = node.keys.get(mid);
            } else {
                // 3. BALANCED LOGIC: For random data
                log("⚖ Internal Balanced Split (Middle)");
                mid = node.keys.size() / 2;
                promoteKey = node.keys.get(mid);
            }

            BPlusTreeNode right = new BPlusTreeNode(false);

            // 4. Move keys and children to the new right node
            // Note: Children are always 1 more than keys
            right.keys.addAll(node.keys.subList(mid + 1, node.keys.size()));
            right.children.addAll(node.children.subList(mid + 1, node.children.size()));

            // 5. Clean up the original node
            node.keys = new ArrayList<>(node.keys.subList(0, mid));
            node.children = new ArrayList<>(node.children.subList(0, mid + 1));

            // 6. Return the new node with the promoted key at the start
            // (matches your existing insertInternal logic)
            right.keys.add(0, promoteKey);

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
        BPlusTree tree = new BPlusTree(10);

        //int[] keys = {10, 20, 5, 6, 12, 30, 7, 17, 3, 25};
        //int[] keys = {10, 20, 5, 6, 12, 30};

        // sorted keys
        int[] keys = {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
                41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60,
                61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80,
                81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100,
                101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120,
                121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140,
                141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160,
                161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180,
                181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200
        };

        // random keys
        int[] randomKeys = {
                142, 12, 178, 45, 99, 7, 134, 189, 23, 56, 111, 167, 84, 3, 195, 62, 128, 37, 156, 91,
                20, 148, 77, 102, 49, 183, 15, 120, 68, 192, 33, 159, 88, 41, 174, 5, 115, 198, 54, 137,
                26, 163, 94, 71, 145, 10, 186, 123, 47, 152, 80, 18, 113, 170, 59, 131, 35, 194, 96, 65,
                2, 150, 73, 108, 43, 180, 125, 14, 155, 82, 31, 191, 52, 118, 165, 90, 39, 140, 61, 177,
                28, 105, 75, 143, 8, 188, 121, 50, 158, 86, 46, 112, 197, 57, 135, 22, 169, 93, 70, 147,
                17, 182, 117, 34, 153, 79, 196, 64, 129, 40, 11, 172, 53, 141, 25, 161, 95, 67, 151, 19,
                185, 110, 72, 144, 4, 190, 124, 48, 157, 83, 32, 119, 200, 55, 136, 21, 168, 92, 69, 146,
                16, 181, 116, 36, 154, 78, 193, 63, 130, 42, 9, 171, 51, 139, 24, 160, 97, 66, 149, 1,
                184, 109, 74, 143, 6, 187, 122, 50, 158, 85, 30, 114, 199, 58, 133, 27, 164, 98, 76, 145,
                13, 179, 107, 38, 162, 81, 190, 60, 132, 44, 100, 173, 56, 138, 29, 166, 89, 71, 101, 175
        };

        for (int k : randomKeys) {
            tree.insert(k);
        }
    }
}
