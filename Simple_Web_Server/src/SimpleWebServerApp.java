import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SimpleWebServerApp extends JFrame {
    private JTextField portField;
    private JTextField webDirField;
    private JTextField logDirField;
    private JTextArea logTextArea;
    private JButton startButton;
    private JButton stopButton;
    private HttpServer server;
    private Thread serverThread;
    private SimpleDateFormat dateFormatter;

    public SimpleWebServerApp() {
        setTitle("Simple Web Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);

        dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);

        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Port:"), c);
        c.gridx = 1;
        portField = new JTextField("1234", 10);
        panel.add(portField, c);

        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Web Directory:"), c);
        c.gridx = 1;
        webDirField = new JTextField(System.getProperty("user.dir"), 20);
        panel.add(webDirField, c);
        c.gridx = 2;
        JButton webDirButton = new JButton("Browse");
        webDirButton.addActionListener(e -> browseDirectory(webDirField));
        panel.add(webDirButton, c);

        c.gridx = 0;
        c.gridy = 2;
        panel.add(new JLabel("Log Directory:"), c);
        c.gridx = 1;
        logDirField = new JTextField(System.getProperty("user.dir") + "/logs", 20);
        panel.add(logDirField, c);
        c.gridx = 2;
        JButton logDirButton = new JButton("Browse");
        logDirButton.addActionListener(e -> browseDirectory(logDirField));
        panel.add(logDirButton, c);

        c.gridx = 0;
        c.gridy = 3;
        startButton = new JButton("Start Server");
        startButton.addActionListener(e -> startServer());
        panel.add(startButton, c);

        c.gridx = 1;
        stopButton = new JButton("Stop Server");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopServer());
        panel.add(stopButton, c);

        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 3;
        logTextArea = new JTextArea(10, 40);
        logTextArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logTextArea);
        panel.add(scrollPane, c);

        DefaultCaret caret = (DefaultCaret) logTextArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        add(panel);
    }

    private void browseDirectory(JTextField field) {
        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnValue = chooser.showOpenDialog(null);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getPath());
        }
    }

    private void logMessage(String message) {
        String timestamp = dateFormatter.format(new Date());
        String logLine = timestamp + " - " + message + "\n";
        logTextArea.append(logLine);
        logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        try (FileWriter fw = new FileWriter(logDirField.getText() + "/" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log", true)) {
            fw.write(logLine);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startServer() {
        int port = Integer.parseInt(portField.getText());
        String webDir = webDirField.getText();
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new MyHttpHandler(webDir));
            server.setExecutor(null);

            serverThread = new Thread(() -> server.start());
            serverThread.start();

            logMessage("Server started on port " + port);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        } catch (IOException e) {
            logMessage("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
            serverThread.interrupt();
            logMessage("Server stopped");
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        }
    }

    private class MyHttpHandler implements HttpHandler {
        private String webDir;

        public MyHttpHandler(String webDir) {
            this.webDir = webDir;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String requestUri = exchange.getRequestURI().getPath();
            String filePath = webDir + requestUri;
            if (requestUri.endsWith("/")) {
                filePath += "index.html";
            }

            InetAddress remoteAddress = exchange.getRemoteAddress().getAddress();
            String clientIP = remoteAddress.getHostAddress();
            if (remoteAddress instanceof Inet6Address && clientIP.startsWith("0:0:0:0:0:0:0:1")) {
                clientIP = "127.0.0.1"; // Map IPv6 loopback to IPv4 loopback
            }

            logMessage("Request from " + clientIP + ": " + method + " " + requestUri);

            File file = new File(filePath);
            if (file.exists() && !file.isDirectory()) {
                String mimeType = Files.probeContentType(file.toPath());
                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.getResponseHeaders().set("Content-Type", mimeType);
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                logMessage("200 OK: " + filePath);
            } else {
                String response = "404 (Not Found)\n";
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                logMessage("404 Not Found: " + filePath);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SimpleWebServerApp app = new SimpleWebServerApp();
            app.setVisible(true);
        });
    }
}
