package ca.concordia.filesystem;

import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

import ca.concordia.filesystem.datastructures.FEntry;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance = null;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks

    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file

        if(instance == null) {
            // We Create a Disk file which will be managed by Filesystem
            try {
                this.disk = new RandomAccessFile(filename, "rw");
                inodeTable = new FEntry[MAXFILES];
                freeBlockList = new boolean[MAXBLOCKS];

            } catch (Exception e) {
                throw new RuntimeException("Unable to open disk file");
            }
            instance = this;
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    //CREATE FILE
    public void createFile(String fileName) throws Exception {
        globalLock.lock();
        try{

            // Check if the file exists
            try {
                FEntry exists = checkFile(fileName);
                throw new Exception("Filename already exists. Try again.");
            } catch (Exception e) {
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
                freeBlockList[block] = false;
            }

            for(int i=0; i< inodeTable.length; i++){
                if(inodeTable[i] == target){
                    inodeTable[i] = null;
                    break;
                }
            }

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

        //SAVE FILE DATA (saves file data so it is available after restarting server)
    // private void saveFileData() throws Exception{
    //     globalLock.lock();
    //     try {
    //         try(DataOutputStream out = new DataOutputStream (new FileOutputStream("inodeTable.temp"))){
    //             for (int i=0; i < inodeTable.length; i++){
    //                 FEntry slot = inodeTable[i];
    //                 if ( slot == null){
    //                     out.writeBoolean(false);
    //                 }else{
    //                     out.writeBoolean(true);
    //                     out.writeUTF(slot.getFilename());
    //                     out.writeShort(slot.getFilesize());
    //                     out.writeShort(slot.getFirstBlock());
    //                 }
    //             }
    //         }

    //         try (DataOutputStream out = new DataOutputStream(new FileOutputStream("freeBlocks.temp"))){
    //             for(int i=0; i< freeBlockList.length; i++) {
    //                 out.writeBoolean(freeBlockList[i]);
    //             }
    //         }

    //     } finally {
    //         globalLock.unlock();
    //     }
    // }
    
    // // LOAD FILE DATA (loads file data after restarting server)
    // private void loadFileData() throws Exception{
    //     globalLock.lock();
    //     try {
    //         try(DataInputStream in = new DataInputStream(new FileInputStream("inodeTable.temp"))){
    //             for(int i=0; i<inodeTable.length; i++){
    //                 boolean exists = in.readBoolean();
    //                 if(!exists){
    //                     inodeTable[i] = null;
    //                 }else{
    //                     String name = in.readUTF();
    //                     short size = in.readShort();
    //                     short block = in.readShort();
    //                     inodeTable[i] = new FEntry(name, size, block);
    //                 }
    //             }
    //         }catch (FileNotFoundException e){
    //         }

    //         try (DataInputStream in = new DataInputStream(new FileInputStream("freeBlocks.temp"))){
    //                 for(int i=0; i < freeBlockList.length; i++){
    //                     freeBlockList[i] = in.readBoolean();
    //                 }
    //         }catch(FileNotFoundException e){
    //         }

    //     } finally {
    //         globalLock.unlock();
    //     }
        
    // }


}