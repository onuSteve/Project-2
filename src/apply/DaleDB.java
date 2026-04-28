package apply;

import implement.HashMap;
import refactor.TreeMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Implementation of {@link StaticDaleDB} backed by a HashMap of TreeMaps.
 * Each pond maps to an AVL-backed TreeMap keyed by timestamp, giving
 * O(log r) record lookup and O(r) ordered scans. A doubly-linked list
 * whose nodes are also stored in a HashMap provides O(1) LRU access-order
 * tracking for evict(). The head of the list is the most-recently-accessed
 * pond; the tail is the least-recently-accessed.
 *
 * @version 1.0
 * @author Stephen Onuh
 */
public class DaleDB implements StaticDaleDB {

    /**
     * Maps pond name to its record store (timestamp to DaleRecord).
     * Gives O(1) average pond lookup and O(log r) record lookup.
     */
    private final HashMap<String, TreeMap<Long, DaleRecord>> db;

    /**
     * Maps pond name to its LRU list node, for O(1) move-to-front.
     */
    private final HashMap<String, LRUNode> lruIndex;

    /** Sentinel head of the LRU list representing the MRU end. */
    private final LRUNode lruHead;

    /** Sentinel tail of the LRU list representing the LRU end. */
    private final LRUNode lruTail;

    /** Number of ponds currently in the database. */
    private int pondCount;

    /**
     * Creates an empty DaleDB with no ponds or records.
     */
    public DaleDB() {
        db       = new HashMap<>();
        lruIndex = new HashMap<>();
        lruHead  = new LRUNode(null);
        lruTail  = new LRUNode(null);
        lruHead.setNext(lruTail);
        lruTail.setPrev(lruHead);
        pondCount = 0;
    }

    /**
     * Inserts a brand-new pond node immediately after lruHead (MRU position).
     *
     * @param node the LRU node to insert at the front
     */
    private void lruInsertFront(LRUNode node) {
        node.setPrev(lruHead);
        node.setNext(lruHead.getNext());
        lruHead.getNext().setPrev(node);
        lruHead.setNext(node);
    }

    /**
     * Unlinks the given node from wherever it currently sits in the list.
     *
     * @param node the LRU node to unlink
     */
    private void lruUnlink(LRUNode node) {
        node.getPrev().setNext(node.getNext());
        node.getNext().setPrev(node.getPrev());
    }

    /**
     * Moves an existing pond node to the front of the LRU list (MRU position).
     * Called every time a pond is accessed.
     *
     * @param pond the name of the pond to mark as most recently accessed
     */
    private void lruTouch(String pond) {
        LRUNode node = lruIndex.get(pond);
        if (node == null) {
            return;
        }
        lruUnlink(node);
        lruInsertFront(node);
    }

    /**
     * Registers a new pond in the LRU list and index, placing it at the
     * front (most-recently-accessed position).
     *
     * @param pond the name of the pond to register
     */
    private void lruRegister(String pond) {
        LRUNode node = new LRUNode(pond);
        lruIndex.put(pond, node);
        lruInsertFront(node);
    }

    /**
     * Removes a pond from the LRU list and index entirely.
     *
     * @param pond the name of the pond to remove from LRU tracking
     */
    private void lruRemove(String pond) {
        LRUNode node = lruIndex.get(pond);
        if (node != null) {
            lruUnlink(node);
            lruIndex.remove(pond);
        }
    }

    /**
     * Returns the least-recently-accessed pond name,
     * i.e. the node just before the tail sentinel.
     *
     * @return the name of the least-recently-accessed pond, or null if empty
     */
    private String lruPeekLeast() {
        if (lruTail.getPrev() == lruHead) {
            return null;
        }
        return lruTail.getPrev().getPond();
    }

    /**
     * Returns the record TreeMap for the given pond without updating LRU
     * access order.
     *
     * @param pond the pond name to look up
     * @return the pond's TreeMap of records, or null if the pond does not exist
     */
    private TreeMap<Long, DaleRecord> getPondMap(String pond) {
        return db.getOrDefault(pond, null);
    }

    /**
     * Gets (or creates) the TreeMap for the given pond and registers or
     * touches its LRU entry. Always call this when accessing a pond.
     *
     * @param pond the pond name to access
     * @return the pond's TreeMap of records, creating it if necessary
     */
    private TreeMap<Long, DaleRecord> accessPond(String pond) {
        TreeMap<Long, DaleRecord> records = db.getOrDefault(pond, null);
        if (records == null) {
            records = new TreeMap<>();
            db.put(pond, records);
            lruRegister(pond);
            pondCount++;
        } else {
            lruTouch(pond);
        }
        return records;
    }

