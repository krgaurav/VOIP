package voip;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer extends Thread{
    public static final int DEFAULT_PORT = 6035;

    public static void main(String[] args) {
        System.out.println("MultiUser Voice Chat server starting...");
        int port = DEFAULT_PORT;
        ServerSocket serverSocket = null;
        Socket socket = null;
        try {
            if(args.length > 0)
                port = Integer.parseInt(args[0]);
        } catch(NumberFormatException nfe) {
            System.err.println("Usage: java ChatServer [port]");
            System.err.println("Where options include:");
            System.err.println("\tport the port on which to listen.");
            System.exit(0);
        }
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Listening on port " + port);
            
            while(true) {
                socket = serverSocket.accept();
                System.out.println("Connection receive from " + socket.getRemoteSocketAddress());
                ChatHandler handler = new ChatHandler(socket);
                handler.start();
            }
        } catch(IOException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                serverSocket.close();
            } catch(IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
}