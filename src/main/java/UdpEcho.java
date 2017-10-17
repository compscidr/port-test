import org.apache.commons.cli.*;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

import java.io.*;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;

/*
 * Runs a UDP echo client and server across a large number of ports
 * Note that if the server port range is too high the JVM can run
 * out of threads due to the number of threads created
 * (one for each port)
 *
 * This code will run as either the server or client depending on
 * what parameters are passed to the program. It is also possible
 * to control whether to use UDP or TCP.
 */
public class UdpEcho {
    public static void main(String[] args) {

        String host = "127.0.0.1";
        int incomingPort = 2000;
        int startPort = 80;
        int endPort = 80;
        int timeout_ms = 200;

        // create Options object
        Options options = new Options();

        // add all of the command line options
        options.addOption("s", "server", false, "Sets the role as echo server (default behavior)");
        options.addOption("c", "client", true, "Sets the role as echo client. Connects to server specified.");
        options.addOption("p","startPort", true, "The starting port, defaults to 80");
        options.addOption("e", "endPort", true, "The ending port, defaults to 80");
        options.addOption("i", "incomingPort", true, "Incoming UDP client port, defaults to 2000");
        options.addOption("h","help", false, "Displays this message");
        options.addOption("t","timeOut", true, "The timeout to wait for a response from the server");
        options.addOption("u", "udp", true, "Set to false if we should use TCP instead of UDP. Default to UDP");

        // create the parser
        CommandLineParser parser = new DefaultParser();
        try {
            // parse the command line arguments
            CommandLine line = parser.parse( options, args );
            if(line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "UdpEcho", options );
                return;
            }

            if(line.hasOption("client") && line.hasOption("server")) {
                System.out.println("Error! This program can't run as both server and client at same time!");
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "UdpEcho", options );
                return;
            }

            if(line.hasOption("endPort")) {
                endPort = Integer.parseInt(line.getOptionValue("endPort"));
            }

            if(line.hasOption("startPort")) {
                startPort = Integer.parseInt(line.getOptionValue("startPort"));
                if(!line.hasOption("endPort")) {
                    endPort = startPort;
                }
            }

            if(startPort > endPort) {
                System.out.println("Error! start port (" + startPort + ") must be lower than end port (" + endPort + ")");
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "UdpEcho", options );
                return;
            }

            if(line.hasOption("client")) {
                host = line.getOptionValue("client");
                if(line.hasOption("incomingPort")) {
                    incomingPort = new Integer(line.getOptionValue("incomingPort"));
                }

                if(line.hasOption("timeOut")) {
                    timeout_ms = new Integer(line.getOptionValue("timeOut"));
                }

                while(startPort <= endPort) {
                    if(line.hasOption("udp") && (line.getOptionValue("udp")).toLowerCase().equals("false")) {
                        tcpClient(host, startPort);
                    } else {
                        udpClient(host, startPort, incomingPort, timeout_ms);
                    }
                    startPort++;
                }
            } else {
                while(startPort <= endPort) {
                    if (line.hasOption("udp") && (line.getOptionValue("udp")).toLowerCase().equals("false")) {
                        tcpServer(startPort);
                    } else {
                        udpServer(startPort);
                    }
                    startPort++;
                }
            }

        }
        catch( ParseException exp ) {
            // oops, something went wrong
            System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
        }
    }
    
    public static void udpClient(String host, int outgoingport, int incomingport, int timeout_ms) {
        try {
            byte[] buf = "TEST FROM APP".getBytes();
            DatagramSocket socket = new DatagramSocket(incomingport);
            socket.setReuseAddress(true);
            DatagramPacket p = new DatagramPacket(buf, buf.length, InetAddress.getByName(host), outgoingport);
            socket.send(p);
            System.out.println("Sending data to " + host + ":" + outgoingport);
            socket.setSoTimeout(timeout_ms);
            DatagramPacket p2 = new DatagramPacket(buf, buf.length);
            socket.receive(p2);
            byte[] recv = new byte[p2.getLength()];
            System.arraycopy(p2.getData(), 0, recv, 0, p2.getLength());
            System.out.println("GOT " + p2.getLength() + " bytes: " + new String(recv));
            System.out.println("FROM " + p2.getAddress() + ":" + p2.getPort());
            socket.close();
        } catch(Exception ex) {
            System.out.println(ex.toString());
        }
    }
    
    public static void udpServer(int port) {
        new Thread() {
            public void run() {
                try {
                    DatagramSocket socket = new DatagramSocket(port);
                    byte[] buf = new byte[1024];
                    DatagramPacket p = new DatagramPacket(buf, 1024);
                    while (true) {
                        System.out.println("Waiting for UDP data on port " + socket.getLocalPort());
                        socket.receive(p);
                        byte[] recv = new byte[p.getLength()];
                        System.arraycopy(p.getData(), 0, recv, 0, p.getLength());
                        System.out.println("GOT " + p.getLength() + " bytes: " + new String(recv));
                        System.out.println("FROM " + p.getAddress() + ":" + p.getPort());
                        DatagramPacket p2 = new DatagramPacket(p.getData(), p.getLength());
                        p2.setAddress(p.getAddress());
                        p2.setPort(p.getPort());
                        socket.send(p2);
                    }
                } catch(IOException ex) {
                    System.out.println(ex.toString());
                }
            }
        }.start();
    }

    public static void tcpClient(String host, int port) {
        try {
            Socket socket = new Socket(InetAddress.getByName(host), port);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write("test\n".getBytes());
            String line = bufferedReader.readLine();
            System.out.println("GOT: " + line);
        } catch(IOException ex) {
            System.out.println(ex.toString());
        }
    }

    public static void tcpServer(int port) {
        new Thread() {
            public void run() {
                try {
                    ServerSocket socket = new ServerSocket(port);
                    while (true) {
                        System.out.println("Waiting for TCP connection");
                        Socket client = socket.accept();

                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        OutputStream outputStream = client.getOutputStream();

                        String line = bufferedReader.readLine();
                        System.out.println(line);
                        outputStream.write(line.getBytes());

                        client.close();
                    }
                } catch(IOException ex) {
                    System.out.println(ex.toString());
                }
            }
        }.start();
    }
}
