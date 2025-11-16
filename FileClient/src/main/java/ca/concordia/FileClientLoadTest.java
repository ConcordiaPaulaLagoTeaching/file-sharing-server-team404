package ca.concordia;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class FileClientLoadTest {

    private static class ClientTask implements Runnable {
        private final int id;
        private final boolean manyFiles;

        public ClientTask(int id, boolean manyFiles) {
            this.id = id;
            this.manyFiles = manyFiles;
        }

        @Override
        public void run() {
            try (Socket socket = new Socket("localhost", 12345);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                if(manyFiles == false) {
                    /* 
                        One file
                    */
                    writer.println("CREATE file.txt");
                    // Each client does a sequence of operations
                    System.out.println("Client " + id + ": " + reader.readLine());

                    writer.println("LIST");
                    System.out.println("Client " + id + ": " + reader.readLine());

                    writer.println("WRITE file.txt hello-from-client-" + id);
                    System.out.println("Client " + id + ": " + reader.readLine());

                    writer.println("READ file.txt");
                    System.out.println("Client " + id + ": " + reader.readLine());
                }
                else if (manyFiles == true) {
                    /* 
                        Multiple files 
                    */
                    writer.println("CREATE file" + id + ".txt");
                    System.out.println("Client " + id + ": " + reader.readLine());

                    writer.println("LIST");
                    System.out.println("Client " + id + ": " + reader.readLine());

                    writer.println("WRITE file" + id +".txt hello-from-client-" + id);
                    System.out.println("Client " + id + ": " + reader.readLine());

                    writer.println("READ file" + id + ".txt");
                    System.out.println("Client " + id + ": " + reader.readLine());
                }

                writer.println("QUIT");

            } catch (Exception e) {
                System.out.println("Client " + id + " error: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);

        // Ask how many clients
        System.out.println("\n################################################################");
        System.out.print("How many clients? ");
        int numberOfClients = scanner.nextInt();
        scanner.nextLine(); // consume leftover newline

        // Ask whether to use many files or a single file
        System.out.print("Do you want the clients to edit the same file? (y/n): ");
        String answer = scanner.nextLine().trim().toLowerCase();
        boolean manyFiles = answer.startsWith("n");
        System.out.println("################################################################");

        Thread[] threads = new Thread[numberOfClients];

        for (int i = 0; i < numberOfClients; i++) {
            threads[i] = new Thread(new ClientTask(i, manyFiles));
            threads[i].start();
        }

        // Wait for all clients to finish
        for (int i = 0; i < numberOfClients; i++) {
            threads[i].join();
        }

        System.out.println("Load test completed.");
    }
}
