package refactor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * AVL-backed TreeMap implementation of {@link StaticTreeMap}.
 * Nodes store key-value pairs ordered by key using an AVL self-balancing
 * tree. The predecessor strategy is used for two-child removal.
 *
 * @version 1.0
 * @author Stephen Onuh
 * @param <K> the type of keys - must be {@link Comparable}
 * @param <V> the type of values
 */
public class TreeMap<K extends Comparable<? super K>, V>
        implements StaticTreeMap<K, V> {
    private TMNode<K, V> root;

    private int size;
    
    /**
     * Recalculates the height and balance factor of a node
     * based on its children's stored heights.
     *
     * @param node the node to update; must be non-null
     */
    private void update(TMNode<K, V> node) {
        int lh = node.getLeft()  == null ? -1 : node.getLeft().getHeight();
        int rh = node.getRight() == null ? -1 : node.getRight().getHeight();
        node.setHeight(1 + Math.max(lh, rh));
        node.setBalanceFactor(lh - rh);
    }

    /**
     * Performs a left rotation on the given node, making its right child
     * the new subtree root and moving the original node down to the left.
     *
     * @param node the root of the subtree to rotate left
     * @return the new root of the rotated subtree
     */
    private TMNode<K, V> leftRotate(TMNode<K, V> node) {
        TMNode<K, V> newRoot = node.getRight();
        node.setRight(newRoot.getLeft());
        newRoot.setLeft(node);
        update(node);
        update(newRoot);
        return newRoot;
    }

    /**
     * Performs a right rotation on the given node, making its left child
     * the new subtree root and moving the original node down to the right.
     *
     * @param node the root of the subtree to rotate right
     * @return the new root of the rotated subtree
     */
    private TMNode<K, V> rightRotate(TMNode<K, V> node) {
        TMNode<K, V> newRoot = node.getLeft();
        node.setLeft(newRoot.getRight());
        newRoot.setRight(node);
        update(node);
        update(newRoot);
        return newRoot;
    }

    /**
     * Inspects the balance factor of a node and applies single or double
     * rotations as necessary to restore the AVL invariant.
     *
     * @param node the root of the subtree to balance
     * @return the new root of the subtree after any rotations
     */
    private TMNode<K, V> balance(TMNode<K, V> node) {
        int bf = node.getBalanceFactor();
        if (bf > 1) {
            if (node.getLeft().getBalanceFactor() < 0) {
                node.setLeft(leftRotate(node.getLeft()));
            }
            return rightRotate(node);
        } else if (bf < -1) {
            if (node.getRight().getBalanceFactor() > 0) {
                node.setRight(rightRotate(node.getRight()));
            }
            return leftRotate(node);
        }
        return node;
    }

    /**
     * Adds the given key-value pair to the map. If an entry already has
     * this key, the old value is replaced with the new one.
     * The tree is rebalanced after insertion if necessary.
     *
     * @param key   the key to add; must not be null
     * @param value the value to associate with the key; must not be null
     * @return null if the key was not already in the map; the old value otherwise
     * @throws IllegalArgumentException if key or value is null
     */
    @Override
    public V put(K key, V value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value must not be null.");
        }
        Holder<V> holder = new Holder<>();
        root = putH(root, key, value, holder);
        return holder.getValue();
    }

    /**
     * Recursive helper for put. Descends the tree to find the correct
     * insertion point, then rebalances on the way back up.
     *
     * @param curr   the root of the current subtree
     * @param key    the key to insert or update
     * @param value  the value to associate with the key
     * @param holder mutable holder that captures any replaced old value
     * @return the new root of the subtree after insertion and rebalancing
     */
    private TMNode<K, V> putH(TMNode<K, V> curr, K key,
                               V value, Holder<V> holder) {
        if (curr == null) {
            size++;
            return new TMNode<>(key, value);
        }
        int comp = key.compareTo(curr.getKey());
        if (comp > 0) {
            curr.setRight(putH(curr.getRight(), key, value, holder));
        } else if (comp < 0) {
            curr.setLeft(putH(curr.getLeft(), key, value, holder));
        } else {
            holder.setValue(curr.getValue());
            curr.setValue(value);
            return curr;
        }
        update(curr);
        return balance(curr);
    }

    /**
     * Removes the entry with the given key from the map and returns its
     * associated value. Uses the in-order predecessor when removing a node
     * with two children. The tree is rebalanced after removal if necessary.
     *
     * @param key the key of the entry to remove; must not be null
     * @return the value previously associated with the key
     * @throws IllegalArgumentException if key is null
     * @throws NoSuchElementException   if the key is not in the map
     */
    @Override
    public V remove(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null.");
        }
        TMNode<K, V> dummy = new TMNode<>(null, null);
        root = removeH(root, key, dummy);
        return dummy.getValue();
    }

    /**
     * Recursive helper for remove. Searches for the node with the given
     * key, removes it (handling 0, 1, and 2 child cases), and rebalances
     * on the way back up the call stack.
     *
     * @param curr  the root of the current subtree
     * @param key   the key to remove
     * @param dummy a dummy node used to capture the removed value
     * @return the new root of the subtree after removal and rebalancing
     * @throws NoSuchElementException if the key is not found in the subtree
     */
    private TMNode<K, V> removeH(TMNode<K, V> curr, K key,
                                  TMNode<K, V> dummy) {
        if (curr == null) {
            throw new NoSuchElementException("Key not found in map.");
        }
        int comp = key.compareTo(curr.getKey());
        if (comp > 0) {
            curr.setRight(removeH(curr.getRight(), key, dummy));
        } else if (comp < 0) {
            curr.setLeft(removeH(curr.getLeft(), key, dummy));
        } else {
            dummy.setValue(curr.getValue());
            size--;
            if (curr.getRight() == null) {
                return curr.getLeft();
            } else if (curr.getLeft() == null) {
                return curr.getRight();
            } else {
                TMNode<K, V> pred = new TMNode<>(null, null);
                curr.setLeft(removePredecessor(curr.getLeft(), pred));
                curr.setKey(pred.getKey());
                curr.setValue(pred.getValue());
            }
        }
        update(curr);
        return balance(curr);
    }

    /**
     * Removes and captures the in-order predecessor (rightmost node of
     * the left subtree) and rebalances on the way back up.
     *
     * @param curr  the root of the subtree to search for the predecessor
     * @param dummy a dummy node used to capture the predecessor's key and value
     * @return the new root of the subtree after the predecessor is removed
     */
    private TMNode<K, V> removePredecessor(TMNode<K, V> curr,
                                            TMNode<K, V> dummy) {
        if (curr.getRight() == null) {
            dummy.setKey(curr.getKey());
            dummy.setValue(curr.getValue());
            return curr.getLeft();
        }
        curr.setRight(removePredecessor(curr.getRight(), dummy));
        update(curr);
        return balance(curr);
    }

    /**
     * Returns the value associated with the given key by performing a
     * standard BST search on the AVL tree.
     *
     * @param key the key to search for; must not be null
     * @return the value associated with the given key
     * @throws IllegalArgumentException if key is null
     * @throws NoSuchElementException   if the key is not in the map
     */
    @Override
    public V get(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null.");
        }
        TMNode<K, V> node = getNode(root, key);
        if (node == null) {
            throw new NoSuchElementException("Key not found in map.");
        }
        return node.getValue();
    }

    /**
     * Searches the subtree rooted at curr for a node with the given key
     * using standard BST traversal.
     *
     * @param curr the root of the current subtree
     * @param key  the key to search for
     * @return the node containing the key, or null if not found
     */
    private TMNode<K, V> getNode(TMNode<K, V> curr, K key) {
        if (curr == null) {
            return null;
        }
        int comp = key.compareTo(curr.getKey());
        if (comp > 0) {
            return getNode(curr.getRight(), key);
        } else if (comp < 0) {
            return getNode(curr.getLeft(), key);
        } else {
            return curr;
        }
    }

    /**
     * Returns a list of all values whose associated keys fall within the
     * inclusive range [lower, upper], in ascending key order. Branches of
     * the AVL tree outside the range are pruned for efficiency, giving
     * O(log n + k) time where k is the number of results.
     *
     * @param lower the lower key bound (inclusive); must not be null
     * @param upper the upper key bound (inclusive); must not be null
     * @return a list of all values with keys in [lower, upper], in order
     * @throws IllegalArgumentException if either bound is null
     */
    @Override
    public List<V> getRange(K lower, K upper) {
        if (lower == null || upper == null) {
            throw new IllegalArgumentException("Bounds must not be null.");
        }
        List<V> result = new ArrayList<>();
        getRangeH(root, lower, upper, result);
        return result;
    }

    /**
     * Recursive in-order traversal that collects values in [lower, upper],
     * pruning left subtrees when the current key is at or below lower, and
     * pruning right subtrees when the current key is at or above upper.
     *
     * @param curr   the root of the current subtree
     * @param lower  the lower key bound (inclusive)
     * @param upper  the upper key bound (inclusive)
     * @param result the list to append in-range values to
     */
    private void getRangeH(TMNode<K, V> curr, K lower,
                            K upper, List<V> result) {
        if (curr == null) {
            return;
        }
        int cmpLow  = curr.getKey().compareTo(lower);
        int cmpHigh = curr.getKey().compareTo(upper);
        if (cmpLow > 0) {
            getRangeH(curr.getLeft(), lower, upper, result);
        }
        if (cmpLow >= 0 && cmpHigh <= 0) {
            result.add(curr.getValue());
        }
        if (cmpHigh < 0) {
            getRangeH(curr.getRight(), lower, upper, result);
        }
    }

    /**
     * Returns whether the given key is present in the map by performing
     * a standard BST search on the AVL tree.
     *
     * @param key the key to search for; must not be null
     * @return true if the key is contained within the map, false otherwise
     * @throws IllegalArgumentException if key is null
     */
    @Override
    public boolean containsKey(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null.");
        }
        return getNode(root, key) != null;
    }

    /**
     * Returns a HashSet containing all keys in this map. The keys are
     * collected via an in-order traversal of the AVL tree.
     *
     * @return the set of all keys in this map
     */
    @Override
    public Set<K> keySet() {
        Set<K> keys = new HashSet<>();
        keySetH(root, keys);
        return keys;
    }

    /**
     * Recursive in-order traversal helper that adds every node's key
     * to the provided set.
     *
     * @param curr the root of the current subtree
     * @param keys the set to add keys into
     */
    private void keySetH(TMNode<K, V> curr, Set<K> keys) {
        if (curr == null) {
            return;
        }
        keySetH(curr.getLeft(), keys);
        keys.add(curr.getKey());
        keySetH(curr.getRight(), keys);
    }

    /**
     * Returns an ArrayList of all values in this map, ordered by their
     * associated keys in ascending order. The values are collected via
     * an in-order traversal of the AVL tree.
     *
     * @return a list of all values in this map sorted by key
     */
    @Override
    public List<V> values() {
        List<V> vals = new ArrayList<>();
        valuesH(root, vals);
        return vals;
    }

    /**
     * Recursive in-order traversal helper that appends every node's value
     * to the provided list in ascending key order.
     *
     * @param curr the root of the current subtree
     * @param vals the list to append values into
     */
    private void valuesH(TMNode<K, V> curr, List<V> vals) {
        if (curr == null) {
            return;
        }
        valuesH(curr.getLeft(), vals);
        vals.add(curr.getValue());
        valuesH(curr.getRight(), vals);
    }

    /**
     * Removes all entries from the map by discarding the root reference.
     * Runs in O(1) time.
     */
    @Override
    public void clear() {
        root = null;
        size = 0;
    }

    /**
     * Returns the height of the root node of the backing AVL tree.
     * An empty tree has height -1 by convention.
     *
     * @return the height of the root, or -1 if the tree is empty
     */
    @Override
    public int height() {
        return root == null ? -1 : root.getHeight();
    }

    /**
     * Returns the number of key-value pairs currently stored in this map.
     *
     * @return the number of entries in the map
     */
    @Override
    public int size() {
        return size;
    }

    /**
     * Returns an iterator that yields values in ascending key order.
     * The iterator uses an explicit stack limited to O(log n) space and
     * runs in amortised O(1) per call with O(log n) worst case.
     *
     * @return an in-order iterator over the values of this map
     */
    @Override
    public Iterator<V> iterator() {
        return new TreeMapIterator();
    }

    /**
     * A node in the AVL-backed TreeMap holding a key-value pair together
     * with structural metadata (height, balance factor, child pointers).
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     */
    private static class TMNode<K, V> {
        private K key;

        private V value;

        private TMNode<K, V> left;

        private TMNode<K, V> right;

        private int height;

        private int balanceFactor;

        /**
         * Constructs a new TMNode with the given key and value.
         *
         * @param key   the key to store
         * @param value the value to associate with the key
         */
        TMNode(K key, V value) {
            this.key   = key;
            this.value = value;
        }

        /**
         * Returns the key stored in this node.
         *
         * @return the key
         */
        K getKey() {
            return key;
        }

        /**
         * Sets the key stored in this node.
         *
         * @param key the new key
         */
        void setKey(K key) {
            this.key = key;
        }

        /**
         * Returns the value stored in this node.
         *
         * @return the value
         */
        V getValue() {
            return value;
        }

        /**
         * Sets the value stored in this node.
         *
         * @param value the new value
         */
        void setValue(V value) {
            this.value = value;
        }

        /**
         * Returns the left child of this node.
         *
         * @return the left child, or null if none
         */
        TMNode<K, V> getLeft() {
            return left;
        }

        /**
         * Sets the left child of this node.
         *
         * @param left the new left child
         */
        void setLeft(TMNode<K, V> left) {
            this.left = left;
        }

        /**
         * Returns the right child of this node.
         *
         * @return the right child, or null if none
         */
        TMNode<K, V> getRight() {
            return right;
        }

        /**
         * Sets the right child of this node.
         *
         * @param right the new right child
         */
        void setRight(TMNode<K, V> right) {
            this.right = right;
        }

        /**
         * Returns the height of the subtree rooted at this node.
         *
         * @return the height
         */
        int getHeight() {
            return height;
        }

        /**
         * Sets the height of the subtree rooted at this node.
         *
         * @param height the new height
         */
        void setHeight(int height) {
            this.height = height;
        }

        /**
         * Returns the balance factor (leftHeight - rightHeight) of this node.
         *
         * @return the balance factor
         */
        int getBalanceFactor() {
            return balanceFactor;
        }

        /**
         * Sets the balance factor of this node.
         *
         * @param balanceFactor the new balance factor
         */
        void setBalanceFactor(int balanceFactor) {
            this.balanceFactor = balanceFactor;
        }
    }

    /**
     * A single-slot mutable container used to pass a value back up
     * the recursive call stack without requiring an array cast.
     *
     * @param <T> the type of the held value
     */
    private static final class Holder<T> {

        /** The value being held. */
        private T value;

        /**
         * Returns the held value.
         *
         * @return the held value, or null if unset
         */
        T getValue() {
            return value;
        }

        /**
         * Sets the held value.
         *
         * @param value the new value to hold
         */
        void setValue(T value) {
            this.value = value;
        }
    }

    /**
     * Iterates over map values in ascending key order using an explicit
     * stack to simulate in-order traversal without recursion.
     * Maintains O(log n) auxiliary space at all times.
     */
    private class TreeMapIterator implements Iterator<V> {

        /** Stack of nodes whose left subtrees have already been visited. */
        private final Deque<TMNode<K, V>> stack = new ArrayDeque<>();

        /**
         * Constructs a new iterator and primes the stack with the
         * left spine of the tree so that the smallest key is on top.
         */
        TreeMapIterator() {
            pushLeftSpine(root);
        }

        /**
         * Pushes the given node and all of its left descendants onto the
         * stack, so that the leftmost (smallest) node ends up on top.
         *
         * @param node the node from which to start pushing
         */
        private void pushLeftSpine(TMNode<K, V> node) {
            while (node != null) {
                stack.push(node);
                node = node.getLeft();
            }
        }

        /**
         * Returns true if there are more values to iterate over.
         *
         * @return true if the iterator has at least one more element
         */
        @Override
        public boolean hasNext() {
            return !stack.isEmpty();
        }

        /**
         * Returns the next value in ascending key order. After popping the
         * current node, the left spine of its right subtree is pushed onto
         * the stack to prepare the next in-order successor.
         *
         * @return the next value in ascending key order
         * @throws NoSuchElementException if no more elements exist
         */
        @Override
        public V next() {
            if (!hasNext()) {
                throw new NoSuchElementException(
                        "No more elements in the TreeMap.");
            }
            TMNode<K, V> node = stack.pop();
            V val = node.getValue();
            pushLeftSpine(node.getRight());
            return val;
        }
    }
}