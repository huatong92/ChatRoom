Name: Hua Tong
Uni: ht2334

My chatroom application is written in java. It includes two class: server and client. 


Server Class:
1. There is one thread continuously accept socket through this server socket.
2. For each accepted socket, create a serverThread to process command. The server times out these socket and thus stops these serverThread every 30 seconds.
3. There is a heartbeat thread that checks heartbeat evert 1 minutes.

Client Class:
1. Create a client socket to connect to server
2. Associate this socket with a ServerIn thread to read from server and write to client.
The socket close when server close the corresponding socket breaks when server sends timeout information to user. 
3. KeyboardOut thread to read from keyboard and send out. The keyboardOut thread is always running
4. When creating the client, also create a serverSocket for server and other clients to connect to. A serverSocketThread is used to accept socket from this serverSocket.
5. There is also a heartbeat thread that sends “LIVE” information to server. 

Ideas about some features:
1. I used setSoTimeout and TimeoutException to control the closing of socket to maintain a non-persistant connection.
2. Every time server accepts a user, they exchange information on each side to see if this user is logged in.
3. I used nested class structure, and placed all threads of server under server class, and all threads of client under client class. This is because this threads are only used by server/client side, so it is fair to use such structure. It is also to maintain consistency of some information.
4. If a client is not connected to server but logged in, whenever it writes something the connection will be re-established. In the same way, the server can connect a client even if it is not connected but logged in.

Features I implemented:
user authentication
message exchange
multiple clients support
heartbeat
blacklist
offline messaging
broadcast
display current users
logout
graceful exit using control+c
obtain online user’s IP
offline report
p2p message exchange 
p2p privacy and consent

=====Available Commands=====
message <user> <message>
broadcast <message>
online
block <user>
unblock <user>
logout
getaddress <user>
private <user> <message>
===========================

How to run:
In the package ChatRoom, there are two packages Server and Client. Just type:
java -cp . Server.Server 2200            to run the Server
java -cp . Client.Client 0.0.0.0 2200    to run the Client

If you want to re-complier, which you don’t have to do,
just go to each package and type:
javac Server.java
javac Client.java

Available commands are listed above.

Sample outputs:
******from user foobar**************
Username: 
foobar
Password: 
passpass
Welcome to simple chat server!
=====Available Commands=====
message <user> <message>
broadcast <message>
online
block <user>
unblock <user>
logout
getaddress <user>
private <user> <message>
===========================
message columbia hello columbia
broadcast hi
block columbia
User columbia has been blocked
unblock columbia
User columbia has been unblocked
columbiawants to privately chat with you.
Do you accept the request? (Y/N)
y
foobar: hi 
private columbia how are you
foobar: I am good! 
logout
***************************************

******from user columbia**************
Username: 
columbia
Password: 
116bway
Welcome to simple chat server!
=====Available Commands=====
message <user> <message>
broadcast <message>
online
block <user>
unblock <user>
logout
getaddress <user>
private <user> <message>
===========================
foobar: hello columbia 
foobar: hi 
message foobar hi
You are blocked by foobar. You can't send him/her message
getaddress foobar
Address is stored
private foobar hi
Yes accepted private chat. Please enter 'chatnow' to resend your message
chatnow
columbia: how are you 
private foobar I am good!
online
foobar
***************************************

************from server**************
Simple Chat Server
columbia logged in
foobar logged in
columbia has time out, disconnected
foobar has time out, disconnected
columbia has time out, disconnected
columbia has time out, disconnected
***************************************

I used my 3 late days, implemented all features and an extra one: p2p consent 

