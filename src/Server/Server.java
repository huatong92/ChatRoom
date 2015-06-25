/* Author: Hua Tong
 * Uni: ht2334
 * */
package Server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/* Class Server
 * Defines all actions the Server end will perform 
 * 
 * */
public class Server {
	// block time for login
	private final int BLOCK_TIME = 60000;
	// timeout for socket to close
	private final int CONNECT_TIMEOUT = 30000;
	// every one minute check heartbeat
	private final int CHECK_HEARTBEAT_TIME = 60000;
	// logout if heartbeat hasn't send for 60 seconds
	private final int LOGOUT_TIME = 60000;
	private final String CREDENTIAL_PATH = "credentials.txt";

	private ServerSocket serverSocket;
	
	// the hash table of connected threads, username as key, threads as value
	private Map<String, Socket> connectedUsers;
	private List<String> userCredentials;
	// the list of username of all registered users
	private List<String> allUsers;
	// the hash table to record logged in users, use username as key, ip address as value
	private Map<String, String> loggedInUsersWithIP;
	// the hash table to record logged in users, use username as key, port number as value
	private Map<String, Integer> loggedInUsersWithPort;
	// the hash table ofblocked users because of false login, username as key, block start time as value
	private Map<String, Long> blockedUsers;
	// for offline messages, key: receiver's username, value: a list of messages
	private Map<String, List<String>> offlineMsg;
	// keep the lists of blocked users, username as key, the list of name of users who blocked this user as value
	private Map<String, List<String>> blockedBy;
	// store the login times for each username
	private Map<String, Integer> userLoginTimes; 
	// store the last time a user send a heartbeat
	private Map<String, Long> heartbeat;
	
	
	// constructor
	public Server(int port) throws IOException {

		// create a new serverSocket using the parsed port
		serverSocket = new ServerSocket(port);
		System.out.println("Simple Chat Server");

		// initialize
		userCredentials = new ArrayList<String>();
		allUsers = new ArrayList<String>();
		loggedInUsersWithIP = new HashMap<String, String>();
		loggedInUsersWithPort = new HashMap<String, Integer>();
		connectedUsers = new HashMap<String, Socket>();
		blockedUsers = new HashMap<String, Long>();
		blockedBy = new HashMap<String, List<String>>();
		userLoginTimes = new HashMap<String, Integer>();
		heartbeat = new HashMap<String, Long>();
		offlineMsg = new HashMap<String, List<String>>();

		// load user credential list from text file
		loadCredentials(CREDENTIAL_PATH);
		
		// check heartbeat thread
		new CheckHeartbeat();
		
		// start accepting client sockets
		acceptSocket();
		
		
	}

