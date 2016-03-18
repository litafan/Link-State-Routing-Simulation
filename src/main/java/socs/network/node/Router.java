package socs.network.node;

import socs.network.util.Configuration;
import java.net.*;
import java.io.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import socs.network.message.SOSPFPacket;
import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import java.util.Vector;
import java.util.ListIterator;
import java.util.LinkedList;
import java.util.TimerTask;
import java.util.Timer;
import java.util.Date;

/*
1) when the new neighbors are detected, you have to add the LSA describing the neighbors to the LinkStateDatabase [DONE]

2) when a new LSA is added to LinkStateDatabase, you have to broadcast it to the neighbors (not include the one sent you the LSA) for synchronization [DONE]

Based on 1) and 2) you will get consistent database across the network , after that 

3) you have to implement the handler of "detect" command which output the shortest path from the router where you run the command to the destination IP address [DONE]

NOTE: The evaluation does not include the test regarding the sudden topology change, but only test if the database is consistent over the network 
*/


public class Router {
        protected LinkStateDatabase lsd;
        RouterDescription rd = new RouterDescription();

        //assuming that all routers are with 4 ports
        Link[] ports = new Link[4];

        public Router(Configuration config) {
            rd.simulatedIPAddress = config.getString("socs.network.router.ip"); //Load the simulated IP address from the config
            rd.processIPAddress=config.getString("socs.network.router.processIPAddress"); //Load the process IP address from the config
            rd.processPortNumber=config.getShort("socs.network.router.processPortNumber"); //Load the port number from the config
            lsd = new LinkStateDatabase(rd); //Initialize the database
            System.out.println("My simulated IP address is: " + rd.simulatedIPAddress); //Print preliminary information about the router's actual and simulated address
            startRouterServer(); //Start the server socket
            
            
            Timer time = new Timer();
            ScheduledTask st = new ScheduledTask();
            time.schedule(st, 0, 5000);
        }
        
        public int removeFromLSD(String toRemove) {
			//Returns 1 if it successfully removes the specified router from our LSA
			//Returns -1 if the specified router is not in our list of neighbors
			LSA ownLSA = lsd._store.get(rd.simulatedIPAddress);
			for (LinkDescription l : ownLSA.links) {
				if (l.linkID.equals(toRemove)) {
					ownLSA.links.remove(l);
					ownLSA.lsaSeqNumber++;
					return 1;
				}
			}
			return -1;
		}
		
		public int removeFromNeighbor(String neighbor) {
			//Returns 1 if it successfully removes ourselves from the neighbor's LSA
			//Returns -1 if we're not in the neighbor's LSA
			LSA neighborLSA = lsd._store.get(neighbor);
			for (LinkDescription l : neighborLSA.links) {
				if (l.linkID.equals(rd.simulatedIPAddress)) {
					neighborLSA.links.remove(l);
					neighborLSA.lsaSeqNumber++;
					return 1;
				}
			}
			return -1;
		}

		public int getNeighborPort(String neighbor) { //Returns the port the neighbor is on if the neighbors exists, otherwise returns -1
			int toRet = -1;
			for (int i = 0; i < ports.length; i++) {
				if (ports[i] != null) {
					if (ports[i].router2.simulatedIPAddress.equals(neighbor)) {
						toRet = i;
						break;
					}
				}
			}
			return toRet;
		}

