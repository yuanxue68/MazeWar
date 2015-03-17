import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MazewarServer {
  private static AtomicInteger nextClientID = new AtomicInteger();
  private static CopyOnWriteArrayList<ClientDataStructure> clients = new CopyOnWriteArrayList<ClientDataStructure>();
  private static AtomicInteger quitAckCount = new AtomicInteger();
  
  private static class MazewarServerHandlerThread implements Runnable {
    private Socket socket = null;
    private ObjectInputStream fromClient = null;
    
    private MazewarServerHandlerThread (Socket socket) throws IOException {
      this.socket = socket;
      fromClient = new ObjectInputStream(socket.getInputStream());
    }
    
    @Override
    public void run () {
      try {
        synchronized (this) {
          MazewarPacket packetFromClient;
          while (true) {
            packetFromClient = (MazewarPacket) fromClient.readObject();
            
            if (packetFromClient.type == MazewarPacket.QUIT_ACK) {
              quitAckCount.incrementAndGet();
            } else if (packetFromClient.type == MazewarPacket.ENTER_NAMESERVICE) {
              ClientDataStructure clientData = new ClientDataStructure();
              clientData.client = packetFromClient.name;
              clientData.clientID = nextClientID.getAndIncrement();
              clientData.hostname = packetFromClient.hostname;
              clientData.port = packetFromClient.port;
              clientData.socket = this.socket;
              clientData.outputStream = new ObjectOutputStream(this.socket.getOutputStream());
              
              
              
              if (clients.size() == 0) {
                MazewarPacket tokenPacket = new MazewarPacket();
                tokenPacket.type = MazewarPacket.TOKEN;
                tokenPacket.seqNo = 0;
                clientData.outputStream.writeObject(tokenPacket);
                
                //make first client missile coordinator
                MazewarPacket packetMissile = new MazewarPacket();
                packetMissile.type = MazewarPacket.MISSILE;
                clientData.outputStream.writeObject(packetMissile);
              } else {
                MazewarPacket initializePacket = new MazewarPacket();
                initializePacket.type = MazewarPacket.CONNECT;
                initializePacket.clientID = clientData.clientID;
                
                for (int i = 0; i < clients.size(); i++) {
                  ClientDataStructure c = new ClientDataStructure();
                  c.client = clients.get(i).client;
                  c.clientID = clients.get(i).clientID;
                  c.hostname = clients.get(i).hostname;
                  c.port = clients.get(i).port;
                  initializePacket.clients.add(c);
                }
                
                
                clientData.outputStream.writeObject(initializePacket);
                
                MazewarPacket connectInfoPacket = new MazewarPacket();
                connectInfoPacket.type = MazewarPacket.ENTER_NAMESERVICE;
                connectInfoPacket.clients = new CopyOnWriteArrayList<ClientDataStructure>();
                
                ClientDataStructure tmp = new ClientDataStructure();
                tmp.client = packetFromClient.name;
                tmp.clientID = initializePacket.clientID;
                tmp.hostname = packetFromClient.hostname;
                tmp.port = packetFromClient.port;
                connectInfoPacket.clients.add(tmp);
                
                
                for (int i = 0; i < clients.size(); i++) {
                  clients.get(i).outputStream.writeObject(connectInfoPacket);
                }
              }
              clients.add(clientData);
            } else if (packetFromClient.type == MazewarPacket.QUIT_NAMESERVICE) {
              
              AtomicInteger token = new AtomicInteger(packetFromClient.seqNo);
              
              MazewarPacket quitPacket = new MazewarPacket();
              quitPacket.type = MazewarPacket.QUIT_NAMESERVICE;
              quitPacket.name = packetFromClient.name;
              quitPacket.hostname = packetFromClient.hostname;
              
              /* inform other clients about which client quit the name service*/
              for (int i = 0; i < clients.size(); i++) {
                if (!clients.get(i).client.equals(packetFromClient.name)){
                  clients.get(i).outputStream.writeObject(quitPacket);
                }
              }
              
              quitAckCount.set(0);
              // waiting for all the other clients acknowledge the quit
              while(quitAckCount.get() != clients.size() - 1);
              
              /*ack the quitting client*/
              /*remove the client that initiated the quit packet*/
              for (int i = 0; i < clients.size(); i++) {
                if (clients.get(i).client.equals(packetFromClient.name)) {
                  MazewarPacket reply = new MazewarPacket();
                  reply.type = MazewarPacket.QUIT_ACK;
                  clients.get(i).outputStream.writeObject(reply);
                  
                  clients.get(i).outputStream.close();
                  clients.get(i).socket.close();
                  clients.remove(i);
                  break;
                }
              }
              
              if (clients.size() >= 1) {
                MazewarPacket tokenPacket = new MazewarPacket();
                tokenPacket.type = MazewarPacket.TOKEN;
                tokenPacket.seqNo = token.get();
                clients.get(0).outputStream.writeObject(tokenPacket);
              }
              
              MazewarPacket packetMissile = new MazewarPacket();
              packetMissile.type = MazewarPacket.MISSILE;
              if(clients.size() > 0) {
                clients.get(0).outputStream.writeObject(packetMissile);
              }

              return;
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(-1);
      }
    }
  }
  
  public static void main (String [] args) throws IOException, ClassNotFoundException {
    ServerSocket serverSocket = null;
//    boolean listening = true;
    
    try {
      if (args.length == 1) {
        serverSocket = new ServerSocket(Integer.parseInt(args[0]));
      } else {
        System.err.println("ERROR: Invalid arguments!");
        System.exit(-1);
      }
    } catch (IOException e) {
        System.err.println("ERROR: Could not listen on port!");
        System.exit(-1);
    } 
    
    
    while (serverSocket.isBound() == true) {
      (new Thread(new MazewarServerHandlerThread(serverSocket.accept()))).start();
    }
    serverSocket.close();
    
  }

}
