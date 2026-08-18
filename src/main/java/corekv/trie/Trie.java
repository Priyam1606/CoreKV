package corekv.trie;

import corekv.hash.CustomHashTable;

import java.util.ArrayList;
import java.util.List;

public class Trie {
    private final TrieNode root = new TrieNode();

    public void insert(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        TrieNode current = root;
        for (char character : word.toCharArray()) {
            TrieNode next = current.children.get(character);
            if (next == null) {
                next = new TrieNode();
                current.children.put(character, next);
            }
            current = next;
        }
        current.terminal = true;
    }

    public boolean contains(String word) {
        TrieNode node = traverse(word);
        return node != null && node.terminal;
    }

    public boolean startsWith(String prefix) {
        return traverse(prefix) != null;
    }

    public void remove(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        remove(root, word, 0);
    }

    public List<String> keysWithPrefix(String prefix) {
        List<String> results = new ArrayList<>();
        String safePrefix = prefix == null ? "" : prefix;
        TrieNode startNode = traverse(safePrefix);
        if (startNode == null) {
            return results;
        }
        collect(startNode, new StringBuilder(safePrefix), results);
        return results;
    }

    public void clear() {
        root.children.clear();
        root.terminal = false;
    }

    private TrieNode traverse(String text) {
        if (text == null) {
            return null;
        }
        TrieNode current = root;
        for (char character : text.toCharArray()) {
            current = current.children.get(character);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private boolean remove(TrieNode node, String word, int index) {
        if (index == word.length()) {
            if (!node.terminal) {
                return false;
            }
            node.terminal = false;
            return node.children.isEmpty();
        }

        char character = word.charAt(index);
        TrieNode child = node.children.get(character);
        if (child == null) {
            return false;
        }

        boolean shouldPruneChild = remove(child, word, index + 1);
        if (shouldPruneChild) {
            node.children.remove(character);
        }

        return !node.terminal && node.children.isEmpty();
    }

    private void collect(TrieNode node, StringBuilder builder, List<String> output) {
        if (node.terminal) {
            output.add(builder.toString());
        }

        for (CustomHashTable.Entry<Character, TrieNode> entry : node.children.entries()) {
            builder.append(entry.key());
            collect(entry.value(), builder, output);
            builder.deleteCharAt(builder.length() - 1);
        }
    }

    private static final class TrieNode {
        private final CustomHashTable<Character, TrieNode> children = new CustomHashTable<>();
        private boolean terminal;
    }
}
