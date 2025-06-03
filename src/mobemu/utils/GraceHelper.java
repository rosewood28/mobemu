package mobemu.utils;

import mobemu.node.Message;

import java.util.*;

public class GraceHelper {

    /**
     * Size of a normal message in a GRACE network used for bandwidth calculations.
     */
    public static final int GRACE_MESSAGE_SIZE = 5;

    /**
     * Size of an ant agent in a GRACE network used for bandwidth calculations.
     */
    public static final int GRACE_ANT_SIZE = 1;

    /**
     * Extension of the standard message class that includes a customizable size field.
     * This allows distinguishing between regular data messages and lightweight ant agents
     * based on their conceptual bandwidth usage.
     */
    public static class GraceMessage extends Message {
        private int size = GRACE_MESSAGE_SIZE;

        public GraceMessage(int source, int destination, String message, long timestamp, int copies) {
            super(source, destination, message, timestamp, copies);
        }

        /**
         * Gets the size of the message.
         *
         * @return the size of the message
         */
        public int getSize() {
            return size;
        }
    }

    /**
     * Ants are lightweight agents that are spread in the network upon message generation
     * in order to update pheromone levels in the nodes.
     * These will travel like messages but are not considered messages since they serve a
     * different purpose and should not skew the metrics and statistics mobemu computes for those.
     */
    public static class Ant {

        /**
         * Id of the ant.
         */
        private final int antId;

        /**
         * Id of the origin node of the ant.
         */
        private final int source;

        /**
         * Id of the destination of the message and implicitely the node these ants
         * are looking for.
         */
        private final int destination;

        /**
         * Id of the message this ant was generated for.
         */
        private final int messageID;

        /**
         * Time when the ant was generated.
         */
        private final long timestamp;

        /**
         * Time-to-live of an ant (how long before expiration).
         */
        private long ttl;

        /**
         * The value this ant will use to update the pheromones of the next node it will visit.
         * This values decreases with every hop since the further we get fromm the source the more
         * reasons to having the path back already interrupted or changed.
         */
        private double strength;

        /**
         * Statistics of this ant.
         */
        private AntStats antStats;

        /**
         * Fixed size for every ant used for bandwidth calculations.
         */
        private final int size = GRACE_ANT_SIZE;

        /**
         * Base for the pheromone strength decay factor.
         */
        private static final double DECAY_FACTOR_BASE = 1000.0;

        /**
         * Total number of ants generated.
         */
        private static int antCount = 0;

        /**
         * Constructor for a {@link Ant} object.
         *
         * @param source          the sender of the message that generated the ant
         * @param destination     the destination of the message and the nodes the ants are looking for
         * @param timestamp       time of the ant generation
         * @param initialTTL      time-to-live of the ant
         * @param initialStrength initial strength of the ant
         * @param copies          number of initial copies of the ant
         */
        public Ant(int source, int destination, int messageID, long timestamp, long initialTTL, double initialStrength, int copies) {
            this.antId = antCount++;
            this.source = source;
            this.destination = destination;
            this.messageID = messageID;
            this.timestamp = timestamp;
            this.ttl = initialTTL;
            this.strength = initialStrength;
            this.antStats = new AntStats(copies, source);
        }

        /**
         * Computes pheromone strength this ant will add to the next node it visits based on elapsed time
         * since creation of the ant.
         *
         * @param currentTime Current simulation time.
         * @return Decayed strength (exponential decay in this case
         */
        public double computeStrength(long currentTime) {
            long elapsed = currentTime - timestamp;
            if (elapsed > this.ttl) return 0.0;

            // Exponential decay
            double decayFactor = Math.exp(-elapsed / DECAY_FACTOR_BASE);
            return this.strength * decayFactor;
        }

        /**
         * Updates the strength of the ant.
         *
         * @param newStrength new strength of the ant
         */
        public void updateStrength(double newStrength) {
            this.strength = newStrength;
        }

        /**
         * Checks if the current ant is expired or not
         *
         * @param currentTime Current simulation time.
         * @return {@code true} if expired or {@code false} if not
         */
        public boolean isExpired(long currentTime) {
            return (currentTime - timestamp) > ttl;
        }

        /**
         * Checks if two ants are equal.
         *
         * @param o the object to compare
         * @return {@code true} if equal, {@code false} otherwise
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Ant ant = (Ant) o;
            return antId == ant.antId;
        }

        /**
         * Gets the hash code of the ant.
         *
         * @return the hash code of the ant
         */
        @Override
        public int hashCode() {
            return Objects.hash(antId);
        }

        /**
         * Clones the ant.
         *
         * @return the cloned ant
         */
        public Ant clone() {
            Ant clone = new Ant(this.source, this.destination, this.messageID,
                    this.timestamp, this.ttl, this.strength,
                    this.antStats.getCopies(this.source));

            return clone;
        }

        /**
         * Gets the id of the ant.
         *
         * @return the id of the ant
         */
        public int getAntId() {
            return antId;
        }