	// read from file that stores credential information
	// return a list of strings, each with "username password" format
	public void loadCredentials(String filePath) {

		String next = null;
		File f = new File(filePath);
		InputStreamReader reader;
		try {
			reader = new InputStreamReader(new FileInputStream(f));
			BufferedReader br = new BufferedReader(reader);
			while ((next = br.readLine()) != null) {
				String[] identity = next.split("\\s+");
				userCredentials.add(next);
				allUsers.add(identity[0]);
			}

			br.close();

		} catch (FileNotFoundException e) {
			System.out.println("Credential file not found!");
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	// start accept socket from client, for each accepted socket, create a new

	// accept sockets, create a new serverThread for each accepted socket
	public void acceptSocket() {
		// while server is not closed, accept client socket
		while (true) {
			try {
				// create a new socket for the client socket
				Socket socket = serverSocket.accept();
				if (socket.isConnected()) {
					// create a new thread for this line
					new ServerThread(socket);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}
	
	/* check if heartbeat are still there
	 * logout users who lost heartbeat
	 * */
	public class CheckHeartbeat extends Thread {
		// constructor
		public CheckHeartbeat() {
			ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
			exec.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					for (String user : loggedInUsersWithIP.keySet()){
						if (heartbeat.containsKey(user)){
							// if user has not send heart beat for LOGOUT_TIME, logout the user
							if (System.currentTimeMillis() - heartbeat.get(user) > LOGOUT_TIME){
								Socket socket = connectedUsers.get(user);
								try {
									DataOutputStream out = new DataOutputStream(socket.getOutputStream());
									out.writeUTF("logout");
									out.flush();
									socket.close();
								} catch (IOException e) {
									e.printStackTrace();
								}
								
								connectedUsers.remove(user);
								loggedInUsersWithIP.remove(user);
								loggedInUsersWithPort.remove(user);
								
							}
						}
						
					}
				}
			}, 0, CHECK_HEARTBEAT_TIME, TimeUnit.SECONDS);
		}
	}
	

	/*
	 * Nested class under Server ServerThread extends Thread defines everything
	 * a server has to do towards one client Making it nested under Server for
	 * some reasons: 1. it belongs to a server in logic 2. it is easier to
	 * access variables belongs to Server
	 */
	public class ServerThread extends Thread {
		// socket on the Server side to connect to one client
		private Socket socket;
		private String username;

		// in and out stream for the above socket
		private DataInputStream input;
		private DataOutputStream output;

		private String clientServerSocketIP;
		private int clientServerSocketPort;
		
		// constructor
		public ServerThread(Socket socket) throws IOException {
			// initialization
			this.socket = socket;

			input = new DataInputStream(socket.getInputStream());
			output = new DataOutputStream(socket.getOutputStream());

			// once constructed a new server thread, start run()
			start();
		}

		// @Override
		public void run() {
			// check if the login info is consistent with the user side
			boolean loggedIn = false;
			// store the current command for logout to catch exception
			String[] command = null;

			// if user is not logged in, ask him to login, otherwise, skip login
			try {
				String checkLogin = input.readUTF();
				String lines = input.readUTF();
				
				// if the information stored at client and server are the same,
				// see the user as logged in
				if (checkLogin.charAt(0) == '*') {
					if (loggedInUsersWithIP.containsKey(checkLogin.substring(1))) {
						loggedIn = true;
						username = checkLogin.substring(1);
					}
				}
				if (!loggedIn) {
					// run login process
					username = login();
				}

				// add this user to connectedUsers
				if (!connectedUsers.containsKey(username)) {
					connectedUsers.put(username, socket);
				}

				// set timeout
				socket.setSoTimeout(CONNECT_TIMEOUT);

				if (lines.equals("New Start")) {
					lines = input.readUTF();
				}
				
				boolean first = true;
				do {
					
						if (!first) {
							lines = input.readUTF();
						}
						command = lines.split("\\s+");
						// handle commands
						if (command[0].equals("message")) {
							if (command.length < 3) {
								output.writeUTF("Wrong format, use: message <user> <message>");
								output.flush();
							} else {
								String msg = "";
								for (int i = 2; i < command.length; i++){
									msg += command[i] + " ";
								}
								message(command[1], msg);
							}
						} else if (command[0].equals("broadcast")) {
							if (command.length < 2) {
								output.writeUTF("Wrong format, use: broadcast <message>");
								output.flush();
							} else {
								String msg = "";
								for (int i = 1; i < command.length; i++){
									msg += command[i] + " ";
								}
								broadcast(msg);
							}
						} else if (command[0].equals("online")){
							online();
						} else if (command[0].equals("block")){
							if (command.length != 2) {
								output.writeUTF("Wrong format, use: block <user>");
								output.flush();
							} else {
								block(command[1]);
							}
						} else if (command[0].equals("unblock")){
							if (command.length != 2) {
								output.writeUTF("Wrong format, use: unblock <user>");
								output.flush();
							} else {
								unblock(command[1]);
							}
						} else if (command[0].equals("getaddress")){
							if (command.length != 2) {
								output.writeUTF("Wrong format, use: getaddress <user>");
								output.flush();
							} else {
								getAddress(command[1]);
							}
						} 
						else if (command[0].equals("private")) {
							if (command.length < 3) {
								output.writeUTF("Wrong format, use: private <user> <message>");
								output.flush();
							} else {
								getConsent(command[1]);
							}
						}
						else if (command[0].equals("logout")) {
							connectedUsers.remove(username);
							loggedInUsersWithIP.remove(username);
							loggedInUsersWithPort.remove(username);
							output.writeUTF("logout");
							output.flush();
							socket.close();
						}
						else if (command[0].equals("LIVE")){
							heartbeat.put(command[1], System.currentTimeMillis());
						}
						else if (command != null){
							output.writeUTF("Invalid command.");
							output.flush();
							showCommands();
						}
						
						first = false;

					}  while (true);

			} catch (SocketTimeoutException e) {
				// If user has not send any message for a period of
				// time, disconnect with the user
				System.out.println(username + " has time out, disconnected");
				try {
					output.writeUTF("disconnect");
					output.flush();
				} catch (IOException e2) {
					e2.printStackTrace();
				}
				connectedUsers.remove(username);
				try {
					socket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			} catch (IOException e) {
				// when ctr+C is used by user
				connectedUsers.remove(username);
				loggedInUsersWithIP.remove(username);
				loggedInUsersWithPort.remove(username);
				
				//catchIOException();
			}

		}

		// handle broadcast messages command: to send a message to all online users
		public void broadcast(String msg) throws IOException {
			boolean sendAll = true;
			// to every logged in users
			for (String user : loggedInUsersWithIP.keySet()) {
				boolean blocked = false;
				// check is user has blocked the message sender
				if (blockedBy.containsKey(username)){
					if (blockedBy.get(username).contains(user)){
						blocked = true;
						sendAll = false;
					}
				}
				
				// exclude the message sender and whoever blocked him/her
				if (!user.equals(username) && !blocked){
					// if this user is currently connected, directly send message
					if (connectedUsers.containsKey(user)) {
						DataOutputStream out = new DataOutputStream(connectedUsers.get(user).getOutputStream());
						out.writeUTF(username + ": " + msg);
						out.flush();
					} else {
						Socket tempSocket = new Socket(loggedInUsersWithIP.get(user), loggedInUsersWithPort.get(user));
						DataOutputStream out = new DataOutputStream(tempSocket.getOutputStream());
						out.writeUTF("");
						out.flush();
						out.writeUTF(username + ": " + msg);
						out.flush();
						out.close();
						tempSocket.close();
					}
				}
			}
			if(!sendAll){
				output.writeUTF("Your message could not be delivered to some recipient because they blocked you");
				output.flush();
			}
			
		}

		// handle message command: to send a message through server
		public void message(String user, String msg) throws IOException {
			// if receiver is not a valid username, alert the user
			if (!allUsers.contains(user)) {
				output.writeUTF("There is no user: " + user);
				output.flush();
				return;
			}
			// if receiver has blocked this user
			if (blockedBy.containsKey(username)){
				if (blockedBy.get(username).contains(user)){
					output.writeUTF("You are blocked by " + user + ". You can't send him/her message");
					output.flush();
					return;
				}
			}
			// if receiver is not logged in, store message in offilineMsg
			if (!loggedInUsersWithIP.containsKey(user)) {
				List<String> list;
				if (offlineMsg.containsKey(user)){
					list = new ArrayList<String>(offlineMsg.get(user));
				} else {
					list = new ArrayList<String>();	
				}
				list.add(username + ": " + msg);
				offlineMsg.put(user, list);
				output.writeUTF(user + " is not online now. Offline message send.");
				output.flush();
			}
			// if receiver is connected now, directly send message
			else if (connectedUsers.containsKey(user)) {
				DataOutputStream out = new DataOutputStream(connectedUsers.get(user).getOutputStream());
				out.writeUTF(username + ": " +msg);
				out.flush();
			}
			// else: receiver is logged in, but not connected now
			// contact receiver, create socket, send message
			else {
				Socket tempSocket = new Socket(loggedInUsersWithIP.get(user), loggedInUsersWithPort.get(user));

				DataOutputStream out = new DataOutputStream(tempSocket.getOutputStream());
				out.writeUTF("");
				out.flush();
				out.writeUTF(username + ": " + msg);
				out.flush();
				out.close();
				tempSocket.close();

			}
			
		}
		
		// handle online command: to list logged in users
		public void online() throws IOException{
			
			// to every logged in users
			for (String user : loggedInUsersWithIP.keySet()) {
				// exclude the message sender
				String str = "";
				if (!user.equals(username)){
					str += (user + "\r\n");
				}
				output.writeUTF(str);
				output.flush();
			}				
		}
		
		// handle block command: to block a user
		public void block(String user) throws IOException {
			// if receiver is not a valid username, alert the user
			if (!allUsers.contains(user)) {
				output.writeUTF("There is no user: " + user);
				output.flush();
			}
			// add user to block list
			else {
				if (blockedBy.containsKey(user)){
					List<String> list = new ArrayList<String>(blockedBy.get(user));
					list.add(username);
					blockedBy.put(user, list);
				} else {
					List<String> list = new ArrayList<String>();
					list.add(username);
					blockedBy.put(user, list);
				}
				output.writeUTF("User " + user + " has been blocked");
				output.flush();
			}
		}
		
		// handle unblock command: to unblock a user
		public void unblock(String user) throws IOException {
			// if receiver is not a valid username, alert the user
			if (!allUsers.contains(user)) {
				output.writeUTF("There is no user: " + user);
				output.flush();
			}
			// remove user from block list
			else {
				if (blockedBy.containsKey(user)){
					List<String> list = new ArrayList<String>(blockedBy.get(user));
					if (list.contains(username)){
						list.remove(username);
					}
					blockedBy.put(user, list);
				} 
				output.writeUTF("User " + user + " has been unblocked");
				output.flush();
			}
			
		}		
		
		
		// handle getaddress command: to get the address of a certain user
		public void getAddress(String user) throws IOException{
			// if receiver is not a valid username, alert the user
			if (!allUsers.contains(user)) {
				output.writeUTF("There is no user: " + user);
				output.flush();
				return;
			} 
			// if current user is blocked by this user, can't get address
			if (blockedBy.containsKey(username)){
				if (blockedBy.get(username).contains(user)) { 
					output.writeUTF("You are blocked by " + user + ". Can't get his/her address.");
					output.flush();
					return;
				} 
			}
			
			// if this user is not logged in, can't get address
			if (!loggedInUsersWithIP.containsKey(user)){
				output.writeUTF(user + " is not logged in. Can't get his/her address.");
				output.flush();
			}
			else {
				
				output.writeUTF("#" + user + " " + loggedInUsersWithIP.get(user) + " " + loggedInUsersWithPort.get(user));
				output.flush();
			}
			
			
		}
		
		// handle private command: to establish private chat with another user
		public void getConsent(String user) throws IOException{
			
			// notify user B and get his consent
			boolean accept = false;
			Socket tempSocket = null;
			DataOutputStream out;
			DataInputStream in;

			tempSocket = new Socket(loggedInUsersWithIP.get(user), loggedInUsersWithPort.get(user));
			connectedUsers.put(user, tempSocket);
			out = new DataOutputStream(tempSocket.getOutputStream());
			in = new DataInputStream(tempSocket.getInputStream());
			
			out.writeUTF("#server");
			out.flush();
			out.writeUTF(username + "wants to privately chat with you.");
			out.flush();
			
			boolean redo = true;
			do{
				out.writeUTF("Do you accept the request? (Y/N)");
				out.flush();
				String ans = in.readUTF();
				if (ans.equals("Y") || ans.equals("y")){
					accept = true;
					redo = false;
				}
				else if (ans.equals("N") || ans.equals("n")){
					accept = false;
					redo = false;
				}
			} while (redo);
			
			// if user B accept A's private chat request, give B's ip and port to A
			// else, notify A.
			if (accept){
				output.writeUTF("$Yes "+user);
				output.flush();
			} else {
				output.writeUTF("$No "+user);
				output.flush();
			}
		}	
		
			
	
		
		// login process: check if a user has a valid username and password
		// return the logged in username
		public String login() {
			boolean login = false;
			String name = "";
			
			try{

				while (!login) {
					
					String identity = getIdentity();
				
					// if user's information is consistent with given
					// credentials, change login to true
					if (userCredentials.contains(identity)) {
						login = true;
					}
					String[] id = identity.split(" ");
					name = id[0];
					
					// if the user is still blocked, ask user to try again,
					// change login to false and restart the inner loop
					// if the user is not blocked and login is true, then break
					// the inner loop
					if (checkBlock(name)) {
						output.writeUTF("Due to multiple login failures, your account has been blocked."
								+ "\r\nPlease try again after sometime.");
						output.flush();
						login = false;
						continue;
					}
					
					// if not blocked, update login time of this username
					if (userLoginTimes.containsKey(name)){
						//(login times + 1) % 3, keep login time always < 3
						userLoginTimes.put(name, (userLoginTimes.get(name) + 1) % 3);
					} else{
						userLoginTimes.put(name, 0);
					}
					int times = userLoginTimes.get(name);

					// login successful!
					if (login) {
						// if user is logged in from another place, logout the previous one
						if (loggedInUsersWithIP.containsKey(name)){
							// if the user is connected now, send logout info
							if (connectedUsers.containsKey(name)) {
								DataOutputStream out = new DataOutputStream(connectedUsers.get(name).getOutputStream());
								out.writeUTF("logout");
								out.flush();
								connectedUsers.remove(name);
							}
							// else: user is logged in, but not connected now
							// contact user, create socket, send logout info
							else {
								Socket newSocket = new Socket(loggedInUsersWithIP.get(name), loggedInUsersWithPort.get(name));
								DataOutputStream out = new DataOutputStream(newSocket.getOutputStream());
								out.writeUTF("logout");
								out.flush();
								newSocket.close();
							}
							loggedInUsersWithIP.remove(name);
							loggedInUsersWithPort.remove(name);
							
						}
						
						output.writeUTF("Welcome to simple chat server!");
						output.flush();
						// show the available commands to user
						showCommands();
						// if there are offline messages, pass to user
						
						if (offlineMsg.containsKey(name)){
							output.writeUTF("Here is your offline messages:");
							output.flush();
							List<String> list = new ArrayList<String>(offlineMsg.get(name));
							for (String msg : list){
								output.writeUTF(msg);
								output.flush();
							}
							output.writeUTF("================================");
							output.flush();
							offlineMsg.remove(name);
							
						}
						
						
						// pass the successfully logged in name back to user
						output.writeUTF("*" + name);
						output.flush();
						
						System.out.println(name + " logged in");
						
						// remove this user from login times list
						userLoginTimes.remove(name);
						
						// get the user's server socket info
						String str;
						try {
							str = input.readUTF();
							if (str.charAt(0) == '#') {
								clientServerSocketIP = socket.getRemoteSocketAddress().toString();
								clientServerSocketIP = clientServerSocketIP.replaceAll("\\:([0-9]+)", "");
								clientServerSocketIP = clientServerSocketIP.substring(1);
								clientServerSocketPort = Integer.parseInt(str.substring(1));
							}

						} catch (IOException e) {
							//e.printStackTrace();
						}
						loggedInUsersWithIP.put(name, clientServerSocketIP);
						loggedInUsersWithPort.put(name, clientServerSocketPort);
						username = name;
					}

					// if login failed, ask user to login again
					if (!login) {
						if (times < 2){
							output.writeUTF("Invalid Password. Please try again");
							output.flush();
						} else {
							output.writeUTF("Invalid Password. Your account has been blocked. "
									+ "Please try again after sometime.");
							output.flush();
							// add user name and block start time to lists
							String[] iden = identity.split(" ");
							blockedUsers.put(iden[0], System.currentTimeMillis());
						}
					}

				}

			} catch(IOException e){
				//e.printStackTrace();
			}
			
			return name;
		}

		// check if the current user is blocked for login in
		private boolean checkBlock(String name) {
			boolean blocked = false;

			// if the name is in block list
			if (blockedUsers.containsKey(name)) {

				// if block time has passed, remove name and block start time
				// from lists
				if ((System.currentTimeMillis() - blockedUsers.get(name)) >= BLOCK_TIME) {
					blockedUsers.remove(name);
					blocked = false;
				} else {
					blocked = true;
				}
			}

			return blocked;
		}

		// promp to the user to get username and password input
		public String getIdentity() {
			String identity = "";

			try {
				// get name from client
				output.writeUTF("Username: ");
				output.flush();
				String name = input.readUTF();

				// get possword from client
				output.writeUTF("Password: ");
				output.flush();
				String password = input.readUTF();

				identity = name + " " + password;
			} catch (IOException e) {
				//e.printStackTrace();
			}
			return identity;
		}

	

		public void showCommands() {
			try {
				output.writeUTF("=====Available Commands=====\r\n"
						+ "message <user> <message>\r\n"
						+ "broadcast <message>\r\n" + "online\r\n"
						+ "block <user>\r\n" + "unblock <user>\r\n"
						+ "logout\r\n" + "getaddress <user>\r\n"
						+ "private <user> <message>\r\n"
						+ "===========================");
				output.flush();
			} catch (IOException e) {
				//e.printStackTrace();

			}

		}

	}	

	public static void main(String args[]) throws IOException {
		new Server(Integer.parseInt(args[0]));
	}
}
