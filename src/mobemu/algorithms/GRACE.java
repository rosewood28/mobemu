package mobemu.algorithms;

import mobemu.node.Context;
import mobemu.node.Message;
import mobemu.node.Node;
import mobemu.utils.GraceHelper;

import java.util.*;

public class GRACE extends Node {

    // Separate memory for ants
    private List<GraceHelper.Ant> antMemory = new ArrayList<>();

    // Destination and its assigned pheromone value (how good does the current node act as a relay for that destination?)
    private Map<Integer, Double> pheromoneTable = new HashMap<>();

    // Decay per tick
    private static final double EVAPORATION_RATE = 0.1;

    // Tracks contact start time: encounteredNode ID - contact start time
    private Map<Integer, Long> contactStartTimes = new HashMap<>();

    // Track last ant exchange: enxounteredNode ID - last exchange time
    private Map<Integer, Long> lastAntExchangeTimes = new HashMap<>();

    // Interval between ant exchanges (ex. 5 sec)
    private static final long ANT_EXCHANGE_INTERVAL = 5000;

    public GRACE(int id, int nodes, Context context, boolean[] socialNetwork, int dataMemorySize, int exchangeHistorySize, long seed, long traceStart, long traceEnd, int antMemorySize) {
        super(id, nodes, context, socialNetwork, dataMemorySize, exchangeHistorySize, seed, traceStart, traceEnd);
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
        int totalMessages = 0;

        // First we allow ants to update the available pathways in order to take the best decision based on recent data
        //exchangeAnts

        // Analyze messages of the encountered node's data memory and apply algorithm
        for (Message message : graceEncounteredNode.dataMemory) {
            // Assure that network capacity is not exceeded
            if (totalMessages >= remainingMessages) {
                return;
            }
        }

        // Analyze messages of the encountered node's own memory and apply algorithm
        for (Message message : graceEncounteredNode.ownMessages) {
            // Assure that network capacity is not exceeded
            if (totalMessages >= remainingMessages) {
                return;
            }
        }
    }

    // At message generation, create an assigned ant fo
    @Override
    public Message generateMessage(Message message) {

        // Create ant
        GraceHelper.Ant ant = new GraceHelper.Ant(message.getId(), message.getDestination(), message.getId(), message.getTimestamp(), 100, 1, 1);

        // Add ant in antMemory for future spreading in the network
        antMemory.add(ant);

        // Add message in own memory
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
    }

    @Override
    public void run(Node encounteredNode, long tick, long contactDuration, boolean newContact, long timeDelta, long sampleTIme) {
        super.run(encounteredNode, tick, contactDuration, newContact, timeDelta, sampleTIme);

        // Initialize contact start time for new contacts
        if (newContact) {
            contactStartTimes.put(encounteredNode.getId(), tick);
            lastAntExchangeTimes.put(encounteredNode.getId(), tick);
        }

        // Check if time for next ant exchange (ex. 5 sec)
        long lastExchange = lastAntExchangeTimes.getOrDefault(encounteredNode.getId(), tick);
        if (tick - lastExchange >= ANT_EXCHANGE_INTERVAL) {
            //exchangeAnts(encounteredNode, tick);

            // Update last exchange time
            lastAntExchangeTimes.put(encounteredNode.getId(), tick);
        }
    }

    private void exchangeAnts(GRACE encounteredNode, long currentTime) {
        // Remove all expired ants of the current node
        Iterator<GraceHelper.Ant> iterator = antMemory.iterator();
        while (iterator.hasNext()) {
            GraceHelper.Ant ant = iterator.next();
            if (ant.isExpired(currentTime)) {
                iterator.remove();
                continue;
            }

            // Skip if the encountered node already has this ant
            if (encounteredNode.hasAnt(ant)) continue;

            // Forward ant
        }
    }

    public boolean hasAnt(GraceHelper.Ant otherAnt) {
        for (GraceHelper.Ant ant : antMemory) {
            if (ant.getSource() == otherAnt.getSource() && ant.getTimestamp() == otherAnt.getTimestamp()) {
                return true;
            }
        }

        return false;
    }
}
