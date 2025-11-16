package ca.concordia.server;
import java.net.ServerSocket;
import java.net.Socket;

import ca.concordia.filesystem.FileSystemManager;

// Server class
public class FileServer {
    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize){
        // Initialize the FileSystemManager
        // FileSystemManager fsManager = new FileSystemManager(fileSystemName,
        //         10*128 );
        this.fsManager = new FileSystemManager(fileSystemName, 10*128 );
        // this.fsManager = fsManager;
        this.port = port;
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Server started. Listening on port 12345...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling client: " + clientSocket);

                ClientHandling cHandling = new ClientHandling(clientSocket, fsManager);
                Thread thread = new Thread (cHandling);
                thread.start();

                // try (
                //         BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                //         PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
                // ) {
                //     String line;
                //     while ((line = reader.readLine()) != null) {
                //         System.out.println("Received from client: " + line);

                //         // Split the line into three (Command, filename, content)
                //         String[] parts = line.split(" ", 3);
                //         String command = parts[0].toUpperCase();

                //             try {
                //                 switch (command) {
                //                     case "CREATE":
                //                         fsManager.createFile(parts[1]);
                //                         writer.println("SUCCESS: File '" + parts[1] + "' created.");
                //                         break;

                //                     case "WRITE":
                //                         fsManager.writeFile(parts[1], parts[2].getBytes());
                //                         writer.println("SUCCESS: File '" + parts[1] + "' written to.");
                //                         break;

                //                     case "READ":
                //                         byte[] content = fsManager.readFile(parts[1]);
                //                         writer.println("CONTENT: " + new String(content) + " (" + content.length + " bytes)");
                //                     break;
                                    
                //                     case "LIST":
                //                         // writer.println("Checking for available files...");
                //                         String [] filesAvailable = fsManager.listFiles();
                //                         if (filesAvailable == null || filesAvailable.length == 0){
                //                              writer.println("No files found.");
                //                         }else{
                //                             writer.println("Current files available: " + String.join(", ",filesAvailable));
                //                         }
                //                     break;

                //                     case "DELETE":
                //                         fsManager.deleteFile(parts[1]);
                //                         writer.println("SUCCESS: File " + parts[1]+ " deleted");
                //                         break;

                //                     case "QUIT":
                //                         writer.println("SUCCESS: Disconnecting.");
                //                         return;

                //                     default:
                //                         writer.println("ERROR: Unknown command.");
                //                         break;
                //                 }
                //             } catch (Exception e) {
                //                 // Catch any error thrown by FileSystemManager and send it back
                //                 e.printStackTrace();
                //                 writer.println(e.getMessage());
                //             }

                //             writer.flush();
                //     }
                // } catch (Exception e) {
                //     e.printStackTrace();
                // } finally {
                //     try {
                //         clientSocket.close();
                //     } catch (Exception e) {
                //         // Ignore
                //     }
                // }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

}


// class ClientHandling implements Runnable{

//     private Socket clientSocket;
//     private FileSystemManager fsManager;

//     //constructor
//     public ClientHandling (Socket cSocket, FileSystemManager fsManager){
//         this.clientSocket = cSocket;
//         this.fsManager = fsManager;
//     }

//     @Override
//     public void run(){
//         System.out.println("running..." + clientSocket);

//         //implement the input and output buffer
//         // wait for input from client and send response back to client (show relations in UML diagram)
//         //close all streams and sockets

//         //try catch for reader and writer
//         try (
//             BufferedReader reader = new BufferedReader (new InputStreamReader(clientSocket.getInputStream()));
//             PrintWriter writer = new PrintWriter (clientSocket.getOutputStream(), true);
//         )
//          {
//             String request;

//             //reads request from client 
//             while((request = reader.readLine()) != null){

//                 // Split the line into three (Command, filename, content)
//                 String[] parts = request.split(" ", 3);
//                 String command = parts[0].toUpperCase();


//                 try {
//                     switch (command) {
//                         case "CREATE":
//                             fsManager.createFile(parts[1]);
//                             writer.println("SUCCESS: File '" + parts[1] + "' created.");
//                             break;

//                         case "WRITE":
//                             fsManager.writeFile(parts[1], parts[2].getBytes());
//                             writer.println("SUCCESS: File '" + parts[1] + "' written to.");
//                             break;

//                         case "READ":
//                             byte[] content = fsManager.readFile(parts[1]);
//                             writer.println("CONTENT: " + new String(content) + " (" + content.length + " bytes)");
//                         break;
                        
//                         case "LIST":
//                             // writer.println("Checking for available files...");
//                             String [] filesAvailable = fsManager.listFiles();
//                             if (filesAvailable == null || filesAvailable.length == 0){
//                                     writer.println("No files found.");
//                             }else{
//                                 writer.println("Current files available: " + String.join(", ",filesAvailable));
//                             }
//                         break;

//                         case "DELETE":
//                             fsManager.deleteFile(parts[1]);
//                             writer.println("SUCCESS: File " + parts[1]+ " deleted");
//                             break;

//                         case "QUIT":
//                             writer.println("SUCCESS: Disconnecting.");
//                             return;

//                         default:
//                             writer.println("ERROR: Unknown command.");
//                             break;
//                     }
//                 } catch (Exception e) {
//                     writer.println("ERROR: " + e.getMessage());
//                 } 
//             }
            
//         } catch (Exception e) {
//             System.out.println("Completed..." + e.getMessage());

//         }



//     }

//     // public static void main(String[] args) {
//     //     FileHandler filehandler = new FileHandler();
//     //     Thread thread = new Thread (filehandler);
//     //     thread.start();
//     // }
// }
