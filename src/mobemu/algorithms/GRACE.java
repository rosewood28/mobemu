package mobemu.algorithms;

import mobemu.node.Context;
import mobemu.node.Message;
import mobemu.node.Node;
import mobemu.utils.GraceHelper;

import java.util.*;

/**
 * Class for a GRACE node.
 *
 * GRACE (Gradient Relays via Ant Colony Exploration) is a routing algorithm that uses
 * lightweight control packets called ants to explore the network and establish
 * gradient-based routes for messages.
 */
public class GRACE extends Node {

    /**
     * Initial ant TTL
     */
    private static final long INITIAL_ANT_TTL = 4 * 60 * 60 * 1000; //4 hours

    /**
     * Initial ant pheromone strength (with what value it will update
     * the pheromone levels in the next node's pheromoneTable)
     */
    private static final double INITIAL_ANT_STRENGTH = 1;

    /**
     * Decay per tick
     */
    private static final double EVAPORATION_RATE = 0.1;

    /**
     * Minimum pheromone value to consider a node as a valid relay in the pheromone table.
     */
    private static final double MINIMUM_PHEROMONE_THRESHOLD = 0.3;

    /**
     * Size in bandwidth units of a normal message used for bandwidth calculations.
     */
    public static final int GRACE_MESSAGE_SIZE = 5;

    /**
     * Size in bandwidth units of an ant used for bandwidth calculations.
     */
    public static final int GRACE_ANT_SIZE = 1;

    /**
     * Pheromone levels indicating how frequently this node is visited by ants from various sources.
     * Used to estimate how useful this node could be as a relay for messages originating from those sources.
     */
    private Map<Integer, Double> pheromoneTable = new HashMap<>();

    /**
     * Maximum size of ant memory.
     */
    private final int antMemorySize;

    /**
     * Separate memory for ants
     */
    private List<GraceHelper.Ant> antMemory = new ArrayList<>();

    /**
     * Instantiates an {@code GRACE} object.
     *
     * @param id ID of the node
     * @param nodes total number of existing nodes
     * @param context the context of this node
     * @param socialNetwork the social network as seen by this node
     * @param antMemorySize maximum size of ant memory
     * @param dataMemorySize maximum size of data memory
     * @param exchangeHistorySize maximum size of exchange history
     * @param seed seed for the random number generator
     * @param traceStart timestamp of the start time of the trace
     * @param traceEnd timestamp of the end time of the trace
     */
    public GRACE(int id, int nodes, Context context, boolean[] socialNetwork, int antMemorySize, int dataMemorySize, int exchangeHistorySize, long seed, long traceStart, long traceEnd) {
        super(id, nodes, context, socialNetwork, dataMemorySize, exchangeHistorySize, seed, traceStart, traceEnd);

        this.antMemorySize = antMemorySize;
    }

    @Override
    public String getName() { return "GRACE"; }

    @Override
    protected void onDataExchange(Node encounteredNode, long contactDuration, long currentTime) {
        // Check and cast to the GRACE instance
        if (!(encounteredNode instanceof GRACE graceEncounteredNode)) {
            return;
        }

        // Perform the delivery of messages that are destined for the current node
        int remainingMessages = deliverDirectMessages(graceEncounteredNode, false, contactDuration, currentTime, false);

        // Convert remainingMessages to bandwidth units (1 message = GRACE_MESSAGE_SIZE units)
        double remainingCapacity = remainingMessages * GRACE_MESSAGE_SIZE;
        double antCapacity = remainingCapacity * 0.1;
        remainingCapacity -= antCapacity;
        int usedCapacity = 0;

        // First we allow ants to update the available pathways in order to take the best decision based on recent data
        for (GraceHelper.Ant ant : graceEncounteredNode.antMemory) {
            // Assure that network capacity is not exceeded
            if (usedCapacity + GRACE_ANT_SIZE > antCapacity) {
                break;
            }

            // Count successful ant transfers against bandwidth (each ant uses GRACE_ANT_SIZE bandwidth unit)
            if (insertAnt(ant, graceEncounteredNode, currentTime)) {
                usedCapacity += GRACE_ANT_SIZE;
            }
        }
        // Reset used capacity to 0 for messages transfers capacity calculations
        usedCapacity = 0;

        // Analyze messages of the encountered node's data memory and apply algorithm
        for (Message message : graceEncounteredNode.dataMemory) {
            // Assure that network capacity is not exceeded
            if (usedCapacity + GRACE_MESSAGE_SIZE > remainingCapacity) {
                return;
            }

            // Count successful messages transfers against bandwidth (each message uses GRACE_MESSAGE_SIZE bandwidth unit)
            if (shouldAcceptMessage(message)) {
                if (insertMessage(message, graceEncounteredNode, currentTime, false, false)) {
                    usedCapacity += GRACE_MESSAGE_SIZE;
                }
            }
        }

        // Analyze messages of the encountered node's own memory and apply algorithm
        for (Message message : graceEncounteredNode.ownMessages) {
            // Assure that network capacity is not exceeded
            if (usedCapacity + GRACE_MESSAGE_SIZE > remainingCapacity) {
                return;
            }

            // Count successful messages transfers against bandwidth (each message uses GRACE_MESSAGE_SIZE bandwidth unit)
            if (shouldAcceptMessage(message)) {
                if (insertMessage(message, graceEncounteredNode, currentTime, false, false)) {
                    usedCapacity += GRACE_MESSAGE_SIZE;
                }
            }
        }
    }

