package com.blinkdrop;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.Collections;
import java.net.*;
import java.util.Collections;
import java.util.Enumeration;


public class Receiver{

    public static InetAddress getLocalNonLoopbackAddress() throws SocketException {
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (InetAddress address : Collections.list(ni.getInetAddresses())) {
                if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                    return address;
                }
            }
        }
        throw new RuntimeException("No non-loopback IPv4 address found");
    }

    public static void main(String[] args) {
        int port = 0;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            port = serverSocket.getLocalPort(); // assigned dynamic port if 0

            System.out.println("Listening on port " + port + "...");

            // Advertise mDNS service
            InetAddress addr = getBestLocalAddress();
            System.out.println("Using local address: " + addr.getHostAddress() + " on interface for JmDNS");
            JmDNS jmdns = JmDNS.create(addr);

            //JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
            String osName = System.getProperty("os.name");
            String hostname = InetAddress.getLocalHost().getHostName();

            String uniqueName = osName+"_"+ hostname;

            ServiceInfo serviceInfo = ServiceInfo.create(
                    "_blinkdrop._tcp.local.",
                    uniqueName,
                    port,
                    "BlinkDrop's Java Receiver"

            ); // <- Add device name here);

            jmdns.registerService(serviceInfo);

            System.out.println("BlinkDrop receiver is discoverable on local network!");
            System.out.println("Service Name: " + serviceInfo.getName());
            System.out.println("Type: " + serviceInfo.getType());
            System.out.println("Port: " + serviceInfo.getPort());

            // Start listening for file transfers
            receiveFiles(serverSocket);

            // When the app exits (optional cleanup)
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    jmdns.unregisterAllServices();
                    jmdns.close();
                    serverSocket.close();
                    System.out.println("Receiver shut down and port released.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void receiveFiles(ServerSocket serverSocket) {
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Connection received from: " + socket.getInetAddress());

                DataInputStream dataIn = new DataInputStream(socket.getInputStream());

                // Read filename length and filename
                int fileNameLength = dataIn.readInt();
                if (fileNameLength <= 0) {
                    System.out.println("Invalid filename length");
                    continue;
                }

                byte[] fileNameBytes = new byte[fileNameLength];
                dataIn.readFully(fileNameBytes);
                String fileName = new String(fileNameBytes);

                // Read file size
                long fileSize = dataIn.readLong();

                // Save file
                String downloadPath = System.getProperty("user.home") + "/Downloads/";
                Files.createDirectories(Paths.get(downloadPath));

                File outFile = new File(downloadPath, fileName);
                try (FileOutputStream fileOut = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[100 * 1024 * 1024];
                    int bytesRead;
                    long totalRead = 0;

                    while (totalRead < fileSize &&
                            (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                        fileOut.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }

                    System.out.println("File saved to: " + outFile.getAbsolutePath());
                }

                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static InetAddress getBestLocalAddress() throws SocketException, UnknownHostException {
        InetAddress fallback = null;
        InetAddress localhost = InetAddress.getLocalHost();
        if (localhost != null && !localhost.isLoopbackAddress() && localhost instanceof Inet4Address) {
            // If getLocalHost gives a usable IPv4, keep as fallback
            fallback = localhost;
        }

        // Heuristics preference: Wi-Fi/WLAN/Ethernet interface names (case-insensitive)
        String[] preferIfNames = new String[] { "wi-fi", "wifi", "wlan", "wireless", "ethernet", "en0", "en1" };

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

            // Optional: skip docker/virtual adapters by name
            String ifName = ni.getDisplayName() == null ? ni.getName() : ni.getDisplayName();
            String ifNameLower = ifName == null ? "" : ifName.toLowerCase();

            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (!(addr instanceof Inet4Address)) continue;            // prefer IPv4
                if (addr.isLoopbackAddress()) continue;
                String s = addr.getHostAddress();
                if (s.startsWith("169.254.")) continue;                 // skip link-local
                if (s.startsWith("127.")) continue;

                // prefer site-local/private addresses
                if (addr.isSiteLocalAddress()) {
                    // remember first site-local as candidate
                    if (candidate == null) candidate = addr;

                    // if the interface name looks like wifi/ethernet, prefer it
                    for (String pref : preferIfNames) {
                        if (ifNameLower.contains(pref)) {
                            preferredByName = addr;
                            break;
                        }
                    }
                    if (preferredByName != null) break;
                } else {
                    // non-site-local but non-loopback ipv4 (rare); keep as fallback if nothing else
                    if (candidate == null) candidate = addr;
                }
            }
            if (preferredByName != null) break;
        }

        if (preferredByName != null) return preferredByName;
        if (candidate != null) return candidate;
        if (fallback != null && !fallback.isLoopbackAddress() && fallback instanceof Inet4Address) return fallback;

        // Last resort: try all addresses of InetAddress.getLocalHost() or throw
        if (localhost != null && localhost instanceof Inet4Address && !localhost.isLoopbackAddress()) {
            return localhost;
        }

        throw new RuntimeException("No usable IPv4 address found");
    }

}
