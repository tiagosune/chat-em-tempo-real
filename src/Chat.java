import javax.swing.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

public class Chat extends JFrame {
    private JTextField loginUsuario;
    private JPasswordField loginSenha;
    private JButton loginBtn, cadastrarBtn;
    private JTextArea chatArea;
    private JTextField mensagemField;
    private JButton enviarBtn;

    private static final String USERS_FILE_NAME = "usuarios.properties";
    private final Properties usuarios = new Properties();
    private Path usuariosPath;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public Chat() {
        inicializarRepositorioUsuarios();

        mostrarTelaLogin();
    }

    private void mostrarTelaLogin() {
        JFrame loginFrame = new JFrame("Login");

        loginFrame.setBounds(100, 100, 400, 300);
        loginFrame.setLayout(null);

        JLabel userLabel = new JLabel("Usuário:");
        userLabel.setBounds(50, 50, 100, 30);
        loginFrame.add(userLabel);

        loginUsuario = new JTextField();
        loginUsuario.setBounds(150, 50, 150, 30);
        loginFrame.add(loginUsuario);

        JLabel senhaLabel = new JLabel("Senha:");
        senhaLabel.setBounds(50, 100, 100, 30);
        loginFrame.add(senhaLabel);

        loginSenha = new JPasswordField();
        loginSenha.setBounds(150, 100, 150, 30);
        loginFrame.add(loginSenha);

        loginBtn = new JButton("Entrar");
        loginBtn.setBounds(50, 150, 120, 30);
        loginFrame.add(loginBtn);

        loginBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String usuario = loginUsuario.getText();
                String senha = new String(loginSenha.getPassword());

                if (validarLogin(usuario, senha)) {
                    loginFrame.dispose();
                    iniciarChat();
                } else {
                    JOptionPane.showMessageDialog(loginFrame, "Usuário ou senha inválidos!", "Erro de login", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        cadastrarBtn = new JButton("Cadastrar");
        cadastrarBtn.setBounds(180, 150, 120, 30);
        loginFrame.add(cadastrarBtn);

        cadastrarBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String usuario = loginUsuario.getText();
                String senha = new String(loginSenha.getPassword());

                if (cadastrarUsuario(usuario, senha)) {
                    JOptionPane.showMessageDialog(loginFrame, "Usuário cadastrado com sucesso!", "Cadastro", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(loginFrame, "Usuário já existe ou erro ao cadastrar.", "Erro de cadastro", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        loginFrame.setLocationRelativeTo(null);
        loginFrame.setVisible(true);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    private boolean validarLogin(String usuario, String senha) {
        String usuarioNormalizado = normalizarUsuario(usuario);
        if (usuarioNormalizado.isEmpty() || senha == null || senha.isEmpty()) {
            return false;
        }

        synchronized (usuarios) {
            String senhaSalvaHash = usuarios.getProperty(usuarioNormalizado);
            return senhaSalvaHash != null && senhaSalvaHash.equals(hashSenha(senha));
        }
    }

    private boolean cadastrarUsuario(String usuario, String senha) {
        String usuarioNormalizado = normalizarUsuario(usuario);
        if (usuarioNormalizado.isEmpty() || senha == null || senha.isEmpty()) {
            return false;
        }

        synchronized (usuarios) {
            if (usuarios.containsKey(usuarioNormalizado)) {
                return false;
            }

            usuarios.setProperty(usuarioNormalizado, hashSenha(senha));
            try {
                salvarUsuarios();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    private void inicializarRepositorioUsuarios() {
        try {
            usuariosPath = Paths.get(System.getProperty("user.dir"), USERS_FILE_NAME);
            if (Files.exists(usuariosPath)) {
                try (InputStream in = Files.newInputStream(usuariosPath)) {
                    usuarios.load(in);
                }
            } else {
                Files.createFile(usuariosPath);
            }
            System.out.println("Repositório de usuários pronto em: " + usuariosPath);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Erro ao inicializar o repositório de usuários.", "Erro", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    private void salvarUsuarios() throws IOException {
        Path arquivoTemporario = Paths.get(usuariosPath.toString() + ".tmp");
        try (OutputStream out = Files.newOutputStream(arquivoTemporario, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            usuarios.store(out, "Usuarios do chat");
        }
        Files.move(arquivoTemporario, usuariosPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private String normalizarUsuario(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase();
    }

    private String hashSenha(String senha) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(senha.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo de hash indisponível", e);
        }
    }

    private void iniciarChat() {
        setTitle("Chat");
        setBounds(100, 120, 600, 500);
        setLayout(null);
        setResizable(false);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBounds(20, 20, 550, 350);
        add(scrollPane);

        mensagemField = new JTextField();
        mensagemField.setBounds(20, 400, 450, 30);
        add(mensagemField);

        enviarBtn = new JButton("Enviar");
        enviarBtn.setBounds(480, 400, 90, 30);
        add(enviarBtn);

        enviarBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String mensagem = mensagemField.getText();
                if (!mensagem.isEmpty()) {
                    enviarMensagem(mensagem);
                    mensagemField.setText("");
                }
            }
        });


        iniciarConexao();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }


    private void iniciarConexao() {
        try {
            socket = new Socket("localhost", 8084);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            new Thread(new Runnable() {
                public void run() {
                    try {
                        String mensagemRecebida;
                        while ((mensagemRecebida = in.readLine()) != null) {
                            chatArea.append(mensagemRecebida + "\n");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Erro ao conectar ao servidor de chat.", "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void enviarMensagem(String mensagem) {
        out.println(loginUsuario.getText() + ": " + mensagem);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Chat());
    }
}
