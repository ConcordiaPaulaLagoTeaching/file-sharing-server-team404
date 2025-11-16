package ca.concordia.server;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ca.concordia.filesystem.FileSystemManager;

// Server class
public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    private ExecutorService threadPool;

    public FileServer(int port, String fileSystemName, int totalSize){
        // Initialize the FileSystemManager
        // FileSystemManager fsManager = new FileSystemManager(fileSystemName,
        //         10*128 );
        this.fsManager = new FileSystemManager(fileSystemName, 10*128 );
        // this.fsManager = fsManager;
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(100);
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Server started. Listening on port 12345...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(new ClientHandling(clientSocket, fsManager));
                System.out.println("Handling client: " + clientSocket);

                ClientHandling cHandling = new ClientHandling(clientSocket, fsManager);
                Thread thread = new Thread (cHandling);
                thread.start();

            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

}