        public void startRouterServer() { //Start the server socket
            int port=rd.processPortNumber;
            try {
                new Thread(new MTSS(port, this)).start(); //Start a server socket on the specified port
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        
        public int removeNeighbor(String simulatedIPAddress) {
			//Returns 1 if it successfully finds the neighbor and removes it from the ports array
			//Returns -1 if it doesn't find the neighbor in the ports array
			short i = 0;
			for (i = 0; i < ports.length; i++) {
				if (ports[i] != null) {
					if (ports[i].router2.simulatedIPAddress.equals(simulatedIPAddress)) {
						ports[i] = null;
						return 1;
					}
				}
			}
			return -1;
		}

        public boolean isNeighbor(String simulatedIPAddress) { //Returns true if the simulated IP address is a neighbor of the router
            short i = 0;
            for (i = 0; i < ports.length; i++) {
                if (ports[i] != null) {
                    if (ports[i].router2.simulatedIPAddress.equals(simulatedIPAddress)) return true;
                }
            }
            return false;
        }

        public void setStatus(String simulatedIPAddress, RouterStatus status) { //Sets the status of specified neighbor to the given status
            short i = 0;
            for (i = 0; i< ports.length; i++) {
                if (ports[i] != null) {
                    if (ports[i].router2.simulatedIPAddress.equals(simulatedIPAddress)) {
                        ports[i].router2.status = status;
                        break;
                    }
                }
            }
        }

        public RouterStatus getStatus(String simulatedIPAddress) { //Returns the status of the specified neighbor
            short i = 0;
            for (i = 0; i< ports.length; i++) {
                if (ports[i] != null) {
                    if (ports[i].router2.simulatedIPAddress.equals(simulatedIPAddress)) {
                        return ports[i].router2.status;
                    }
                }
            }
            return null;
        }
        public void addNeighbor(String processIP, short processPort, String simulatedIP) { //Adds the specified router to the list of neighbors
            short i = 0;
            for (i = 0; i < ports.length; i++) {
                if (ports[i] == null) {
                    RouterDescription remoteRouterDescription=new RouterDescription(); //Create a new router description for the destination
                    remoteRouterDescription.simulatedIPAddress=simulatedIP;
                    remoteRouterDescription.processIPAddress=processIP;
                    remoteRouterDescription.processPortNumber=processPort;
                    remoteRouterDescription.status=RouterStatus.INIT; //Initialize the values for the router description
                    ports[i]=new Link(this.rd,remoteRouterDescription); //Add the new link to the ports array
                    break;
                }
            }
        }

        /**
         * output the shortest path to the given destination ip
         * <p/>
         * format: source ip address  -> ip address -> ... -> destination ip
         *
         * @param destinationIP the ip adderss of the destination simulated router
         */
        private void processDetect(String destinationIP) {
			System.out.println(lsd.getShortestPath(destinationIP));
        }

        /**
         * disconnect with the router identified by the given destination ip address
         * Notice: this command should trigger the synchronization of database
         *
         * @param portNumber the port number which the link attaches at
         */
        private void processDisconnect(short portNumber) {
			if (ports[portNumber] != null) {
				if (ports[portNumber].router2.status != null) {
					String serverName=ports[portNumber].router2.processIPAddress;
                    int port=ports[portNumber].router2.processPortNumber;
					SOSPFPacket packet=new SOSPFPacket();
					packet.sospfType=2;
					packet.neighborID = rd.simulatedIPAddress;
					packet.srcProcessIP = rd.processIPAddress;
					packet.srcProcessPort = rd.processPortNumber;
					new Thread(new Client(serverName, port, packet)).start();
					int result = removeFromLSD(ports[portNumber].router2.simulatedIPAddress);
					if (result == 1) {
						
					} else if (result == -1) {
						System.out.println("The specified router was not found to be a neighbor");
					} else {
						System.out.println("This should never print");
					}
					LSAUpdate();
					ports[portNumber] = null;					
				}
			}
        }

        /**
         * attach the link to the remote router, which is identified by the given simulated ip;
         * to establish the connection via socket, you need to indentify the process IP and process Port;
         * additionally, weight is the cost to transmitting data through the link
         * <p/>
         * NOTE: this command should not trigger link database synchronization
         */
        private void processAttach(String processIP, short processPort, String simulatedIP, short weight) {
            short i = 0;
            for (i = 0; i < ports.length; i++) {
                if (ports[i] == null) {
                    RouterDescription remoteRouterDescription=new RouterDescription(); //Create a new router description for the destination
                    remoteRouterDescription.simulatedIPAddress=simulatedIP;
                    remoteRouterDescription.processIPAddress=processIP;
                    remoteRouterDescription.processPortNumber=processPort;
                    ports[i]=new Link(this.rd,remoteRouterDescription); //Add the new link to the ports array
                    //We now need to add the entry to the lsd
                    LSA newLSA = lsd._store.get(rd.simulatedIPAddress); //Get the current LSA for our router
                    newLSA.lsaSeqNumber++; //Increment the sequence number
                    LinkDescription newLink = new LinkDescription(); //Create a new link
                    newLink.linkID = simulatedIP;
                    newLink.portNum = i;
                    newLink.tosMetrics = weight; //Initialize the new link with the proper info
                    newLSA.links.add(newLink); //Add a new link with the specified information into our links
                    break;
                }
            }
            if (i == ports.length) System.out.println("Failed to attach neighbor. All of the ports on this router are occupied!");
        }

        /**
         * broadcast Hello to neighbors
         */
        private void processStart() {
            for(int i=0; i<ports.length; i++) {
                if (ports[i] != null) {
                    if (ports[i].router2.status == null) {
                        String serverName=ports[i].router2.processIPAddress;
                        int port=ports[i].router2.processPortNumber;
                        SOSPFPacket packet=new SOSPFPacket();
                        packet.sospfType=0;
                        packet.neighborID = rd.simulatedIPAddress;
                        packet.srcProcessIP = rd.processIPAddress;
                        packet.srcProcessPort = rd.processPortNumber;
                        new Thread(new Client(serverName, port, packet)).start();
                    }
                }
            }
            LSAUpdate(); //Create the link with our neighbors, then send an LSA update packet
        }

        /**
         * attach the link to the remote router, which is identified by the given simulated ip;
         * to establish the connection via socket, you need to indentify the process IP and process Port;
         * additionally, weight is the cost to transmitting data through the link
         * <p/>
         * This command does trigger the link database synchronization
         */
        private void processConnect(String processIP, short processPort, String simulatedIP, short weight) {
			//System.out.println("Executing the connect() subroutine");
			processAttach(processIP, processPort, simulatedIP, weight);
			processStart();
        }

        /**
         * output the neighbors of the routers
         */
        private void processNeighbors() {
            System.out.println("My neighbors are: ");
            for (short i = 0; i < ports.length; i++) {
                if (ports[i] != null) {
                    if (ports[i].router2.status != null) {
                        System.out.println(ports[i].router2.processIPAddress + ":" + ports[i].router2.processPortNumber + " (" + ports[i].router2.simulatedIPAddress + ") -- " + ports[i].router2.status);
                    }
                }
            }
        }

        /**
         * disconnect with all neighbors and quit the program
         */
        private void processQuit() {
            /*
            System.out.println("Disconnecting from all neighbors");
            for (short i = 0; i < ports.length; i++) {
                processDisconnect(i);
            }
            System.out.println("All neighbors disconnected from. Exiting");
            */
            System.out.println("Exiting");
            System.exit(0);
        }

        /**
         *
         *
         */
        private void LSAUpdate() {
            for(int i=0; i<ports.length; i++) {
				if (ports[i] != null) {
					String serverName=ports[i].router2.processIPAddress;
					int port=ports[i].router2.processPortNumber;
					SOSPFPacket packet=new SOSPFPacket();
					packet.sospfType=1; //Indicate that this is an LSAUpdate packet
					packet.srcIP = rd.simulatedIPAddress;
					packet.dstIP = ports[i].router2.simulatedIPAddress;
					Vector<LSA> lsaVector = new Vector<LSA>(); //Create a new LSA vector for the packet
					for (LSA lsa : lsd._store.values()) {
						lsaVector.add(lsa); //Add all the LSA entries in this router's LSD
					}
					packet.lsaArray = lsaVector;
					new Thread(new Client(serverName,port,packet)).start();
				}
            }

        }
        
        private void LSAUpdate(String neighbor) { //Broadcasts an LSA update to all neighbors except the one passed as input
            for(int i=0; i<ports.length; i++) {
				if (ports[i] != null && !(ports[i].router2.simulatedIPAddress.equals(neighbor))) {
					String serverName=ports[i].router2.processIPAddress;
					int port=ports[i].router2.processPortNumber;
					SOSPFPacket packet=new SOSPFPacket();
					packet.sospfType=1; //Indicate that this is an LSAUpdate packet
					packet.srcIP = rd.simulatedIPAddress;
					packet.dstIP = ports[i].router2.simulatedIPAddress;
					Vector<LSA> lsaVector = new Vector<LSA>(); //Create a new LSA vector for the packet
					for (LSA lsa : lsd._store.values()) {
						lsaVector.add(lsa); //Add all the LSA entries in this router's LSD
					}
					packet.lsaArray = lsaVector;
					new Thread(new Client(serverName,port,packet)).start();
					
				}
            }

        }


        public void terminal() {
            try {
                InputStreamReader isReader = new InputStreamReader(System.in);
                BufferedReader br = new BufferedReader(isReader);
                System.out.print(">> ");
                String command = br.readLine();
                while (true) {
                    if (command.startsWith("detect ")) {
                        String[] cmdLine = command.split(" ");
                        processDetect(cmdLine[1]);
                    } else if (command.startsWith("disconnect ")) {
                        String[] cmdLine = command.split(" ");
                        processDisconnect(Short.parseShort(cmdLine[1]));
                    } else if (command.startsWith("quit")) {
                        isReader.close();
                        br.close();
                        processQuit();
                    } else if (command.startsWith("attach ")) {
                        String[] cmdLine = command.split(" ");
                        processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                                      cmdLine[3], Short.parseShort(cmdLine[4]));
                    } else if (command.equals("start")) {
                        processStart();
                    } else if (command.startsWith("connect ")) {
                        String[] cmdLine = command.split(" ");
                        processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                                       cmdLine[3], Short.parseShort(cmdLine[4]));
                    } else if (command.equals("neighbors")) {
                        //output neighbors
                        processNeighbors();
                    } else if (command.equals("print lsd")) {
                        System.out.print(lsd);
                    } else if (command.equals("lsa update")) {
						LSAUpdate();
                    } else if (command.equals("exit")) {
						isReader.close();
						br.close();
						processQuit();
					} else if (command.equals("ports")) {
						for (int i = 0; i < ports.length; i++) {
							if (ports[i] != null) System.out.println("Port[" + i + "]: " + ports[i].router2.simulatedIPAddress);
						}
                    } else {
                        //invalid command
                        //break; //Don't want the program to stop if I mis-type a command...
                    }
                    System.out.print(">> ");
                    command = br.readLine();
                }
                //isReader.close();
                //br.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        class MTSS implements Runnable {
                private ServerSocket serverSocket;
                Router router;
                public MTSS(int port, Router r) throws IOException{
                    serverSocket =new ServerSocket(port);
                    router = r;
                    System.out.println("Opened a server socket with IP: " + serverSocket.getInetAddress() + ":" + serverSocket.getLocalPort());
                }

                public void run() {
                    while(true) {
                        try {
                            Socket server=serverSocket.accept(); //Wait for a client to connect
                            new Thread(new ClientServiceThread(server, router)).start(); //Defer the handling of the connected client to another thread
                        } catch (SocketTimeoutException s) {
                            System.out.println("Socket timed out");
                            break;
                        } catch (Exception e) {
                            e.printStackTrace();
                            break;
                        }
                    }

                }
        }
        
        class ScheduledTask extends TimerTask {			
			public void run() {
				for (int i = 0; i < ports.length; i++) {
					if (ports[i] != null) {
						if (ports[i].router2.status != null) {
							String simIP = ports[i].router2.simulatedIPAddress;
							String serverName=ports[i].router2.processIPAddress;
							int port=ports[i].router2.processPortNumber;
							Socket client = new Socket();
							try {
								client.connect(new InetSocketAddress(serverName, port), 10000);
								//System.out.println("We still see: " + simIP);
							} catch (IOException e) {
								System.out.println("We no longer see: " + simIP);
								int removePorts = removeNeighbor(simIP);
								if (removePorts == 1) {
									//System.out.println("We've successfully removed the neighbor from our ports array");
								} else {
									System.out.println("There was an error removing the neighbor from the ports array");
								}
								int removeLSD = removeFromLSD(simIP);
								if (removeLSD == 1) {
									//System.out.println("We've successfully removed the associated link from our LSA");
								} else {
									System.out.println("There was an error removing the associated link from our LSA");
								}
								int removeLSD2 = removeFromNeighbor(simIP);
								if (removeLSD2 == 1) {
									//System.out.println("We've successfully removed the associated link from neighbor's LSA");
								} else {
									System.out.println("There was an error removing the associated link from neighbor's LSA");
								}
								LSAUpdate();
							}
						}
					}
				}
			}			
		}

        class ClientServiceThread implements Runnable {
                Socket server;
                Router router;
                ClientServiceThread(Socket s, Router r) {
                    server=s;
                    router=r;
                }
                public void run() {
					ObjectInputStream in = null;
					ObjectOutputStream out = null;
                    try {
                        //We've receeived a client and now we want to see what the client has to say
                        in = new ObjectInputStream(server.getInputStream());
                        //This is where we will do processing on the packet
                        SOSPFPacket message = (SOSPFPacket) in.readObject(); //Receive an initial message
                        if (message.sospfType == 0) { //It was a HELLO message
                            System.out.println("Received HELLO from " + message.neighborID);
                            if (!router.isNeighbor(message.neighborID)) { //If we've already got this client as a neighbor, then don't bother messing with its state
                                router.addNeighbor(message.srcProcessIP, message.srcProcessPort, message.neighborID);
                                System.out.println("Set " + message.neighborID + " state to INIT");
                            }
                            out=new ObjectOutputStream(server.getOutputStream()); //Prepare to send a response packet
                            SOSPFPacket response = new SOSPFPacket();
                            response.sospfType = 0; //Indicate this is a HELLO message
                            response.neighborID = router.rd.simulatedIPAddress;
                            response.srcProcessIP = router.rd.processIPAddress;
                            response.srcProcessPort = router.rd.processPortNumber; //Initialize the correct return address
                            out.writeObject(response); //Write out our response
                            message = (SOSPFPacket) in.readObject(); //Receive another message
                            if (message.sospfType == 0) { //If it's another HELLO message
                                System.out.println("Received HELLO from " + message.neighborID);
                                if (router.getStatus(message.neighborID) == RouterStatus.INIT) { //If we haven't set our client's status to TWO_WAY, then set it to TWO_WAY
                                    router.setStatus(message.neighborID, RouterStatus.TWO_WAY);
                                    System.out.println("Set " + message.neighborID + " state to TWO_WAY");
                                }
                                //Receive an LSA update packet from the sender to update my LSD
                                message = (SOSPFPacket) in.readObject(); //Receive another message
                                if (message.sospfType == 1) {//If it's a LSA update message to get the db's in sync
									LSA newLSA = router.lsd._store.get(router.rd.simulatedIPAddress); //Get our current LSA
									newLSA.lsaSeqNumber++; //Increment the sequence number because it's being changed
									LinkDescription newLink = new LinkDescription(); //Create a new link
									newLink.linkID = message.neighborID; //Fill in the IP of our new neighbor
									newLink.portNum = router.getNeighborPort(message.neighborID); //Fill in the details of the port the neighbor is on
									int weight = -1; //Instantiate weight to be a negative number
									LSA lsa = message.lsaArray.elementAt(0); //Get the lsa that the neighbor sent us
									for (LinkDescription l : lsa.links) { //Go through the links
										if (l.linkID.equals(router.rd.simulatedIPAddress)) { //Find the link corresponding to us
											weight = l.tosMetrics; //Set the weight to be what our neighbor thinks the weight is (maintains symmetry)
											break;
										}
									}
									newLink.tosMetrics = weight; //Initialize the new link with the proper info
									newLSA.links.add(newLink); //Add a new link with the specified information into our links
									router.lsd._store.put(router.rd.simulatedIPAddress, newLSA); //Update our LSD
									router.LSAUpdate(); //Our LSD has changed, send an LSA update to all of our neighbors
								}								
                                System.out.print(">> ");
                            }
                        } else if (message.sospfType == 1) { //It was an LSA update
							//System.out.println("We've received an LSA update!");
							Vector<LSA> lsaArray = message.lsaArray;
							for (LSA lsa : lsaArray) { //Go through every LSA in the vector given to us
								if (router.lsd._store.containsKey(lsa.linkStateID)) { //If our current database has a corresponding LSA already
									//If this lsa is newer, then change them
									if (router.lsd._store.get(lsa.linkStateID).lsaSeqNumber < lsa.lsaSeqNumber) { //This lsa is newer than the one we have currently
										router.lsd._store.put(lsa.linkStateID, lsa);
										//We need to send this newer lsa to the rest of our neighbors
										router.LSAUpdate(message.srcIP);
									}
								} else { //If the current database doesn't have this LSA
									router.lsd._store.put(lsa.linkStateID, lsa);
									//We need to send this new LSA to the rest of our neighbors
									router.LSAUpdate(message.srcIP);
								}
							}						
						} else if (message.sospfType == 2) { //It was a message saying that our neighbor is disconnecting
							router.removeFromLSD(message.neighborID);
							LSAUpdate();
							router.removeNeighbor(message.neighborID);					
						}
						//in.close();
                        //server.close(); //Close the server
                    } catch (Exception e) {
                        //e.printStackTrace();
                    } finally {
						try {
							in.close();
							out.close();
							server.close();
						} catch (Exception e) {
							//System.out.println("There was a problem trying to close the input stream and socket");
						}
					}
                }
        }

        class Client implements Runnable {
                private String serverName;
                private int port;
                private SOSPFPacket packet;
                public Client(String serverName,int port,SOSPFPacket packet) {
                    this.serverName=serverName;
                    this.port=port;
                    this.packet=packet;
                }
                public void run() {
					OutputStream outToServer = null;
					ObjectOutputStream out = null;
					InputStream inFromServer = null;
					ObjectInputStream in = null;
					Socket client = null;
                    try {
						//Socket client=new Socket(serverName, port); //Create a new socket and connect to the specified server
                        if (packet.sospfType == 0) {	//If we're sending out a HELLO message
							client=new Socket(serverName, port); //Create a new socket and connect to the specified server
                            outToServer=client.getOutputStream();
                            out = new ObjectOutputStream(outToServer);
                            out.writeObject(packet); //Send the initial message to the server
                            inFromServer=client.getInputStream();
                            in = new ObjectInputStream(inFromServer); //Wait for a response from the server
                            SOSPFPacket response = (SOSPFPacket) in.readObject(); //Receive a response packet from the server
                            if (response.sospfType == 0) { //If it's a HELLO response
                                System.out.println("Received HELLO from " + response.neighborID);
                                if (getStatus(response.neighborID) == RouterStatus.INIT) { //If the server's status is INIT, then set it to TWO_WAY
                                    setStatus(response.neighborID, RouterStatus.TWO_WAY);
                                    System.out.println("Set " + response.neighborID + " state to TWO_WAY");
                                } else if (getStatus(response.neighborID) == null) { //If the server's status is null, then set it to TWO_WAY
                                    setStatus(response.neighborID, RouterStatus.TWO_WAY);
                                    System.out.println("Set " + response.neighborID + " state to TWO_WAY");
                                }
                                out.writeObject(packet); //Send a response HELLO packet to acknowledge we received the server's HELLO
                                //We've established a two way channel with our neighbor
                                SOSPFPacket packet2 = new SOSPFPacket(); //Create a new packet to update the neighbor's LSD
                                packet2.sospfType = 1; //Indicate that this is an LSA update packet
                                packet2.neighborID = rd.simulatedIPAddress; //Fill the sender IP field in the packet
                                Vector<LSA> lsaVector = new Vector<LSA>(); //Create a new LSA vector for the packet
								lsaVector.add(lsd._store.get(rd.simulatedIPAddress)); //Add the "base LSA" to the vector
								packet2.lsaArray = lsaVector; //Add the vector to the packet
								out.writeObject(packet2); //Send the packet to the neighbor								
                                System.out.print(">> ");
                            }
                        } else if (packet.sospfType == 1) {//If we're sending out an LSA update packet
							client=new Socket(serverName, port); //Create a new socket and connect to the specified server
                            outToServer=client.getOutputStream();
                            out = new ObjectOutputStream(outToServer);
                            out.writeObject(packet); //Send the LSA update packet to the server
                            //System.out.print(">> ");
						} else if (packet.sospfType == 2) {//If we're letting our neighbors know that we're leaving the network
							client=new Socket(serverName, port); //Create a new socket and connect to the specified server
							outToServer = client.getOutputStream();
							out = new ObjectOutputStream(outToServer);
							out.writeObject(packet);							
						}
                    } catch (Exception e) {
                        //e.printStackTrace();
                    } finally {
						try {
							out.close();
							in.close();
							client.close();
						} catch (Exception e) {
							//System.out.println("There was an error trying to clean up resources in the client");
						}
					}
                }
        }
}


