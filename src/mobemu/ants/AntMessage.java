package mobemu.ants;

import mobemu.node.Message;

import java.util.ArrayList;
import java.util.List;

public class AntMessage extends Message {
    private List<Integer> visitedNodes;
    private boolean isBackwardAnt;

    public AntMessage(int source, int destination) {
        super(source, destination, "ant", System.currentTimeMillis(), 0);
        this.visitedNodes = new ArrayList<>();
        this.isBackwardAnt = false;  // starts as a Forward Ant
    }

    public void visitNode(int nodeId) {
        visitedNodes.add(nodeId);
    }

    public List<Integer> getVisitedNodes() {
        return visitedNodes;
    }

    public boolean isBackwardAnt() {
        return isBackwardAnt;
    }

    public void convertToBackwardAnt() {
        this.isBackwardAnt = true;
    }
}
