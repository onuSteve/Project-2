package implement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Your implementation of a Linear Probing HashMap. Must implement {@link Iterable}.
 */
public class HashMap<K, V> implements Iterable<K> {

    /**
     * The initial capacity of the LinearProbingHashMap when created with the
     * default constructor.
     *
     * DO NOT MODIFY THIS VARIABLE!
     */
    public static final int INITIAL_CAPACITY = 13;

    /**
     * The max load factor of the LinearProbingHashMap
     *
     * DO NOT MODIFY THIS VARIABLE!
     */
    public static final double MAX_LOAD_FACTOR = 0.67;

    private MapEntry<K, V>[] table;
    private int size;

    /**
     * Constructs a new Linear Probing HashMap.
     *
     * The backing array should have an initial capacity of {@code INITIAL_CAPACITY}.
     *
     * Use constructor chaining.
     */
    public HashMap() {
        this(INITIAL_CAPACITY);
    }

    /**
     * Constructs a new LinearProbingHashMap.
     *
     * The backing array should have an initial capacity of initialCapacity.
     *
     * You may assume initialCapacity will always be positive.
     *
     * @param initialCapacity the initial capacity of the backing array
     */
    @SuppressWarnings("unchecked")
    public HashMap(int initialCapacity) {
        table = new MapEntry[initialCapacity];
        size = 0;
    }

    /**
     * Adds the given key-value pair to the map. If an entry in the map
     * already has this key, replace the entry's value with the new one
     * passed in.
     *
     * In the case of a collision, use linear probing as your resolution
     * strategy. See the PDF for more instructions on edge cases and resizing.
     *
     * If a value was updated, return the old value; otherwise, return null.
     *
     * @param key   the key to add
     * @param value the value to add
     * @return null if the key was not already in the map. If it was in the
     * map, return the old value associated with it
     * @throws IllegalArgumentException if key or value is null
     */
    public V put(K key, V value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value must not be null.");
        }

        // Resize BEFORE inserting if adding would exceed load factor
        if ((double) (size + 1) / table.length > MAX_LOAD_FACTOR) {
            resizeBackingTable(2 * table.length + 1);
        }

        int startIndex = Math.abs(key.hashCode() % table.length);
        int delIndex = -1; // Track first DEL slot encountered

        for (int i = 0; i < table.length; i++) {
            int idx = (startIndex + i) % table.length;
            MapEntry<K, V> entry = table[idx];

            if (entry == null) {
                // Empty slot: insert here (or at DEL slot if found earlier)
                if (delIndex != -1) {
                    table[delIndex] = new MapEntry<>(key, value);
                } else {
                    table[idx] = new MapEntry<>(key, value);
                }
                size++;
                return null;
            } else if (entry.isRemoved()) {
                // DEL flag: record first DEL position, keep probing for duplicate key
                if (delIndex == -1) {
                    delIndex = idx;
                }
                // Check if the key matches even on a DEL entry
                if (entry.getKey().equals(key)) {
                    // Key was deleted; treat as insert at first DEL slot
                    table[delIndex] = new MapEntry<>(key, value);
                    size++;
                    return null;
                }
            } else if (entry.getKey().equals(key)) {
                // Found existing key: update value
                V oldValue = entry.getValue();
                entry.setValue(value);
                return oldValue;
            }
        }

