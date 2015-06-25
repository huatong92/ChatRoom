/* Author: Hua Tong
 * Uni: ht2334
 * */

package Client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Client {
	// heartbeat time, unit: seconds
	private final int HEARTBEAT_TIME = 5;
	// the port of the serverSocket created by this client
	private int serverSocketPort;
	
	// ths ip and port of the server to connect to 
	private String serverIP;
	private int serverPort;
	
	// record the client's username, for consistency in non-persistent connection
	private String username = null;
	// record the client's status, default is false
	private boolean login = false;
	
	// record other users' ip and port information for private chat
	private Map<String, String> userIP;
	private Map<String, Integer> userPort;
	private Map<String, Boolean> consent;
	
	private KeyboardOutThread keyboardThread;

	private String lastCommand;
	// store the message before consent is established
	private String msgBeforeConsent;
	// constructor
	// read in server's IP address and port number
	public Client(String ip, int port) {
		try {
			serverIP = ip;
			serverPort = port;
			Socket clientSocket = new Socket(ip, port);
			// for input, output from server
			
			new ServerSocketThread();
						
			// create a new thread to get info from server
			new ServerInThread(clientSocket);

			// create a new thread to get info from keyboard
			keyboardThread = new KeyboardOutThread(clientSocket);

			DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());
			
			new Heartbeat();
			
			userIP = new HashMap<String, String>();
			userPort = new HashMap<String, Integer>();
			consent = new HashMap<String, Boolean>();
			
			lastCommand = "";
			
			// to keep a format
			output.writeUTF("New Start");
			output.flush();
			output.writeUTF("New Start");
			output.flush();
			
		} catch (UnknownHostException e) {
			System.out.println("Error in finding the server.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * ServerSocketThread extends Thread is nested under Client because it
	 * belongs to client and only used by client 
	 * This thread is to use a server socket to accept requests
	 */
	public class ServerSocketThread extends Thread{
		ServerSocket serverSocket;
		
		public ServerSocketThread(){
			// create a new ServerSocket for server and p2p to use
			// automatically find a port to use
			boolean available = false;
			while (!available){
				serverSocketPort = (int) Math.round(Math.random()*100) + 2000;
				try {
					serverSocket = new ServerSocket(serverSocketPort);
					available = true;
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Can't connect to server");
				}
			}
			
			start();
			
		}
		
		public void run(){
			while (true){
				try {
					Socket socket = serverSocket.accept();
					if (socket.isConnected()) {
						DataInputStream in = new DataInputStream(socket.getInputStream());
						String lines = in.readUTF();
						boolean isPrivate = false;
						
						// if the request is send from another user
						if (lines != null && lines.length() > 0){
							if (lines.charAt(0) == ('*')){
								keyboardThread.addPrivate(lines.substring(1), socket);
								isPrivate = true;
							}
						} 
						if (!isPrivate){
							keyboardThread.setSocket(socket);
						}
						
						// create a new thread for this line
						new ServerInThread(socket);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			
		}
	}
	/*
	 * fromServerThread extends Thread is nested under Client because it belongs
	 * to client and only used by client 
	 * this thread is to listen from the server and print server's information
	 */
	public class ServerInThread extends Thread {
		Socket socket;
		DataInputStream in;
		DataOutputStream out;

		public ServerInThread(Socket socket) throws IOException {
			this.socket = socket;
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
			start();
		}

		// read from input and write to output, for thread use
		@Override
		public void run() {
			boolean isRunning = true;
			try {
				while (isRunning) {
					String lines = in.readUTF();
					lastCommand = lines;
					if (lines != null && lines.length() > 0){
						// server send logged in username to this client to keep a seperate record
						if (lines.charAt(0) == '*') {
							username = lines.substring(1);
							login = true;
							out.writeUTF("#" + serverSocketPort);
							out.flush();
						} 
						else if (lines.equals("logout")){
							out.close();
							in.close();
							socket.close();
							System.exit(0);
						}
						else if (lines.equals("disconnect")){
							keyboardThread.closeOut();
							out.close();
							in.close();
							socket.close();
							
						}
						// server sends the information of another client's socket
						// store this info
						else if (lines.charAt(0) == '#'){
							lines = lines.substring(1);
							String[] info = lines.split("\\s");
	
							userIP.put(info[0], info[1]);
							userPort.put(info[0], Integer.valueOf(info[2]));
							System.out.println("Address is stored");
						}
						// get consent
						else if (lines.charAt(0) == '$'){
							lines = lines.substring(1);
							String[] info = lines.split("\\s");
							if (info[0].equals("Yes")){
								consent.put(info[1], true);
								System.out.println(info[0] + " accepted private chat. Please enter 'chatnow' to resend your message");
							}else{
								consent.put(info[1], false);
								System.out.println(info[1] + " declined private chat.");
							}
						}
						else {
							System.out.println(lines);
						}
					}

				}

			} catch (Exception e) {
				//e.printStackTrace();
			}

		}

	}
	
	
	/*
	 * FromKeyboardThread extends Thread is nested under Client because it
	 * belongs to client and only used by client 
	 * This thread is to read from keyboard and output to server
	 */
	public class KeyboardOutThread extends Thread {
		// store the Username for consistency in non-persistent connection
		Socket socket;
		DataOutputStream out;
		BufferedReader reader;
		
		// store the socket and output of each private chat 
		Map<String, Socket> privateChat;
		
		public KeyboardOutThread(Socket socket) throws IOException {
			this.socket = socket;
			out = new DataOutputStream(socket.getOutputStream());
			reader = new BufferedReader(new InputStreamReader(System.in));
			privateChat = new HashMap<String, Socket>();
			
			start();
		}
		
		// to set the socket or private socket
		public void setSocket(Socket socket) throws IOException {
			this.socket = socket;
			out = new DataOutputStream(socket.getOutputStream());
			
		}
		
		public void addPrivate(String user, Socket socket) throws IOException {
			privateChat.put(user, socket);
		}
		
		// to close the output stream
		public void closeOut() throws IOException{
			out.close();
		}
		
		@Override
		public void run() {
			while (true) {
				String lines = null;
	
				try {
					lines = reader.readLine();
				} catch (IOException e2) {
					e2.printStackTrace();
				}
				
				// to get consent
				if (lastCommand == "Do you accept the request? (Y/N)"){
					try {
						DataOutputStream privateOut = new DataOutputStream(privateChat.get("server").getOutputStream());
					
						privateOut.writeUTF(lines);
						privateOut.flush();
						
					} catch (IOException e) {
						e.printStackTrace();
					}
					continue;
				}
				
				if (lines.equals("chatnow")){
					lines = msgBeforeConsent;
				}
				
				// deal with private chat situation
				if (lines.contains(" ")){
					String[] info = lines.split("\\s+");
					
					if (info[0].equals("private") && info.length > 2){
						
						// directly send message if the socket to the other user is open
						if (privateChat.containsKey(info[1])){
							Socket socket = privateChat.get(info[1]);
							DataOutputStream privateOut;
							
							try {
								privateOut = new DataOutputStream(socket.getOutputStream());
							
								String msg = "";
								for (int i = 2; i < info.length; i++){
									msg += info[i] + " ";
								}
							
								privateOut.writeUTF(info[1] + ": " + msg);
								privateOut.flush();

							} catch (IOException e) {
								e.printStackTrace();
							}
							continue;

						}
						else {
							// if don't have ip information of the other user
							if (!userIP.containsKey(info[1])){
								System.out.println("Do: getaddress <user>");
								continue;
							}
							if (!consent.containsKey(info[1])){
								consent.put(info[1], false);
								msgBeforeConsent = lines;
							}
							else if (consent.get(info[1])){
								// create a socket to connect to the other client
								Socket socket;
								try {
									socket = new Socket(userIP.get(info[1]), userPort.get(info[1]));
									DataOutputStream privateOut = new DataOutputStream(socket.getOutputStream());
									// indicate this connection is from a user
									privateOut.writeUTF("*" + username);
									privateOut.flush();
									
									String msg = "";
									for (int i = 2; i < info.length; i++){
										msg += info[i] + " ";
									}
									
									privateOut.writeUTF(info[1] + ": " + msg);
									privateOut.flush();
									
									// add this socket to keyboard thread
									addPrivate(info[1], socket);
									// create a new thread to get info from that client
									new ServerInThread(socket);
									
									
								} catch (UnknownHostException e) {
									e.printStackTrace();
								} catch (IOException e) {
									e.printStackTrace();
								}
								continue;
							}
						}
							
					}
						
					
				}
				// if not private command, send to server
				try{
					out.writeUTF(lines);
					out.flush();
				} catch (IOException e) {
					// if not connected, create a new socket to connect to server
					try {
						Socket socket = new Socket(serverIP, serverPort);
						out = new DataOutputStream(socket.getOutputStream());
						
						// create a new thread to get info from server
						new ServerInThread(socket);
						
						if (login) {
							out.writeUTF("*" + username);
							out.flush();
							
							if (lastCommand == "Do you accept the request? (Y/N)"){
								try {
									DataOutputStream privateOut = new DataOutputStream(privateChat.get("server").getOutputStream());
								
									privateOut.writeUTF(lines);
									privateOut.flush();
									
								} catch (IOException e2) {
									e2.printStackTrace();
								}
								
							}
							else{
								out.writeUTF(lines);
								out.flush();	
							}
							
						} else {
							out.writeUTF("New Start");
							out.flush();
						}
						
					} catch (UnknownHostException e1) {
						System.out.println("Error in finding the server.");
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
		
			}
		}
	}



	/*
	 * heartneatThread extends Thread is a nested class under Client it send
	 * LIVE signal to the server every 30 seconds
	 */
	public class Heartbeat extends Thread{
		public Heartbeat() {
			ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
			exec.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					try {
						Socket socket = new Socket(serverIP, serverPort);
						DataOutputStream output = new DataOutputStream(socket.getOutputStream());
						output.writeUTF("LIVE" + username);
						output.flush();
						socket.close();
					} catch (UnknownHostException e) {
						System.out.println("Error in finding the server.");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}, 0, HEARTBEAT_TIME, TimeUnit.SECONDS);
		}
		
	}
	
	public static void main(String args[]) {

		new Client(args[0], Integer.parseInt(args[1]));


	}
}