        /**
         * Gets the source id of the ant.
         *
         * @return the source of the ant
         */
        public int getSource() {
            return source;
        }

        /**
         * Gets the destination id of the ant.
         *
         * @return the destination of the ant
         */
        public int getDestination() {
            return destination;
        }

        /**
         * Gets the id of the message this ant was generated for.
         *
         * @return the id of the message
         */
        public int getMessageID() {
            return messageID;
        }

        /**
         * Gets the timestamp when the ant was generated.
         *
         * @return the timestamp of the ant generation
         */
        public long getTimestamp() {
            return timestamp;
        }

        /**
         * Gets the time-to-live of the ant.
         *
         * @return the time-to-live of the ant
         */
        public long getTtl() {
            return ttl;
        }

        /**
         * Gets the strength this ant will update
         * the future encountered node's pheromone value.
         *
         * @return the strength of the ant
         */
        public double getStrength() {
            return strength;
        }

        /**
         * Gets the statistics of the ant.
         *
         * @return the statistics of the ant
         */
        public AntStats getAntStats() {
            return antStats;
        }

        /**
         * Gets the size of the ant.
         *
         * @return the size of the ant
         */
        public int getSize() {
            return size;
        }

    }

    /**
     * Class that tracks the statistics of an ant.
     */
    public static class AntStats {
        /**
         * Number of hops an ant has until now per node.
         */
        private Map<Integer, Integer> hops;

        /**
         * Number of copies of this ant at each node.
         */
        private Map<Integer, Integer> copies;

        /**
         * List of nodes this ant has visited.
         */
        private Set<Integer> visited;

        /**
         * When each node was visited.
         */
        private Map<Integer, Long> visitTime;

        /**
         * Constructor for a {@link AntStats} object.
         *
         * @param initialCopies number of initial copies
         * @param sourceId      ID of the node that generates the ant
         */
        public AntStats(int initialCopies, int sourceId) {
            this.copies = new HashMap<>();
            this.copies.put(sourceId, initialCopies);   // Initial copies at source node
            this.hops = new HashMap<>();
            this.visited = new HashSet<>();
            this.visitTime = new HashMap<>();
        }

        /**
         * Gets the number of copies of this ant at a given node.
         *
         * @param nodeId ID of the node
         * @return number of copies
         */
        public int getCopies(int nodeId) {
            Integer result = copies.get(nodeId);
            return result == null ? 0 : result;
        }

        /**
         * Sets a new value for the number of copies of an ant at a given node.
         *
         * @param nodeId ID of the node
         * @param value  new value for copies
         */
        public void setCopies(int nodeId, int value) {
            copies.put(nodeId, value);
        }

        /**
         * Deletes all copies of this ant at the given node.
         *
         * @param nodeId ID of the node from which the ant is deleted
         */
        public void deleteCopies(int nodeId) {
            if (copies.containsKey(nodeId)) {
                copies.put(nodeId, 0);
            }
        }

        /**
         * Duplicates the copies of an ant from a given node to another one.
         *
         * @param from source node ID
         * @param to   destination node ID
         */
        public void copy(int from, int to) {
            copies.put(to, copies.get(from));
        }

        /**
         * Marks a node as visited by this ant.
         *
         * @param nodeId         ID of the node visited
         * @param visitTimestamp timestamp of the visit
         */
        public void markAsVisited(int nodeId, long visitTimestamp) {
            visited.add(nodeId);
            increaseHopCount(nodeId);
            visitTime.put(nodeId, visitTimestamp);
        }

        /**
         * Increases the number of hops an ant has.
         * Can also be seen as a path that registers every node and
         * the number of hops an ant had until visiting that node.
         *
         * @param nodeId ID of the node
         * @return new number of hops
         */
        public int increaseHopCount(int nodeId) {
            Integer hopsCount = hops.get(nodeId);
            if (hopsCount == null) {
                hopsCount = 0;
            }
            hopsCount++;
            hops.put(nodeId, hopsCount);
            return hopsCount;
        }

        /**
         * Checks if the ant has visited a given node.
         *
         * @param nodeId ID of the node
         * @return {@code true} if visited, {@code false} otherwise
         */
        public boolean hasVisited(int nodeId) {
            return visited.contains(nodeId);
        }

        /**
         * Gets the number of hops the ant has until now per node.
         *
         * @return the number of hops
         */
        public Map<Integer, Integer> getHops() {
            return hops;
        }

        /**
         * Gets the number of copies of the ant at each node.
         *
         * @return the number of copies
         */
        public Map<Integer, Integer> getCopies() {
            return copies;
        }

        /**
         * Gets the nodes visited by the ant.
         *
         * @return the nodes visited
         */
        public Set<Integer> getVisited() {
            return visited;
        }

        /**
         * Gets the time when each node was visited by the ant.
         *
         * @return the time when each node was visited
         */
        public Map<Integer, Long> getVisitTime() {
            return visitTime;
        }
    }
}
