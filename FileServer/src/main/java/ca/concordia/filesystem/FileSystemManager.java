package ca.concordia.filesystem;

import java.io.RandomAccessFile;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance = null;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    // Readers–writers sync (replaces globallock)
    private final Semaphore mutex = new Semaphore(1);
    private final Semaphore wrt = new Semaphore(1);
    private int readCount = 0;

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private FNode[] fnodes; // Array of fnodes
    private boolean[] freeBlockList; // Bitmap for free blocks

    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file

        if(instance == null) {
            // We Create a Disk file which will be managed by Filesystem
            try {
                this.disk = new RandomAccessFile(filename, "rw");
                inodeTable = new FEntry[MAXFILES];
                fnodes = new FNode[MAXBLOCKS];
                freeBlockList = new boolean[MAXBLOCKS];
                freeBlockList[0] = true; // metadata lives here (reserved area)

                if (disk.length() >= BLOCK_SIZE) {
                    // Existing filesystem: load previous FEntry/FNode
                    readMetada();
                } else {
                    // New filesystem: write empty metadata once
                    writeMetadata();
                }
            } catch (Exception e) {
                throw new RuntimeException("Unable to open disk file");
            }
            instance = this;
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    public void writeMetadata() throws Exception {
        try {
            disk.seek(0);

            // Write all FEntry objects
            for (int i = 0; i < inodeTable.length; i++) {
                FEntry entry = inodeTable[i];

                // Default values for an empty slot
                String name;
                short size;
                short firstBlock;

                if (entry == null) {
                    name = "";
                    size = 0;
                    firstBlock = -1;
                } else {
                    name = entry.getFilename();
                    size = entry.getFilesize();
                    firstBlock = entry.getFirstBlock();
                }

                if (name == null) {
                    name = "";
                }

                // 11-byte filename
                byte[] nameBytes = name.getBytes();
                for (int j = 0; j < 11; j++) {
                    if (j < nameBytes.length) {
                        disk.write(nameBytes[j]);
                    } else {
                        disk.write(0);
                    }
                }

                // 2-byte filesize and 2-byte firstBlock
                disk.writeShort(size);
                disk.writeShort(firstBlock);
            }

            // Write all FNode objects
            for (int i = 0; i < fnodes.length; i++) {
                FNode node = fnodes[i];

                short blockIndex;
                short next;

                if (node == null) {
                    blockIndex = -1;
                    next = -1;
                } else {
                    // make sure FNode has these getters, or add them
                    blockIndex = (short) node.getBlockIndex();
                    next = (short) node.getNextBlock();
                }

                // 2 bytes blockIndex + 2 bytes next
                disk.writeShort(blockIndex);
                disk.writeShort(next);
            }

            // Pad the rest of block 0
            long pos = disk.getFilePointer();
            while (pos < BLOCK_SIZE) {
                disk.write(0);
                pos++;
            }
        } catch (Exception e) {
            throw e;
        }
    }

    public void readMetada() throws Exception {
        disk.seek(0);

        // Read FEntries
        for (int i = 0; i < inodeTable.length; i++) {
            // 11 bytes for filename
            byte[] nameBuffer = new byte[11];
            disk.readFully(nameBuffer);

            // Strip zeros
            int realLen = 0;
            while (realLen < 11 && nameBuffer[realLen] != 0) {
                realLen++;
            }
            String name = new String(nameBuffer, 0, realLen);

            // 2 bytes size, 2 bytes firstBlock
            short size = disk.readShort();
            short firstBlock = disk.readShort();

            // Decide if this slot is used or empty
            if (name.isEmpty() && size == 0 && firstBlock < 0) {
                inodeTable[i] = null;
            } else {
                inodeTable[i] = new FEntry(name, size, firstBlock);
            }
        }

        // Read FNodes
        for (int i = 0; i < fnodes.length; i++) {
            short blockIndex = disk.readShort();
            short next       = disk.readShort();

            if (blockIndex < 0) {
                fnodes[i] = null;       // unused node
            } else {
                FNode node = new FNode(blockIndex);
                node.setNextBlock(next);
                fnodes[i] = node;
            }
        }

        // Rebuild freeBlockList
        for (int i = 0; i < freeBlockList.length; i++) {
            freeBlockList[i] = false;
        }

        freeBlockList[0] = true;

        // Mark blocks used by files
        for (FEntry entry : inodeTable) {
            if (entry != null) {
                int current = entry.getFirstBlock();
                while (current >= 0 && current < freeBlockList.length && !freeBlockList[current]) {
                    freeBlockList[current] = true;

                    FNode node = fnodes[current];
                    if (node == null) break;
                    int next = node.getNextBlock();
                    if (next == current) break;
                    current = next;
                }
            }
        }
    }

    //CREATE FILE
    public void createFile(String fileName) throws Exception {
        startWrite();
        try{

            // Check if the file exists
            FEntry exists = null;
            try {
                exists = checkFile(fileName);
            } catch (Exception e) {
            }

            if (exists != null) {
                throw new Exception("Filename already exists. Try again.");
            }

            //Check the first available fentry
            int availableSpace = -1;
            for (int i=0; i<inodeTable.length; i++){
                if (inodeTable[i] == null){
                    availableSpace =i;
                    break;
                }
            }

            if (availableSpace ==-1){
                throw new Exception("No free file entries available.");
            }

            // Check if free/occupied nodes
            short freeBlock=0;
            while (freeBlock < freeBlockList.length && freeBlockList[freeBlock]){
                freeBlock++;
            }
            if(freeBlock == freeBlockList.length){
                throw new Exception("No free blocks available.");

            }
            freeBlockList[freeBlock]= true;

            //Create the file
            FEntry newFile = new FEntry (fileName, (short)0, freeBlock);
            inodeTable[availableSpace] = newFile; //Store the new file
            writeMetadata();
        } finally {
            endWrite();
        }
    }

    //WRITE FILE
    public void writeFile(String fileName, byte[] contents) throws Exception {
        startWrite();
        globalLock.lock();

        try {
            // Check if the file exists and finds first entry
            FEntry target = checkFile(fileName);
    
            // Calculate how many blocks we need
            int bytesToWrite = contents.length;
            int blocksNeeded = (int) Math.ceil((double) bytesToWrite / BLOCK_SIZE);

            // Find freeblocks to write to
            java.util.List<Integer> chosenBlocks = new java.util.ArrayList<>(); 

            // Make sure we have enough free blocks
            int freeCount = 0;
            for (boolean used : freeBlockList){
                if (!used) freeCount++;     
            }
                
            if (blocksNeeded > freeCount){
                throw new Exception("ERROR: file too large.");
            }

            // Free old blocks used by this file
            short oldFirstBlock = target.getFirstBlock();
            int oldCurrent = oldFirstBlock;

            while (oldCurrent >= 0 && oldCurrent < freeBlockList.length) {
                FNode oldNode = fnodes[oldCurrent];
                int oldNext = -1;

                if (oldNode != null) {
                    oldNext = oldNode.getNextBlock();
                }

                // Clear old block data
                long oldOffset = (long) oldCurrent * BLOCK_SIZE;
                disk.seek(oldOffset);
                byte[] zeros = new byte[BLOCK_SIZE];
                disk.write(zeros);

                // Mark free
                freeBlockList[oldCurrent] = false;

                // Remove node
                fnodes[oldCurrent] = null;

                // Move through chain
                oldCurrent = oldNext;
            }


            for (int i = 1; i < freeBlockList.length && chosenBlocks.size() < blocksNeeded; i++) {
                if (!freeBlockList[i]) {
                    chosenBlocks.add(i);
                }
            }  

            // Mark blocks as used and set up FNode
            for (int i = 0; i < chosenBlocks.size(); i++) {
                int blockIndex = chosenBlocks.get(i);

                // mark as used
                freeBlockList[blockIndex] = true;

                // create or reuse FNode
                FNode node = fnodes[blockIndex];
                if (node == null) {
                    node = new FNode(blockIndex);
                    fnodes[blockIndex] = node;
                }

                // link to next block (or -1 if last)
                if (i == chosenBlocks.size() - 1) {
                    node.setNextBlock(-1);
                } else {
                    int nextBlockIndex = chosenBlocks.get(i + 1);
                    node.setNextBlock(nextBlockIndex);
                }
            }

            // Update FEntry metadata
            short headBlock = chosenBlocks.get(0).shortValue();
            target.setFirstBlock(headBlock);
            target.setFilesize((short) bytesToWrite);

            // Write data in all blocks
            int remaining = bytesToWrite;
            int contentOffset = 0;
            int currentBlock = headBlock;

            while (currentBlock != -1 && remaining > 0) {
                long diskOffset = (long) currentBlock * BLOCK_SIZE;
                disk.seek(diskOffset);

                int bytesThisBlock = Math.min(remaining, BLOCK_SIZE);
                disk.write(contents, contentOffset, bytesThisBlock);

                contentOffset += bytesThisBlock;
                remaining -= bytesThisBlock;

                currentBlock = fnodes[currentBlock].getNextBlock();
            }

            // Update metadata
            target.setFilesize((short) bytesToWrite);
            writeMetadata();
            System.out.println("File " + fileName + " written successfully (" + bytesToWrite + " bytes).");

        } catch (Exception e) {
            throw new Exception("Error writing file: " + e.getMessage());
        } finally {
            globalLock.unlock();
            endWrite();
        }
    }

    //READ FILE
    public byte[] readFile(String fileName) throws Exception {
        startRead();
        try {
            // Check if the file exists and finds first entry
            FEntry target = checkFile(fileName);

            // Get size and offset
            int size = Short.toUnsignedInt(target.getFilesize());
            if (size == 0) return new byte[0];

            // Reading bytes
            byte[] buf = new byte[size];
            int remaining = size;
            int bufOffset = 0;
            int currentBlock = target.getFirstBlock();

            while (currentBlock != -1 && remaining > 0) {
                long diskOffset = (long) currentBlock * BLOCK_SIZE;
                disk.seek(diskOffset);

                int bytesThisBlock = Math.min(remaining, BLOCK_SIZE);
                disk.readFully(buf, bufOffset, bytesThisBlock);

                bufOffset += bytesThisBlock;
                remaining -= bytesThisBlock;

                FNode node = fnodes[currentBlock];
                currentBlock = (node != null) ? node.getNextBlock() : -1;
            }

            return buf;

        } catch (Exception e) {
            throw new Exception(e.getMessage());
        } finally {
            endRead();
        }
    }

    //LIST ALL FILES
    public String[] listFiles() throws Exception {
        startRead();
        try {
            int fileCount = 0;
            for (int i=0; i< inodeTable.length; i++){
                FEntry entry = inodeTable[i];
                if(entry != null){
                    fileCount ++;
                }
            }
            String[] filesAvailable = new String[fileCount];
            int index=0;

            for (int i=0; i< inodeTable.length; i++){
                FEntry entry = inodeTable[i];
                if(entry != null){
                    filesAvailable[index] = entry.getFilename();
                    index++;
                }
            }
            return filesAvailable;
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        } finally{
            endRead();
        }
    }

    //DELETE FILES
    public void deleteFile(String fileName) throws Exception{
        startWrite();
        try {
            //Check if file name exists
            FEntry target = checkFile(fileName);

            short firstBlock = target.getFirstBlock();
            int current = firstBlock;

            // Clear every block
            while (current >= 0 && current < freeBlockList.length) {
                // Save next before we kill this node
                FNode node = fnodes[current];
                int next = -1;
                if (node != null) {
                    next = node.getNextBlock();
                }

                // Clear block contents on disk
                long offset = (long) current * BLOCK_SIZE;
                disk.seek(offset);
                byte[] zeros = new byte[BLOCK_SIZE];
                disk.write(zeros);

                // Mark block free
                freeBlockList[current] = false;

                // Remove FNode
                fnodes[current] = null;

                // Move to next
                current = next;
            }
            
            for(int i=0; i< inodeTable.length; i++){
                if(inodeTable[i] == target){
                    inodeTable[i] = null;
                    break;
                }
            }   
            writeMetadata();

        } catch (Exception e) {
            throw new Exception ("ERROR: " + e.getMessage());
        } finally {
            endWrite();
        }
    }

    //CHECK FILE
    public FEntry checkFile(String fileName) throws Exception {
        boolean fileExist = false;
        FEntry target = null;

        //Check if file exists and takes first entry
        for (int i=0; i<inodeTable.length; i++){
            FEntry entry = inodeTable[i];
            if (entry != null && entry.getFilename().equals(fileName)){
                fileExist = true;
                target = entry;
            }
        }

        if(fileExist == false) {
            throw new Exception("ERROR: file " + fileName + " does not exist.");
        }

        return target;
    }

    /* 
        Reader-Writer Helpers 
    */

    // Reader
    private void startRead() throws InterruptedException {
        mutex.acquire();
        readCount++;
        if (readCount == 1) {
            wrt.acquire();
        }
        mutex.release();
    }

    private void endRead() {
        try {
            mutex.acquire();
            readCount--;
            if (readCount == 0) {
                wrt.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mutex.release();
        }
    }

    // Writer
    private void startWrite() throws InterruptedException {
        wrt.acquire();
    }

    private void endWrite() {
        wrt.release();
    }
}