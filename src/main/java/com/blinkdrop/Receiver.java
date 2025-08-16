package com.blinkdrop;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.Collections;


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
            InetAddress addr = getLocalNonLoopbackAddress(); // see method below
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
}