    /**
     * Removes a pond entirely from the database and the LRU tracker.
     * Called when a pond's record count drops to zero.
     *
     * @param pond the name of the pond to remove
     */
    private void removePond(String pond) {
        db.remove(pond);
        lruRemove(pond);
        pondCount--;
    }

    /**
     * Inserts or updates a record in the database. If a record with the same
     * pond and timestamp already exists it is replaced and the old record is
     * returned. Accessing the pond updates its LRU position.
     *
     * @param daleRecord the record to insert or update; must not be null
     * @return the old record if one was replaced, or null for a fresh insert
     * @throws IllegalArgumentException if {@code daleRecord} is {@code null}
     */
    @Override
    public DaleRecord putRecord(DaleRecord daleRecord) {
        if (daleRecord == null) {
            throw new IllegalArgumentException("Record must not be null.");
        }
        TreeMap<Long, DaleRecord> records = accessPond(daleRecord.pond());
        return records.put(daleRecord.timestamp(), daleRecord);
    }

    /**
     * Deletes the record with the given pond name and timestamp from the
     * database and returns it. If the pond becomes empty after deletion it
     * is removed from the database entirely.
     *
     * @param pond      the pond the record belongs to; must not be null
     * @param timestamp the timestamp of the record to delete
     * @return the deleted record
     * @throws IllegalArgumentException if {@code pond} is {@code null}
     * @throws NoSuchElementException   if no matching record exists
     */
    @Override
    public DaleRecord deleteRecord(String pond, long timestamp) {
        if (pond == null) {
            throw new IllegalArgumentException("Pond must not be null.");
        }
        TreeMap<Long, DaleRecord> records = getPondMap(pond);
        if (records == null || !records.containsKey(timestamp)) {
            throw new NoSuchElementException("Record not found.");
        }
        lruTouch(pond);
        DaleRecord removed = records.remove(timestamp);
        if (records.size() == 0) {
            removePond(pond);
        }
        return removed;
    }

    /**
     * Retrieves the record with the given pond name and timestamp without
     * removing it. Accessing the pond updates its LRU position.
     *
     * @param pond      the pond the record belongs to; must not be null
     * @param timestamp the timestamp of the record to retrieve
     * @return the record matching the given pond and timestamp
     * @throws IllegalArgumentException if {@code pond} is {@code null}
     * @throws NoSuchElementException   if no matching record exists
     */
    @Override
    public DaleRecord getRecord(String pond, long timestamp) {
        if (pond == null) {
            throw new IllegalArgumentException("Pond must not be null.");
        }
        TreeMap<Long, DaleRecord> records = getPondMap(pond);
        if (records == null || !records.containsKey(timestamp)) {
            throw new NoSuchElementException("Record not found.");
        }
        lruTouch(pond);
        return records.get(timestamp);
    }

    /**
     * Returns all records belonging to the given pond as a list sorted in
     * ascending order of timestamp. Returns an empty list if the pond does
     * not exist. Accessing the pond updates its LRU position.
     *
     * @param pond the name of the pond to retrieve; must not be null
     * @return a timestamp-sorted list of all records in the pond,
     *         or an empty list if the pond does not exist
     * @throws IllegalArgumentException if {@code pond} is {@code null}
     */
    @Override
    public List<DaleRecord> getPond(String pond) {
        if (pond == null) {
            throw new IllegalArgumentException("Pond must not be null.");
        }
        TreeMap<Long, DaleRecord> records = getPondMap(pond);
        if (records == null) {
            return new ArrayList<>();
        }
        lruTouch(pond);
        return records.values();
    }

    /**
     * Returns the number of ponds currently stored in the database.
     * Runs in O(1) time.
     *
     * @return the current pond count
     */
    @Override
    public int getPondCount() {
        return pondCount;
    }

