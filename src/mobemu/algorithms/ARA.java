package mobemu.algorithms;

import mobemu.node.Context;
import mobemu.node.Message;
import mobemu.node.Node;
import mobemu.utils.Ant;

public class ARA extends Node {

    public ARA(int id, int nodes, Context context, boolean[] socialNetwork, int dataMemorySize, int exchangeHistorySize, long seed, long traceStart, long traceEnd, int antMemorySize) {
        super(id, nodes, context, socialNetwork, dataMemorySize, exchangeHistorySize, seed, traceStart, traceEnd);
    }

    @Override
    public String getName() { return "ARA"; }

    @Override
    protected void onDataExchange(Node encounteredNode, long contactDuration, long currentTime) {
        // Check and cast to the ARA instance
        if (!(encounteredNode instanceof ARA araEncounteredNode)) {
            return;
        }

        // Perform the delivery of messages that are destined for the current node
        int remainingMessages = deliverDirectMessages(araEncounteredNode, false, contactDuration, currentTime, false);
        int totalMessages = 0;

        // Analyze messages of the encountered node's data memory and apply algorithm
        for (Message message : araEncounteredNode.dataMemory) {
            // Assure that network capacity is not exceeded
            if (totalMessages >= remainingMessages) {
                return;
            }
        }

        // Analyze messages of the encountered node's own memory and apply algorithm
        for (Message message : araEncounteredNode.ownMessages) {
            // Assure that network capacity is not exceeded
            if (totalMessages >= remainingMessages) {
                return;
            }
        }
    }

    /**
     * When a message is generated, forward ants will be launched in order to search for the
     * destination of the message.
     */
    @Override
    public Message generateMessage(Message message) {
        ownMessages.add(message);

        // Create Forward Ant for route discovery
        // TODO: sort out what i need to do with the antID
        Ant.AntMessage fant = new Ant.AntMessage(message.getId(), message.getId(), this.id, message.getDestination());

        //Because run() is called at every tick antMessages will be forwarded similar to the normal messages
        //during onDataExhange function by insertAnt() call to download a specific ant from an existing contact
        //or a newly encountered node

        //antMemory.add(fant);

        return message;
    }
}