        // Entire table probed (shouldn't happen after resize, but handle DEL case)
        if (delIndex != -1) {
            table[delIndex] = new MapEntry<>(key, value);
            size++;
        }
        return null;
    }

    /**
     * Removes the entry with a matching key from map by marking the entry as
     * removed.
     *
     * @param key the key to remove
     * @return the value previously associated with the key
     * @throws IllegalArgumentException if key is null
     * @throws NoSuchElementException   if the key is not in the map
     */
    public V remove(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null.");
        }

        int startIndex = Math.abs(key.hashCode() % table.length);

        for (int i = 0; i < table.length; i++) {
            int idx = (startIndex + i) % table.length;
            MapEntry<K, V> entry = table[idx];

            if (entry == null) {
                // Hit a true null: key definitely not in map
                throw new NoSuchElementException("Key not found in the map.");
            } else if (!entry.isRemoved() && entry.getKey().equals(key)) {
                // Found it: soft-remove
                V oldValue = entry.getValue();
                entry.setRemoved(true);
                size--;
                return oldValue;
            }
            // If removed or different key, keep probing
        }

        throw new NoSuchElementException("Key not found in the map.");
    }

    /**
     * Gets the value associated with the given key.
     *
     * @param key the key to search for in the map
     * @return the value associated with the given key
     * @throws IllegalArgumentException if key is null
     * @throws NoSuchElementException   if the key is not in the map
     */
    public V get(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null.");
        }

        int startIndex = Math.abs(key.hashCode() % table.length);

        for (int i = 0; i < table.length; i++) {
            int idx = (startIndex + i) % table.length;
            MapEntry<K, V> entry = table[idx];

            if (entry == null) {
                throw new NoSuchElementException("Key not found in the map.");
            } else if (!entry.isRemoved() && entry.getKey().equals(key)) {
                return entry.getValue();
            }
        }

        throw new NoSuchElementException("Key not found in the map.");
    }

    /**
     * Gets the value associated with the given key, or returns a provided default value.
     *
     * @param key          the key to search for in the map
     * @param defaultValue the value to return if key not found
     * @return the value associated with the given key if found,
     * {@code defaultValue} otherwise
     * @throws IllegalArgumentException if key is null
     */
    public V getOrDefault(K key, V defaultValue) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null.");
        }

        int startIndex = Math.abs(key.hashCode() % table.length);

        for (int i = 0; i < table.length; i++) {
            int idx = (startIndex + i) % table.length;
            MapEntry<K, V> entry = table[idx];

            if (entry == null) {
                return defaultValue;
            } else if (!entry.isRemoved() && entry.getKey().equals(key)) {
                return entry.getValue();
            }
        }

        return defaultValue;
    }

    /**
     * Returns whether or not the key is in the map.
     *
     * @param key the key to search for in the map
     * @return true if the key is contained within the map, false
     * otherwise
     * @throws IllegalArgumentException if key is null
     */
    public boolean containsKey(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null.");
        }

        int startIndex = Math.abs(key.hashCode() % table.length);

        for (int i = 0; i < table.length; i++) {
            int idx = (startIndex + i) % table.length;
            MapEntry<K, V> entry = table[idx];

            if (entry == null) {
                return false;
            } else if (!entry.isRemoved() && entry.getKey().equals(key)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns a Set view of the keys contained in this map.
     *
     * Use java.util.HashSet.
     *
     * @return the set of keys in this map
     */
    public Set<K> keySet() {
        Set<K> keys = new HashSet<>();
        for (MapEntry<K, V> entry : table) {
            if (entry != null && !entry.isRemoved()) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    /**
     * Returns a List view of the values contained in this map.
     *
     * Use java.util.ArrayList or java.util.LinkedList.
     *
     * You should iterate over the table in order of increasing index and add
     * entries to the List in the order in which they are traversed.
     *
     * @return list of values in this map
     */
    public List<V> values() {
        List<V> vals = new ArrayList<>();
        for (MapEntry<K, V> entry : table) {
            if (entry != null && !entry.isRemoved()) {
                vals.add(entry.getValue());
            }
        }
        return vals;
    }

    /**
     * Resize the backing table to length.
     *
     * Disregard the load factor for this method. So, if the passed in length is
     * smaller than the current capacity, and this new length causes the table's
     * load factor to exceed MAX_LOAD_FACTOR, you should still resize the table
     * to the specified length.
     *
     * See the PDF for more details.
     *
     * @param length new length of the backing table
     * @throws IllegalArgumentException if length is less than the
     *                                  number of items in the hash map
     */
    @SuppressWarnings("unchecked")
    public void resizeBackingTable(int length) {
        if (length < size) {
            throw new IllegalArgumentException(
                    "New length cannot be less than the number of items in the map.");
        }

        MapEntry<K, V>[] newTable = new MapEntry[length];

        for (MapEntry<K, V> entry : table) {
            if (entry != null && !entry.isRemoved()) {
                // Re-hash into new table
                int idx = Math.abs(entry.getKey().hashCode() % length);
                while (newTable[idx] != null) {
                    idx = (idx + 1) % length;
                }
                newTable[idx] = new MapEntry<>(entry.getKey(), entry.getValue());
            }
        }

        table = newTable;
    }

    /**
     * Clears the map.
     *
     * Resets the table to a new array of the INITIAL_CAPACITY and resets the
     * size.
     *
     * Must be O(1).
     */
    @SuppressWarnings("unchecked")
    public void clear() {
        table = new MapEntry[INITIAL_CAPACITY];
        size = 0;
    }

    /**
     * Returns the table of the map.
     *
     * For grading purposes only. You shouldn't need to use this method since
     * you have direct access to the variable.
     *
     * @return the table of the map
     */
    public MapEntry<K, V>[] getTable() {
        // DO NOT MODIFY THIS METHOD!
        return table;
    }

    /**
     * Returns the size of the map.
     *
     * For grading purposes only. You shouldn't need to use this method since
     * you have direct access to the variable.
     *
     * @return the size of the map
     */
    public int size() {
        // DO NOT MODIFY THIS METHOD!
        return size;
    }

    /**
     * Returns an iterator of all keys in the HashMap. The order should be the
     * order they appear in the array from left to right.
     * <p>
     * Must be done in O(1) auxiliary space.
     *
     * @return an iterator of all keys in the HashMap
     * @implNote you may create a private inner class to implement {@link Iterator}
     */
    public Iterator<K> iterator() {
        return new HashMapIterator();
    }

    /**
     * Private inner class implementing Iterator for HashMap keys.
     * Uses O(1) auxiliary space by tracking only a single index.
     */
    private class HashMapIterator implements Iterator<K> {
        private int currentIndex;

        /**
         * Constructs a new iterator positioned at the first valid entry.
         */
        HashMapIterator() {
            currentIndex = 0;
            advanceToNext();
        }

        /**
         * Advances currentIndex to the next valid (non-null, non-removed) entry.
         */
        private void advanceToNext() {
            while (currentIndex < table.length
                    && (table[currentIndex] == null
                    || table[currentIndex].isRemoved())) {
                currentIndex++;
            }
        }

        /**
         * Returns true if there are more keys to iterate.
         *
         * @return true if the iterator has more elements
         */
        @Override
        public boolean hasNext() {
            return currentIndex < table.length;
        }

        /**
         * Returns the next key in left-to-right array order.
         *
         * @return the next key
         * @throws NoSuchElementException if no more elements exist
         */
        @Override
        public K next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more elements in the HashMap.");
            }
            K key = table[currentIndex].getKey();
            currentIndex++;
            advanceToNext();
            return key;
        }
    }
}