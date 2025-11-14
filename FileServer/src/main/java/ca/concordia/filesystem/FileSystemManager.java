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
            } catch (Exception e) {
                throw new RuntimeException("Unable to open disk file");
            }
            instance = this;
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    public void writeMetadata() throws Exception {
        disk.seek(0);
        for(int i = 0; i < inodeTable.length; i++){
            if(inodeTable[i] != null){
                disk.writeBytes(inodeTable[i].getFilename());
                disk.write(inodeTable[i].getFilesize());
                disk.write(inodeTable[i].getFirstBlock());
            }
        }

        for(int i = 0; i < fnodes.length; i++){
            if(fnodes[i] != null){
                disk.write(fnodes[i].getBlockIndex());
            }
        }
    }
    

    //CREATE FILE
    public void createFile(String fileName) throws Exception {
        try{

            //Check if file name exists
            for (int i=0; i<inodeTable.length; i++){
                FEntry entry = inodeTable[i];
                if (entry != null && entry.getFilename().equals(fileName)){
                    throw new Exception("FileName already exists. Try again.");
                    // return; //Stops here, to prevent duplicates of the existing file
                }
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
                // return;
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

        }catch(IllegalArgumentException e){
            throw new Exception (e.getMessage());
        }

    }

    // TODO: Add readFile, writeFile and other required methods,

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

            System.out.println("File " + fileName + " written successfully (" + bytesToWrite + " bytes).");

        } catch (Exception e) {
            throw new Exception("Error writing file: " + e.getMessage());
        } finally {
            globalLock.unlock();
        }
    }

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