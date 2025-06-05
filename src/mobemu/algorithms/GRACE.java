package mobemu.algorithms;

import mobemu.node.Context;
import mobemu.node.Message;
import mobemu.node.Node;
import mobemu.utils.GraceHelper;

import java.util.*;

public class GRACE extends Node {
    /**
     * Maximum size of the ant memory.
     */
    private final int antMemorySize;

    // Separate memory for ants
    private List<GraceHelper.Ant> antMemory = new ArrayList<>();

    // Pheromone levels indicating how frequently this node is visited by ants from various sources.
    // Used to estimate how useful this node could be as a relay for messages originating from those sources.
    private Map<Integer, Double> pheromoneTable = new HashMap<>();

    // Decay per tick
    private static final double EVAPORATION_RATE = 0.1;

    // Interval between ant exchanges (ex. 5 sec)
    private static final long ANT_EXCHANGE_INTERVAL = 5000;

    // Initial ant TTL
    private static final long INITIAL_ANT_TTL = 48 * 60 * 60 * 1000; //3 hours

    // Initial ant strength
    private static final double INITIAL_ANT_STRENGTH = 1;

    // Initial ant copies for every generated message
    private static final int INITIAL_ANT_COPIES = 3;

    /**
     * Size in bandwidth units of a normal message used for bandwidth calculations.
     */
    public static final int GRACE_MESSAGE_SIZE = 5;

    /**
     * Size in bandwidth units of an ant used for bandwidth calculations.
     */
    public static final int GRACE_ANT_SIZE = 1;

    /**
     * Minimum pheromone value to consider a node as a valid relay in the pheromone table.
     */
    private static final double MINIMUM_PHEROMONE_THRESHOLD = 0.001;

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

        // Convert to bandwidth units (1 message = 5 units, so total capacity = messages * 5)
        double remainingCapacity = remainingMessages * GRACE_MESSAGE_SIZE;
        double antCapacity = remainingCapacity * 0.1;
        remainingCapacity -= antCapacity;
        int usedCapacity = 0;

        // First we allow ants to update the available pathways in order to take the best decision based on recent data
        for (GraceHelper.Ant ant : graceEncounteredNode.antMemory) {
            System.out.println("this is good");
            // Assure that network capacity is not exceeded
            if (usedCapacity + GRACE_ANT_SIZE > antCapacity) {
                break;
            }

            // Count successful ant transfers against bandwidth (each ant uses GRACE_ANT_SIZE bandwidth unit)
            if (insertAnt(ant, graceEncounteredNode, currentTime)) {
                usedCapacity += GRACE_ANT_SIZE;
            }
        }
        // Reset used capacity to 0 for messages transfers
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

    private boolean insertAnt(GraceHelper.Ant ant, Node from, long currentTime) {
        // Skip if ant is expired
        if (ant.isExpired(currentTime)) {
            System.out.println("ant is expired lol");
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

        printPheromoneTable();

        // If ant memory is full, remove the oldest ant
        if (antMemorySize != Integer.MAX_VALUE && antMemory.size() >= antMemorySize) {
            antMemory.remove(0);
        }

        // Add ant to antMemory
        antMemory.add(newAnt);

        return true;
    }

    private boolean shouldAcceptMessage(Message message) {
        // Get pheromone value for message source
        double pheromoneValue = pheromoneTable.getOrDefault(message.getSource(), 0.0);

        //System.out.println(pheromoneValue);
        // Logic to decide whether to accept the message based on pheromone values.
        return pheromoneValue > 0.1;
    }

    // At message generation, create an assigned ant for future exploration
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
        //pheromoneTable.entrySet().removeIf(entry -> entry.getValue() < MINIMUM_PHEROMONE_THRESHOLD);
    }

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

