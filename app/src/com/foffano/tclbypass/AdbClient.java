package com.foffano.tclbypass;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Cliente mínimo do protocolo ADB (o mesmo que o comando `adb` usa), para o app
 * conversar com o adbd da própria TV em localhost:5555 e rodar comandos como "shell".
 */
public class AdbClient implements AutoCloseable {
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;

    private static final int AUTH_TOKEN = 1;
    private static final int AUTH_SIGNATURE = 2;
    private static final int AUTH_RSAPUBLICKEY = 3;

    private static final int VERSION = 0x01000000;
    private static final int MAX_DATA = 4096;

    // Prefixo DigestInfo do SHA-1 (o adbd assina/verifica o token como se fosse um hash SHA-1).
    private static final byte[] SHA1_PREFIX = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int nextLocalId = 1;

    private static final class Msg {
        int cmd, arg0, arg1;
        byte[] data;
    }

    /** Conecta e autentica. Na primeira vez a TV mostra "Permitir depuração?" e isto espera até authTimeoutMs. */
    public AdbClient(String host, int port, File keyDir, int authTimeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();

        KeyPair keys = loadOrCreateKeys(keyDir);
        socket.setSoTimeout(10000);
        send(A_CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.UTF_8));

        boolean sentSignature = false;
        while (true) {
            Msg m = read();
            if (m.cmd == A_CNXN) {
                socket.setSoTimeout(60000);
                return;
            }
            if (m.cmd != A_AUTH || m.arg0 != AUTH_TOKEN) {
                throw new IOException("resposta inesperada do adbd: " + Integer.toHexString(m.cmd));
            }
            if (!sentSignature) {
                send(A_AUTH, AUTH_SIGNATURE, 0, sign((RSAPrivateKey) keys.getPrivate(), m.data));
                sentSignature = true;
            } else {
                // Chave ainda não autorizada: manda a pública e espera o usuário aceitar na TV.
                send(A_AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyBytes((RSAPublicKey) keys.getPublic()));
                socket.setSoTimeout(authTimeoutMs);
            }
        }
    }

    /** Roda um comando no shell da TV (com permissões de adb) e devolve a saída. */
    public String shell(String command) throws IOException {
        int localId = nextLocalId++;
        send(A_OPEN, localId, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int remoteId = 0;
        while (true) {
            Msg m = read();
            if (m.arg1 != localId) continue; // mensagem de outro stream (ex.: CLSE atrasado)
            if (m.cmd == A_OKAY) {
                remoteId = m.arg0;
            } else if (m.cmd == A_WRTE) {
                output.write(m.data);
                send(A_OKAY, localId, m.arg0, null);
            } else if (m.cmd == A_CLSE) {
                if (remoteId != 0) send(A_CLSE, localId, remoteId, null);
                break;
            }
        }
        return output.toString("UTF-8");
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void send(int cmd, int arg0, int arg1, byte[] data) throws IOException {
        int len = data == null ? 0 : data.length;
        int sum = 0;
        for (int i = 0; i < len; i++) sum += data[i] & 0xff;
        ByteBuffer b = ByteBuffer.allocate(24 + len).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(len).putInt(sum).putInt(~cmd);
        if (len > 0) b.put(data);
        out.write(b.array());
        out.flush();
    }

    private Msg read() throws IOException {
        byte[] h = new byte[24];
        in.readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        m.data = new byte[len];
        in.readFully(m.data);
        return m;
    }

    private static byte[] sign(RSAPrivateKey key, byte[] token) {
        int k = (key.getModulus().bitLength() + 7) / 8;
        byte[] em = new byte[k];
        int t = SHA1_PREFIX.length + token.length;
        em[0] = 0;
        em[1] = 1;
        for (int i = 2; i < k - t - 1; i++) em[i] = (byte) 0xff;
        em[k - t - 1] = 0;
        System.arraycopy(SHA1_PREFIX, 0, em, k - t, SHA1_PREFIX.length);
        System.arraycopy(token, 0, em, k - token.length, token.length);
        BigInteger s = new BigInteger(1, em).modPow(key.getPrivateExponent(), key.getModulus());
        byte[] raw = s.toByteArray();
        byte[] sig = new byte[k];
        int copy = Math.min(raw.length, k);
        System.arraycopy(raw, raw.length - copy, sig, k - copy, copy);
        return sig;
    }

    /** Chave pública no formato do adb: struct RSAPublicKey do Android em base64 + " nome". */
    private static byte[] publicKeyBytes(RSAPublicKey key) {
        int words = 64; // 2048 bits
        BigInteger n = key.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(words * 32 * 2).mod(n);

        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words);
        b.putInt(n0inv.intValue());
        putWords(b, n, words);
        putWords(b, rr, words);
        b.putInt(key.getPublicExponent().intValue());
        String s = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " tcl-bypass@tv\0";
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void putWords(ByteBuffer b, BigInteger v, int words) {
        BigInteger mask = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);
        for (int i = 0; i < words; i++) {
            b.putInt(v.shiftRight(32 * i).and(mask).intValue());
        }
    }

    private static KeyPair loadOrCreateKeys(File dir) throws IOException {
        File priv = new File(dir, "adbkey.pk8");
        File pub = new File(dir, "adbkey.x509");
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            if (priv.exists() && pub.exists()) {
                return new KeyPair(
                        kf.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pub.toPath()))),
                        kf.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(priv.toPath()))));
            }
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            try (FileOutputStream f = new FileOutputStream(priv)) {
                f.write(kp.getPrivate().getEncoded());
            }
            try (FileOutputStream f = new FileOutputStream(pub)) {
                f.write(kp.getPublic().getEncoded());
            }
            return kp;
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException(e);
        }
    }
}
