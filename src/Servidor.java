import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Servidor {
    private static final int PORT = 8084;
    private static final int MAX_CLIENTS = 10;
    private static Set<ClientHandler> clientHandlers = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(MAX_CLIENTS);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor de chat iniciado na porta " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                if (clientHandlers.size() >= MAX_CLIENTS) {
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    out.println("Servidor cheio. Tente novamente mais tarde.");
                    clientSocket.close();
                    continue;
                }

                ClientHandler handler = new ClientHandler(clientSocket);
                clientHandlers.add(handler);
                pool.execute(handler);
                System.out.println("Novo cliente conectado. Total de clientes: " + clientHandlers.size());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }

    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clientHandlers) {
            client.sendMessage(message);
        }
        System.out.println("Mensagem broadcast: " + message);
    }

    public static void removeClient(ClientHandler client) {
        clientHandlers.remove(client);
        System.out.println("Cliente desconectado. Total de clientes: " + clientHandlers.size());
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            OutputStream os = socket.getOutputStream();
            out = new PrintWriter(os, true);
            InputStream is = socket.getInputStream();
            in = new BufferedReader(new InputStreamReader(is));
            out.println("Bem-vindo ao Chat!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    @Override
    public void run() {
        String mensagem;
        try {
            while ((mensagem = in.readLine()) != null) {
                Servidor.broadcast(mensagem, this);
            }
        } catch (IOException e) {
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Servidor.removeClient(this);
        }
    }
}

