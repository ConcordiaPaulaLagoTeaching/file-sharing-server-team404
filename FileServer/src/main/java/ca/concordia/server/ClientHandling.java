package ca.concordia.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import ca.concordia.filesystem.FileSystemManager;

public class ClientHandling implements Runnable{

    private Socket clientSocket;
    private FileSystemManager fsManager;

    //constructor
    public ClientHandling (Socket cSocket, FileSystemManager fsManager){
        this.clientSocket = cSocket;
        this.fsManager = fsManager;
    }

    public void run(){
        System.out.println("running..." + clientSocket);

        // wait for input from client and send response back to client (show relations in UML diagram)

        try (
            //implement the input and output buffer

            BufferedReader reader = new BufferedReader (new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter (clientSocket.getOutputStream(), true);
        )
         {
            String request;

            //reads request from client 
            while((request = reader.readLine()) != null){

                // Split the line into three (Command, filename, content)
                String[] parts = request.split(" ", 3);
                String command = parts[0].toUpperCase();

                try {
                    switch (command) {
                        case "CREATE":
                            fsManager.createFile(parts[1]);
                            writer.println("SUCCESS: File '" + parts[1] + "' created.");
                            break;

                        case "WRITE":
                            fsManager.writeFile(parts[1], parts[2].getBytes());
                            writer.println("SUCCESS: File '" + parts[1] + "' written to.");
                            break;

                        case "READ":
                            byte[] content = fsManager.readFile(parts[1]);
                            writer.println("CONTENT: " + new String(content) + " (" + content.length + " bytes)");
                        break;
                        
                        case "LIST":
                            // writer.println("Checking for available files...");
                            String [] filesAvailable = fsManager.listFiles();
                            if (filesAvailable == null || filesAvailable.length == 0){
                                    writer.println("No files found.");
                            }else{
                                writer.println("Current files available: " + String.join(", ",filesAvailable));
                            }
                        break;

                        case "DELETE":
                            fsManager.deleteFile(parts[1]);
                            writer.println("SUCCESS: File " + parts[1]+ " deleted");
                            break;

                        case "QUIT":
                            writer.println("SUCCESS: Disconnecting.");
                            return;

                        default:
                            writer.println("ERROR: Unknown command.");
                            break;
                    }
                } catch (Exception e) {
                    writer.println("ERROR: " + e.getMessage());
                } 
            }
            
        } catch (Exception e) {
            System.out.println("Completed..." + e.getMessage());

        }finally{
            try {
                clientSocket.close();
            } catch (Exception ignored) {}
        }

    }
}
