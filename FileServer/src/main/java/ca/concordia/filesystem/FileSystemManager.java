package ca.concordia.filesystem;

import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance = null;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

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
                short fb = entry.getFirstBlock();
                if (fb >= 0 && fb < freeBlockList.length) {
                    freeBlockList[fb] = true;
                }
            }
        }
    }


    //CREATE FILE
    public void createFile(String fileName) throws Exception {
        globalLock.lock();
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
            globalLock.unlock();
        }
    }

    //WRITE FILE
    public void writeFile(String fileName, byte[] contents) throws Exception {
        globalLock.lock();
        try {
            // Check if the file exists and finds first entry
            FEntry target = checkFile(fileName);
    
            // Calculate how many blocks we need
            int bytesToWrite = contents.length;
            int blocksNeeded = (int) Math.ceil((double) bytesToWrite / BLOCK_SIZE);

            // Make sure we have enough free blocks
            int freeCount = 0;
            for (boolean used : freeBlockList){
                if (!used) freeCount++;     
            }
                
            if (blocksNeeded > freeCount){
                throw new Exception("ERROR: file too large.");
            }

            // Get the first free block
            short firstBlock = target.getFirstBlock();
            long offset = firstBlock * BLOCK_SIZE;
            disk.seek(offset);

            // Write the bytes to the disk file
            disk.write(contents);

            // Update metadata
            target.setFilesize((short) bytesToWrite);
            writeMetadata();
            System.out.println("File " + fileName + " written successfully (" + bytesToWrite + " bytes).");

        } catch (Exception e) {
            throw new Exception("Error writing file: " + e.getMessage());
        } finally {
            globalLock.unlock();
        }
    }

    //READ FILE
    public byte[] readFile(String fileName) throws Exception {
        globalLock.lock();
        try {
            // Check if the file exists and finds first entry
            FEntry target = checkFile(fileName);

            // Get size and offset
            int size = Short.toUnsignedInt(target.getFilesize());
            if (size == 0) return new byte[0];

            long offset = (long) target.getFirstBlock() * BLOCK_SIZE;
            if (offset < 0) throw new Exception("Invalid block offset.");

            // Reading bytes
            byte[] buf = new byte[size];
            disk.seek(offset);
            disk.readFully(buf, 0, size);

            return buf;

        } catch (Exception e) {
            throw new Exception(e.getMessage());
        } finally {
            globalLock.unlock();
        }
    }

    //LIST ALL FILES
    public String[] listFiles() {
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
    }

    //DELETE FILES
    public void deleteFile(String fileName) throws Exception{
        globalLock.lock();
        try {
            //Check if file name exists
            FEntry target = checkFile(fileName);

            short block = target.getFirstBlock();
            if (block >= 0 && block < freeBlockList.length){

                // Clearing block
                long offset = (long) block * BLOCK_SIZE;
                disk.seek(offset);

                // Write zeros over the whole block
                byte[] zeros = new byte[BLOCK_SIZE];
                disk.write(zeros);

                freeBlockList[block] = false;
            }

            // Clearing FNode
            for (int i = 0; i < fnodes.length; i++) {
                FNode node = fnodes[i];
                if (node != null && node.getBlockIndex() == block) {
                    fnodes[i] = null;
                }
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
            globalLock.unlock();
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
}