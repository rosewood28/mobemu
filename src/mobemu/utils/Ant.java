package mobemu.utils;


import mobemu.node.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for ant inspired algorithms.
 */
public class Ant {
    /**
     * Class for an ant message. This is a lightweight packet that only travels through nodes
     * in order to find the best paths to the destination and gather routing information.
     * It does not carry the message or other metrics in order to minimize network overhead.
     */
    public static class AntMessage extends Message {
        /**
         * Unique ant identifier
         */
        private int antId;

        /**
         * List containing information about the path this ant takes
         * and stores the knowledge it gathered along the way.
         */
        private AntMessageInfo info;

        /**
         * Instantiates a {@code AntMessage} object.
         */
        public AntMessage(int antId, int messageId, int source, int destination) {
            this.antId = antId;
            this.destination = destination;
            info = new AntMessageInfo(antId, messageId, source, destination);
        }
    }

    /**
     * Class for storing information about ant messages.
     */
    public static class AntMessageInfo {
        /**
         * Final destination the ant is searching for.
         */
        private int destination;

        /**
         * message ID the ant is searching the destination for
         */
        private  int messageId;

        /**
         * List containing the nodes that this message has passed through
         * (including the source and the current carrier).
         */
        private List<Integer> path;

        /**
         * Specifies if the ant is searching for the destination or is
         * returning to its source node.
         */
        private boolean isBackwardAnt;

        /**
         * Specifies whether the message has reached its destination.
         */
        private boolean reachedDestination;

        /**
         * List containing better relay nodes (than the source) that this
         * message has passed through (can include the carrier).
         * Computed only if destination was not found.
         * Only backward ants update this list to avoid unnecessary computation.
         */
        private List<Integer> bestRelays;

        /**
         * Instantiates a {@code AntMessageInfo} object.
         *
         * @param id message ID
         * @param source message source
         */
        public AntMessageInfo(int id, int messageId, int source, int destination) {
            this.messageId = messageId;
            this.destination = destination;
            this.path = new ArrayList<>();
            this.path.add(source);
            this.isBackwardAnt = false; // starts as a forward ant
            this.reachedDestination = false;
            this.bestRelays = new ArrayList<>();
        }

        /**
         * Gets the message's ID.
         *
         * @return the ID of the message
         */
        public int getMessageId() {
            return messageId;
        }

        /**
         * Gets the current owner of this message.
         *
         * @return the ID of this message's owner
         */
        public int getOwner() {
            return path.get(path.size() - 1);
        }

        /**
         * Gets the path of this ant.
         *
         * @return list containing the IDs of the nodes this ant has visited
         */
        public List<Integer> getPath() {
            return path;
        }

        /**
         * Checks whether this ant has found the message destination.
         *
         * @return {@code true} if the ant has reached its destination,
         * {@code false} otherwise
         */
        public boolean hasReachedDestination() {
            return reachedDestination;
        }

        /**
         * Add another node to this ant's path.
         *
         * @param node ID of the node to be added
         */
        public void addToPath(int node) {
            path.add(node);
        }

        /**
         * Mark this ant as having reached its destination.
         */
        public void reachedDestination() {
            reachedDestination = true;
        }
    }
}
