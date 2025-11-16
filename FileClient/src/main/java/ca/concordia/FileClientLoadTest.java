package ca.concordia;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class FileClientLoadTest {

    private static class ClientTask implements Runnable {
        private final int id;

        public ClientTask(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try (Socket socket = new Socket("localhost", 12345);
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Each client does a sequence of operations
                writer.println("CREATE file"  + ".txt");
                System.out.println("Client " + ": " + reader.readLine());

                writer.println("WRITE file"  + ".txt hello-from-client-" + id);
                System.out.println("Client " + ": " + reader.readLine());

                writer.println("READ file" + ".txt");
                System.out.println("Client " + ": " + reader.readLine());

                writer.println("QUIT");

            } catch (Exception e) {
                System.out.println("Client " + " error: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {

        int numberOfClients = 3;

        Thread[] threads = new Thread[numberOfClients];

        for (int i = 0; i < numberOfClients; i++) {
            threads[i] = new Thread(new ClientTask(i));
            threads[i].start();
        }

        // Wait for all clients to finish
        for (int i = 0; i < numberOfClients; i++) {
            threads[i].join();
        }

        System.out.println("Load test completed.");
    }
}