    /**
     * Returns all records in the given pond whose timestamps fall within
     * the inclusive range [start, end], sorted in ascending order of
     * timestamp. Accessing the pond updates its LRU position.
     *
     * @param pond  the name of the pond to query; must not be null
     * @param start the inclusive lower bound of the timestamp range
     * @param end   the inclusive upper bound of the timestamp range
     * @return a timestamp-sorted list of matching records
     * @throws IllegalArgumentException if {@code pond} is {@code null}
     * @throws NoSuchElementException   if the pond does not exist
     */
    @Override
    public List<DaleRecord> getRecordRange(String pond, long start, long end) {
        if (pond == null) {
            throw new IllegalArgumentException("Pond must not be null.");
        }
        TreeMap<Long, DaleRecord> records = getPondMap(pond);
        if (records == null) {
            throw new NoSuchElementException("Pond not found.");
        }
        lruTouch(pond);
        return records.getRange(start, end);
    }

    /**
     * Evicts the k least-recently-accessed ponds from the database and
     * returns all of their records. Records are ordered first by eviction
     * order (least-recently-accessed first) and second by ascending timestamp
     * within each pond.
     *
     * @param k the number of ponds to evict; must be positive
     * @return a flat list of all records removed, ordered by eviction then timestamp
     * @throws IllegalArgumentException if {@code k} is nonpositive
     * @throws NoSuchElementException   if fewer than {@code k} ponds exist
     */
    @Override
    public List<DaleRecord> evict(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive.");
        }
        if (k > pondCount) {
            throw new NoSuchElementException("Not enough ponds to evict.");
        }
        List<DaleRecord> result = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            String leastPond = lruPeekLeast();
            TreeMap<Long, DaleRecord> records = getPondMap(leastPond);
            Iterator<DaleRecord> it = records.iterator();
            while (it.hasNext()) {
                result.add(it.next());
            }
            db.remove(leastPond);
            lruRemove(leastPond);
            pondCount--;
        }
        return result;
    }

    /**
     * Returns the maximum number of ducks that were present in the given
     * pond at the same time. Iterates all BoundaryEvent records in
     * timestamp order, adding duck counts on ENTRANCE events and subtracting
     * on EXIT events (cows are excluded using the numCows field), and tracks
     * the running peak.
     *
     * @param pond the name of the pond to analyse; must not be null
     * @return the peak simultaneous duck count
     * @throws IllegalArgumentException if {@code pond} is {@code null}
     * @throws NoSuchElementException   if the pond does not exist
     */
    @Override
    public int getPeakConcurrentOccupancy(String pond) {
        if (pond == null) {
            throw new IllegalArgumentException("Pond must not be null.");
        }
        TreeMap<Long, DaleRecord> records = getPondMap(pond);
        if (records == null) {
            throw new NoSuchElementException("Pond not found.");
        }
        lruTouch(pond);

        int current = 0;
        int peak    = 0;

        Iterator<DaleRecord> it = records.iterator();
        while (it.hasNext()) {
            DaleRecord rec = it.next();
            if (rec instanceof DaleRecord.BoundaryEvent be) {
                int ducks = be.names().size() - be.numCows();
                if (be.type() == DaleRecord.BoundaryEvent.TransitionType.ENTRANCE) {
                    current += ducks;
                    if (current > peak) {
                        peak = current;
                    }
                } else {
                    current -= ducks;
                }
            }
        }
        return peak;
    }

    /**
     * Returns the name of the animal (duck or cow) that appears most often
     * across all BoundaryEvent records in the given pond. Counts every
     * occurrence of each name in every BoundaryEvent regardless of event
     * type, and returns the name with the highest count. If multiple names
     * tie, any one of them may be returned.
     *
     * @param pond the name of the pond to analyse; must not be null
     * @return the name of the most frequent visitor, or null if none exist
     * @throws IllegalArgumentException if {@code pond} is {@code null}
     * @throws NoSuchElementException   if the pond does not exist
     */
    @Override
    public String getMostFrequentVisitor(String pond) {
        if (pond == null) {
            throw new IllegalArgumentException("Pond must not be null.");
        }
        TreeMap<Long, DaleRecord> records = getPondMap(pond);
        if (records == null) {
            throw new NoSuchElementException("Pond not found.");
        }
        lruTouch(pond);

        HashMap<String, Integer> counts = new HashMap<>();
        Iterator<DaleRecord> it = records.iterator();
        while (it.hasNext()) {
            DaleRecord rec = it.next();
            if (rec instanceof DaleRecord.BoundaryEvent be) {
                for (String name : be.names()) {
                    int prev = counts.getOrDefault(name, 0);
                    counts.put(name, prev + 1);
                }
            }
        }

        String best      = null;
        int    bestCount = 0;
        for (String name : counts) {
            int c = counts.get(name);
            if (c > bestCount) {
                bestCount = c;
                best      = name;
            }
        }
        return best;
    }

    /**
     * Merges all FishReport records for each duck within the given pond into
     * a single record per duck. The merged record sits at the duck's latest
     * timestamp and contains all fish weights from all earlier records
     * concatenated in ascending timestamp order. All earlier records for
     * that duck are deleted. Returns a map from each duck's latest timestamp
     * to the list of older timestamps that were merged into it (empty list
     * if the duck had only one record).
     *
     * @param pond the name of the pond to merge; must not be null
     * @return a HashMap where each key is a surviving latest timestamp and
     *         each value is the list of timestamps merged into it
     * @throws IllegalArgumentException if {@code pond} is {@code null}
     * @throws NoSuchElementException   if the pond does not exist
     */
    @Override
    public HashMap<Long, List<Long>> mergeReports(String pond) {
        if (pond == null) {
            throw new IllegalArgumentException("Pond must not be null.");
        }
        TreeMap<Long, DaleRecord> records = getPondMap(pond);
        if (records == null) {
            throw new NoSuchElementException("Pond not found.");
        }
        lruTouch(pond);

        HashMap<String, Long>         duckLatest  = new HashMap<>();
        HashMap<String, List<Double>> duckWeights = new HashMap<>();
        HashMap<String, List<Long>>   duckMerged  = new HashMap<>();

        Iterator<DaleRecord> it = records.iterator();
        while (it.hasNext()) {
            DaleRecord rec = it.next();
            if (!(rec instanceof DaleRecord.FishReport fr)) {
                continue;
            }
            String duck = fr.duck();
            Long   ts   = fr.timestamp();

            if (!duckLatest.containsKey(duck)) {
                List<Double> w = new ArrayList<>(fr.weights());
                duckWeights.put(duck, w);
                duckLatest.put(duck, ts);
                duckMerged.put(duck, new ArrayList<>());
            } else {
                Long oldTs = duckLatest.get(duck);
                duckMerged.get(duck).add(oldTs);
                duckWeights.get(duck).addAll(fr.weights());
                duckLatest.put(duck, ts);
            }
        }

        HashMap<Long, List<Long>> result = new HashMap<>();

        for (String duck : duckLatest) {
            Long         latestTs     = duckLatest.get(duck);
            List<Long>   mergedTsList = duckMerged.get(duck);
            List<Double> allWeights   = duckWeights.get(duck);

            for (Long oldTs : mergedTsList) {
                records.remove(oldTs);
            }

            records.remove(latestTs);
            DaleRecord merged = new DaleRecord.FishReport(
                    pond, latestTs, duck, allWeights);
            records.put(latestTs, merged);

            result.put(latestTs, mergedTsList);
        }

        return result;
    }

    // -----------------------------------------------------------------------
    // Inner class - must appear LAST per Checkstyle InnerTypeLastCheck
    // -----------------------------------------------------------------------

    /**
     * Node in the LRU doubly-linked list.
     * Stores the pond name so we can look up the record store when evicting.
     */
    private static class LRUNode {

        /** The pond name this node represents. */
        private final String pond;

        /** The previous node in the doubly-linked list. */
        private LRUNode prev;

        /** The next node in the doubly-linked list. */
        private LRUNode next;

        /**
         * Constructs a new LRUNode for the given pond name.
         *
         * @param pond the pond name to store in this node
         */
        LRUNode(String pond) {
            this.pond = pond;
        }

        /**
         * Returns the pond name stored in this node.
         *
         * @return the pond name
         */
        String getPond() {
            return pond;
        }

        /**
         * Returns the previous node in the list.
         *
         * @return the previous LRUNode
         */
        LRUNode getPrev() {
            return prev;
        }

        /**
         * Sets the previous node in the list.
         *
         * @param prev the new previous LRUNode
         */
        void setPrev(LRUNode prev) {
            this.prev = prev;
        }

        /**
         * Returns the next node in the list.
         *
         * @return the next LRUNode
         */
        LRUNode getNext() {
            return next;
        }

        /**
         * Sets the next node in the list.
         *
         * @param next the new next LRUNode
         */
        void setNext(LRUNode next) {
            this.next = next;
        }
    }
}