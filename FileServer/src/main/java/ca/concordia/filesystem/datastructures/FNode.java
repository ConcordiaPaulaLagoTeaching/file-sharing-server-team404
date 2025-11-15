package ca.concordia.filesystem.datastructures;

public class FNode {

    private int blockIndex;
    private int next;

    public FNode(int blockIndex) {
        this.blockIndex = blockIndex;
        this.next = -1;
    }

    public FNode(int blockIndex, int next) {
        this.blockIndex = blockIndex;
        this.next = next;
    }

    public int getBlockIndex(){
        return blockIndex;
    }

    public int getNextBlock(){
        return next;
    }

    public void setNextBlock(int next) {
        this.next = next;
    }
}