    /**
     * Inserts an ant into the ant memory.
     * Ants travel by creating copies of the original ant when needed.
     * If a node decides to tranfer an ant into it's own memory, it creates
     * a new copy of the ant and adds it to its own memory. From this moment
     * on, the two are separate entities that explore different paths in the network.
     * It is allowed for a node to receive multiple ants that belong to the same
     * message. This reflects the existence of multiple paths from the source
     * to the node or the strength of a particular path that attracts many ants.
     * However, a single ant copy is not allowed to visit the same node more than
     * once in order to prevent loops.
     *
     * @param ant the ant to insert in antMemory
     * @param from the node from which the ant is coming
     * @param currentTime the current time
     * @return {@code true} if the ant was transfered, {@code false} otherwise
     */
    private boolean insertAnt(GraceHelper.Ant ant, Node from, long currentTime) {
        // Skip if ant is expired
        if (ant.isExpired(currentTime)) {
            return false;
        }

        // Create a new ant copy for this new path with a new copyId
        GraceHelper.Ant newAnt = ant.clone();

        // If this copy has already visited this node, reject to prevent loops
        if (newAnt.getAntStats().hasVisited(this.id)) {
            return false;
        }

        // Mark this node as visited by this copy
        newAnt.getAntStats().markAsVisited(this.id, currentTime);

        // Update pheromone values
        double currentPheromone = pheromoneTable.getOrDefault(newAnt.getSource(), 0.0);
        double newStrength = newAnt.computeStrength(currentTime);
        pheromoneTable.put(newAnt.getSource(), currentPheromone + newStrength);

        // If ant memory is full, remove the oldest ant
        if (antMemorySize != Integer.MAX_VALUE && antMemory.size() >= antMemorySize) {
            antMemory.remove(0);
        }

        // Add ant to antMemory
        antMemory.add(newAnt);

        return true;
    }

    /**
     * Decides whether to accept a message based on the pheromone value of the message's source.
     *
     * @param message the message to accept
     * @return {@code true} if the message should be accepted, {@code false} otherwise
     */
    private boolean shouldAcceptMessage(Message message) {
        // Get pheromone value for message source
        double pheromoneValue = pheromoneTable.getOrDefault(message.getSource(), 0.0);

        // Logic to decide whether to accept the message based on pheromone values.
        return pheromoneValue > 0.1;
    }

    /**
     * At message generation, create an assigned ant for future exploration
     *
     * @param message the message to generate an ant for
     * @return the generated ant
     */
    @Override
    public Message generateMessage(Message message) {
        // Create ant
        GraceHelper.Ant ant = new GraceHelper.Ant(
                message.getSource(),
                message.getDestination(),
                message.getId(),
                message.getTimestamp(),
                INITIAL_ANT_TTL,
                INITIAL_ANT_STRENGTH
        );

        // Add ant in antMemory for future spreading in the network
        antMemory.add(ant);

        // Add GRACE message in own memory
        ownMessages.add(message);

        return message;
    }

    /**
     * Updates the pheromone table and removes expired ants.
     *
     * @param currentTime the current time
     * @param sampleTime the sample time
     */
    @Override
    protected void onTick(long currentTime, long sampleTime) {
        super.onTick(currentTime, sampleTime);

        // Decay pheromone levels of all nodes
        for (Map.Entry<Integer, Double> entry : pheromoneTable.entrySet()) {
            pheromoneTable.put(entry.getKey(), entry.getValue() * (1 - EVAPORATION_RATE));
        }

        // Remove all expired ants
        antMemory.removeIf(ant -> ant.isExpired(currentTime));

        // Remove all node entries with pheromone value below the threshold
        pheromoneTable.entrySet().removeIf(entry -> entry.getValue() < MINIMUM_PHEROMONE_THRESHOLD);
    }

    /**
     * Checks if the ant memory of this node contains a specific ant.
     *
     * @param otherAnt the ant to check for
     * @return {@code true} if the ant is in the memory, {@code false} otherwise
     */
    public boolean hasAnt(GraceHelper.Ant otherAnt) {
        for (GraceHelper.Ant ant : antMemory) {
            if (ant.equals(otherAnt)) {
                return true;
            }
        }

        return false;
    }

    public void printPheromoneTable() {
        if (pheromoneTable.isEmpty()) {
            System.out.println("Pheromone table is empty.");
            return;
        }

        System.out.println("Pheromone Table Contents:");
        for (Map.Entry<Integer, Double> entry : pheromoneTable.entrySet()) {
            System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue());
        }
    }
}

