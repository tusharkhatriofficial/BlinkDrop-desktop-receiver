package com.blinkdrop;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Collections;
import java.util.Enumeration;

public class Receiver {

    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private JmDNS jmdns;
    private TrayIcon trayIcon;

    public Receiver(TrayIcon trayIcon) {
        this.trayIcon = trayIcon;
    }

    public void start() {
        int port = 0;
        try {
            serverSocket = new ServerSocket(port);
            port = serverSocket.getLocalPort();

            System.out.println("Listening on port " + port + "...");

            InetAddress addr = getBestLocalAddress();
            jmdns = JmDNS.create(addr);

            String osName = System.getProperty("os.name");
            String hostname = InetAddress.getLocalHost().getHostName();
            String uniqueName = osName + "_" + hostname;

            ServiceInfo serviceInfo = ServiceInfo.create(
                    "_blinkdrop._tcp.local.",
                    uniqueName,
                    port,
                    "BlinkDrop Java Receiver"
            );

            jmdns.registerService(serviceInfo);
            System.out.println("BlinkDrop is discoverable!");

            running = true;
            listenLoop();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (jmdns != null) {
                jmdns.unregisterAllServices();
                jmdns.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Receiver stopped.");
    }

    private void listenLoop() {
        try {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("Connection from: " + socket.getInetAddress());
                    handleClient(socket);
                } catch (SocketException e) {
                    if (!running) {
                        System.out.println("Socket closed.");
                    } else {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            if (running) e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        try (DataInputStream dataIn = new DataInputStream(socket.getInputStream())) {

            int fileNameLength = dataIn.readInt();
            if (fileNameLength <= 0) return;

            byte[] fileNameBytes = new byte[fileNameLength];
            dataIn.readFully(fileNameBytes);
            String fileName = new String(fileNameBytes);

            long fileSize = dataIn.readLong();

            String downloadPath = System.getProperty("user.home") + "/Downloads/";
            Files.createDirectories(Paths.get(downloadPath));

            File outFile = new File(downloadPath, fileName);
            try (FileOutputStream fileOut = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[1024 * 1024];
                int bytesRead;
                long totalRead = 0;

                while (totalRead < fileSize &&
                        (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                    fileOut.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }
                System.out.println("Saved: " + outFile.getAbsolutePath());

                if (trayIcon != null) {
                    trayIcon.displayMessage("File Received: " + fileName,
                            "Saved to Downloads folder.",
                            TrayIcon.MessageType.INFO);
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException ignore) {}
        }
    }

    public static InetAddress getBestLocalAddress() throws SocketException, UnknownHostException {
        InetAddress fallback = null;
        InetAddress localhost = InetAddress.getLocalHost();
        if (localhost != null && !localhost.isLoopbackAddress() && localhost instanceof Inet4Address) {
            fallback = localhost;
        }

        String[] preferIfNames = new String[]{"wi-fi", "wifi", "wlan", "wireless", "ethernet", "en0", "en1"};
        InetAddress candidate = null;
        InetAddress preferredByName = null;

        Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
        while (nis.hasMoreElements()) {
            NetworkInterface ni = nis.nextElement();
            try {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
            } catch (SocketException ignored) {
                continue;
            }

            String ifName = ni.getDisplayName() == null ? ni.getName() : ni.getDisplayName();
            String ifNameLower = ifName == null ? "" : ifName.toLowerCase();

            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (!(addr instanceof Inet4Address)) continue;
                if (addr.isLoopbackAddress()) continue;
                String s = addr.getHostAddress();
                if (s.startsWith("169.254.") || s.startsWith("127.")) continue;

                if (addr.isSiteLocalAddress()) {
                    if (candidate == null) candidate = addr;
                    for (String pref : preferIfNames) {
                        if (ifNameLower.contains(pref)) {
                            preferredByName = addr;
                            break;
                        }
                    }
                    if (preferredByName != null) break;
                } else {
                    if (candidate == null) candidate = addr;
                }
            }
            if (preferredByName != null) break;
        }

        if (preferredByName != null) return preferredByName;
        if (candidate != null) return candidate;
        if (fallback != null) return fallback;

        throw new RuntimeException("No usable IPv4 address found");
    }
}
