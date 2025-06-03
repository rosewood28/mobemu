package mobemu.utils;

import mobemu.node.Message;

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
    }

    /**
     * Ants are lightweight agents that are spread in the network upon message generation
     * in order to update pheromone levels in the nodes.
     * These will travel like messages but are not considered messages since they serve a
     * different purpose and should not skew the metrics and statistics mobemu computes for those.
     */
    public static class Ant {

        /**
         * Id of the origin node of the ant.
         */
        private final int sourceId;

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
         * Fixed size for every ant used for bandwidth calculations.
         */
        private final int size = GRACE_ANT_SIZE;

        /**
         * Constructor for a {@link Ant} object.
         *
         * @param source      the sender of the message that generated the ant
         * @param destination the destination of the message and the nodes the ants are looking for
         * @param timestamp   time of the ant generation
         */
        public Ant(int source, int destination, int messageID, long timestamp, long initialTTL, double initialStrength) {
            this.sourceId = source;
            this.destination = destination;
            this.messageID = messageID;
            this.timestamp = timestamp;
            this.ttl = initialTTL;
            this.strength = initialStrength;
        }

        /**
         * Computes pheromone strength this ant will add to the next node it visits based on elapsed time
         * since creation of the ant.
         * @param currentTime Current simulation time.
         * @return Decayed strength (exponential decay in this case
         */
        public double computeStrength(long currentTime) {
            long elapsed = currentTime - timestamp;
            if (elapsed > this.ttl) return 0.0; // expired ants contribute nothing

            // Exponential decay for now, maybe change later
            double decayFactor = Math.exp(-elapsed / 1000.0); //decay per second
            return this.strength *decayFactor;
        }

        /**
         * Checks if the current ant is expired or not
         * @param currentTime Current simulation time.
         * @return {@code true} if expired or {@code false} if not
         */
        public boolean isExpired(long currentTime) {
            return (currentTime - timestamp) > ttl;
        }

        public int getSourceId() {
            return sourceId;
        }

        public int getDestination() {
            return destination;
        }

        public int getMessageID() {
            return messageID;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public long getTtl() {
            return ttl;
        }

        public double getStrength() {
            return strength;
        }

        public int getSize() {
            return size;
        }
    }
}
