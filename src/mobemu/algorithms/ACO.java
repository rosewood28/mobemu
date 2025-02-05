package mobemu.algorithms;

import mobemu.node.Context;
import mobemu.node.Message;
import mobemu.node.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Class for classic Ant Colony Optimization Routing node.
 *
 * Nodes update pheromone values for their encountered nodes. The paths with
 * higher pheromone values are most likely to reach the destination.
 */
public class ACO extends Node{
    /**
     * Default pheromone value for each node
     */
    private static final double INITIAL_PHEROMONE = 1.0;
    /**
     * The pheromone decay rate each node loses on each tick
     */
    private static final double EVAPORATION_RATE = 0.1;
    /**
     * The pheromone boost that an encountered node receives upon contact
     */
    private static final double PHEROMONE_BOOST = 2.0;
    /**
     * Random value for pheromone computations
     */
    private static Random pheromoneRandom = null;

    /**
     * Map for every encountered node id and the node's pheromone level as seen by
     * the current node.
     */
    private Map<Integer, Double> pheromoneTable;

    /**
     * Instantiates a {@code ACO} object.
     *
     * @param id ID of the node
     * @param nodes total number of existing nodes
     * @param context the context of this node
     * @param socialNetwork the social network as seen by this node
     * @param dataMemorySize the maximum allowed size of the data memory
     * @param exchangeHistorySize the maximum allowed size of the exchange history
     * @param seed the seed for the random number generators
     * @param traceStart timestamp of the start of the trace
     * @param traceEnd timestamp of the end of the trace
     */
    public ACO(int id, int nodes, Context context, boolean[] socialNetwork, int dataMemorySize, int exchangeHistorySize, long seed, long traceStart, long traceEnd) {
        super(id, nodes, context, socialNetwork, dataMemorySize, exchangeHistorySize, seed, traceStart, traceEnd);

        this.pheromoneTable = new HashMap<>();
        this.pheromoneRandom = new Random(seed);
    }

    @Override
    public String getName() { return "Ant Colony Optimization Routing (ACO)"; }


    @Override
    public void onDataExchange(Node encounteredNode, long contactDuration, long currentTime) {
        // Check and cast to the ACO instance
        if (!(encounteredNode instanceof ACO)) {
            return;
        }

        ACO acoEncounteredNode = (ACO) encounteredNode;

        // Perform the delivery of messages that are destined for the current node
        int remainingMessages = deliverDirectMessages(acoEncounteredNode, false, contactDuration, currentTime, false);
        int totalMessages = 0;

        // Update pheromone levels based on successful contacts
        updatePheromones(acoEncounteredNode.getId());

        // Analyze messages of the encountered node's data memory and apply algorithm
        for (Message message : acoEncounteredNode.dataMemory) {
            // Assure that network capacity is not exceeded
            if (totalMessages >= remainingMessages) {
                return;
            }

            // ACO mechanism to decide which messages are downloaded
            if (shouldForwardMessage(message, acoEncounteredNode)) {
                if (insertMessage(message, acoEncounteredNode, currentTime, false, false)) {
                    totalMessages++;
                }
            }
        }

        // Analyze messages of the encountered node's own memory and apply algorithm
        for (Message message : acoEncounteredNode.ownMessages) {
            // Assure that network capacity is not exceeded
            if (totalMessages >= remainingMessages) {
                return;
            }

            // ACO mechanism to decide which messages are downloaded
            if (shouldForwardMessage(message, acoEncounteredNode)) {
                if (insertMessage(message, acoEncounteredNode, currentTime, false, false)) {
                    totalMessages++;
                }
            }
        }
    }

    /**
     * Evaporates pheromone values over time to prevent over-concentration on certain paths.
     */
    @Override
    protected void onTick(long currentTime, long sampleTime) {
        super.onTick(currentTime, sampleTime);

        for (Map.Entry<Integer, Double> entry : pheromoneTable.entrySet()) {
            pheromoneTable.put(entry.getKey(), entry.getValue() * (1 - EVAPORATION_RATE));
        }
    }

    /**
     * Updates pheromone levels when a contact occurs.
     *
     * @param nodeId ID of the encountered node
     */
    private void updatePheromones(int nodeId) {
        pheromoneTable.putIfAbsent(nodeId, INITIAL_PHEROMONE);
        pheromoneTable.put(nodeId, pheromoneTable.get(nodeId) + PHEROMONE_BOOST);
    }

    /**
     * Determines whether a message should be forwarded based on pheromone probability.
     *
     * @param message The message being considered for forwarding
     * @param encounteredNode The node that was encountered
     * @return {@code true} if the message should be forwarded, {@code false} otherwise
     */
    private boolean shouldForwardMessage(Message message, ACO encounteredNode) {
        double pheromoneLevel = pheromoneTable.getOrDefault(encounteredNode.getId(), INITIAL_PHEROMONE);
        double probability = pheromoneLevel / (pheromoneLevel + 1.0); // Normalize probability

        return pheromoneRandom.nextDouble() < probability;
    }
}

