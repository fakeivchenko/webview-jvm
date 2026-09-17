package dev.ivchenko.webview.chrome.devtools;

import dev.ivchenko.webview.util.ThrowableUtil;
import lombok.SneakyThrows;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * A WebSocket client for one purpose: talking to a browser on the loopback interface.
 *
 * <p>Written by hand instead of using {@code java.net.http}: the JDK client is fine, but it brings HTTP/2, TLS and the
 * SSL engine along, roughly ten megabytes in a native image, for a plain-text socket to {@code 127.0.0.1}. RFC 6455
 * client side without extensions is a handshake and a frame format; that is all there is here. Text messages arrive on
 * the reader thread the connection owns.</p>
 */
class WebSocketTransport implements AutoCloseable {
    private static final String ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int OPCODE_CONTINUATION = 0x0;
    private static final int OPCODE_TEXT = 0x1;
    private static final int OPCODE_CLOSE = 0x8;
    private static final int OPCODE_PING = 0x9;
    private static final int OPCODE_PONG = 0xA;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Consumer<String> onMessage;
    private final Runnable onClose;

    private volatile boolean closed;

    private WebSocketTransport(Socket socket, Consumer<String> onMessage, Runnable onClose) throws IOException {
        this.socket = socket;
        this.in = new BufferedInputStream(socket.getInputStream());
        this.out = socket.getOutputStream();
        this.onMessage = onMessage;
        this.onClose = onClose;
    }

    /** Opens {@code ws://host:port/path}, then delivers every text message to {@code onMessage}. */
    @SneakyThrows
    static WebSocketTransport connect(String host, int port, String path, Consumer<String> onMessage,
                                      Runnable onClose) {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5_000);
        socket.setTcpNoDelay(true);
        try {
            WebSocketTransport transport = new WebSocketTransport(socket, onMessage, onClose);
            transport.handshake(host, port, path);
            Thread reader = new Thread(transport::readLoop, "webview-chromium");
            reader.setDaemon(true);
            reader.start();
            return transport;
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    /** One plain HTTP GET, for the browser's {@code /json/list} endpoint; returns the body. */
    @SneakyThrows
    static String httpGet(String host, int port, String path) {
        try (Socket socket = new Socket(host, port)) {
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: " + host + ":" + port
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            InputStream input = new BufferedInputStream(socket.getInputStream());
            String status = readLine(input);
            if (!status.startsWith("HTTP/1.1 200")) throw new IOException("Unexpected response: " + status);
            int length = -1;
            for (String line = readLine(input); !line.isEmpty(); line = readLine(input)) {
                if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                    length = Integer.parseInt(line.substring(15).strip());
                }
            }
            // The browser keeps the connection open whatever the request says, so the body must be sized.
            byte[] body = length >= 0 ? input.readNBytes(length) : input.readAllBytes();
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /** Sends one text message; safe to call from any thread. */
    @SneakyThrows
    void send(String text) {
        if (this.closed) throw new IOException("WebSocket is closed");
        this.writeFrame(OPCODE_TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        if (this.closed) return;
        try {
            this.writeFrame(OPCODE_CLOSE, new byte[] {0x03, (byte) 0xE8});
        } catch (IOException _) {
            // The peer may already be gone; the socket is closed either way.
        }
        this.handleClosed();
    }

    private void handshake(String host, int port, String path) throws IOException {
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        String key = Base64.getEncoder().encodeToString(nonce);
        this.out.write(("GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + ":" + port + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        this.out.flush();

        String status = readLine(this.in);
        if (!status.startsWith("HTTP/1.1 101")) throw new IOException("WebSocket upgrade refused: " + status);
        String accept = null;
        for (String line = readLine(this.in); !line.isEmpty(); line = readLine(this.in)) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).equalsIgnoreCase("Sec-WebSocket-Accept")) {
                accept = line.substring(colon + 1).strip();
            }
        }
        if (!expectedAccept(key).equals(accept)) throw new IOException("WebSocket accept key mismatch");
    }

    @SneakyThrows
    private static String expectedAccept(String key) {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest((key + ACCEPT_GUID).getBytes(StandardCharsets.US_ASCII));
        return Base64.getEncoder().encodeToString(digest);
    }

    private void readLoop() {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        try {
            while (!this.closed) {
                int first = this.in.read();
                if (first < 0) throw new EOFException();
                boolean last = (first & 0x80) != 0;
                int opcode = first & 0x0F;
                byte[] payload = this.readPayload();

                switch (opcode) {
                    case OPCODE_TEXT, OPCODE_CONTINUATION -> {
                        message.write(payload);
                        if (last) {
                            String text = message.toString(StandardCharsets.UTF_8);
                            message.reset();
                            this.deliver(text);
                        }
                    }
                    case OPCODE_PING -> this.writeFrame(OPCODE_PONG, payload);
                    case OPCODE_CLOSE -> {
                        this.handleClosed();
                        return;
                    }
                    default -> { }
                }
            }
        } catch (IOException _) {
            this.handleClosed();
        }
    }

    private byte[] readPayload() throws IOException {
        int second = this.in.read();
        if (second < 0) throw new EOFException();
        long length = second & 0x7F;
        if (length == 126) {
            length = ((long) this.readByte() << 8) | this.readByte();
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) length = (length << 8) | this.readByte();
        }
        byte[] mask = (second & 0x80) != 0 ? this.in.readNBytes(4) : null;
        if (length > Integer.MAX_VALUE) throw new IOException("Frame too large: " + length);
        byte[] payload = this.in.readNBytes((int) length);
        if (payload.length != length) throw new EOFException();
        if (mask != null) {
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
        }
        return payload;
    }

    private int readByte() throws IOException {
        int value = this.in.read();
        if (value < 0) throw new EOFException();
        return value;
    }

    /** Writes one masked frame, as a client must; the whole frame goes out under one lock so frames never interleave. */
    private synchronized void writeFrame(int opcode, byte[] payload) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(payload.length + 14);
        frame.write(0x80 | opcode);
        if (payload.length < 126) {
            frame.write(0x80 | payload.length);
        } else if (payload.length < 65_536) {
            frame.write(0x80 | 126);
            frame.write(payload.length >>> 8);
            frame.write(payload.length);
        } else {
            frame.write(0x80 | 127);
            for (int shift = 56; shift >= 0; shift -= 8) frame.write((int) ((long) payload.length >>> shift));
        }
        byte[] mask = new byte[4];
        RANDOM.nextBytes(mask);
        frame.write(mask);
        for (int i = 0; i < payload.length; i++) frame.write(payload[i] ^ mask[i & 3]);
        this.out.write(frame.toByteArray());
        this.out.flush();
    }

    private void deliver(String text) {
        try {
            this.onMessage.accept(text);
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
    }

    private void handleClosed() {
        if (this.closed) return;
        this.closed = true;
        try {
            this.socket.close();
        } catch (IOException _) {
        }
        try {
            this.onClose.run();
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int c;
        while ((c = input.read()) >= 0 && c != '\n') {
            if (c != '\r') line.write(c);
        }
        if (c < 0 && line.size() == 0) throw new EOFException();
        return line.toString(StandardCharsets.US_ASCII);
    }
}
