import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MazewarServer {
  private static CopyOnWriteArrayList<String> clients = new CopyOnWriteArrayList<String>();
  private static CopyOnWriteArrayList<Socket> sockets = new CopyOnWriteArrayList<Socket>();
  private static CopyOnWriteArrayList<ObjectOutputStream> outputStreams = new CopyOnWriteArrayList<ObjectOutputStream>();
  private static BlockingQueue<MazewarPacket> packetQueue = new LinkedBlockingQueue<MazewarPacket>();
  private static AtomicInteger seqNo = new AtomicInteger();
  
  private static synchronized void removeFromList (String client) {
      assert(sockets.size() == clients.size() && clients.size() == outputStreams.size());
      for (int i = 0; i < clients.size(); i++) {
        if (clients.get(i).equals(client)) {
          clients.remove(i);
          sockets.remove(i);
          outputStreams.remove(i);
          break;
        }
      }
    }
  
  private static class MazewarServerHandlerThread implements Runnable {
    private Socket socket = null;
    private ObjectInputStream fromClient = null;
    private static ObjectOutputStream toClient = null;
    private String client = null;
    
    private MazewarServerHandlerThread (Socket socket) throws IOException {
      System.out.println("Created new Thread to handle client");
      this.socket = socket;
      fromClient = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run () {
      System.out.println("running");
      try {
        System.out.println("trying");
        synchronized (this) {
          MazewarPacket packetFromClient = (MazewarPacket) fromClient.readObject();
          toClient = new ObjectOutputStream(socket.getOutputStream());
          client = packetFromClient.name;
          outputStreams.add(toClient);
          clients.add(this.client);
          sockets.add(this.socket);
  
          MazewarPacket packetToClient = new MazewarPacket();
          for (int i = 0; i < clients.size(); i++) {
            System.out.println(clients.get(i));
            packetToClient.clients.add(clients.get(i));
          }

          packetToClient.seqNo = seqNo.getAndIncrement();
          packetToClient.type = MazewarPacket.CLIENTS;
          packetQueue.add(packetToClient);
        }
        
        while (socket.isClosed() == false) {
          MazewarPacket packetFromClient = (MazewarPacket) fromClient.readObject();
          packetQueue.add(packetFromClient);
          if (packetFromClient.type == MazewarPacket.DISCONNECT) {
            break;
          }
        }
        removeFromList(this.client);
        return;
      } catch (IOException e) {
        e.printStackTrace();
        System.exit(-1);
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
        System.exit(-1);
      }
    }
    
    
    
  }
  
  private static class broadcast implements Runnable {
    @Override
    public synchronized void run () {
      try {
        while (true) {
          if (!packetQueue.isEmpty()) {
            System.out.println("Taking a packet from the queue.");
            MazewarPacket packetToClient = packetQueue.take();
            packetToClient.seqNo = seqNo.getAndIncrement();
            assert(outputStreams.size() == sockets.size());
            for (int i = 0; i < outputStreams.size(); i++) {
              outputStreams.get(i).writeObject(packetToClient);
//              toClient = new ObjectOutputStream(sockets.get(i).getOutputStream());
//              toClient.writeObject(packetToClient);
            }
            if(packetToClient.type==MazewarPacket.DISCONNECT)
            {
              removeFromList(packetToClient.name);
            }
          }
        }
      } catch (Exception e) {
        System.err.println("ERROR: encountered during packet broadcasting. Exiting.");
        e.printStackTrace();
        System.exit(1);
      }
    }
  }
  private static class missileTick implements Runnable {
      @Override
      public synchronized void run () {
        try {
          while (true) {
            System.out.println("Ticking...");
            MazewarPacket packetMissile = new MazewarPacket();
            packetMissile.type = MazewarPacket.MISSILE;
            packetQueue.add(packetMissile);
            Thread.sleep(200);
          }
        } catch (Exception e) {
          System.err.println("ERROR: encountered during missileTick. Exiting.");
          e.printStackTrace();
          System.exit(1);
        }
      }
    }
  
  public static void main (String [] args) throws IOException, ClassNotFoundException {
    ServerSocket serverSocket = null;
//    boolean listening = true;
    
    try {
      if(args.length == 1) {
        serverSocket = new ServerSocket(Integer.parseInt(args[0]));
      } else {
        System.err.println("ERROR: Invalid arguments!");
        System.exit(-1);
      }
    } catch (IOException e) {
        System.err.println("ERROR: Could not listen on port!");
        System.exit(-1);
    }
    
    (new Thread(new broadcast())).start();
    (new Thread(new missileTick())).start();

    while (serverSocket.isBound() == true) {
      (new Thread(new MazewarServerHandlerThread(serverSocket.accept()))).start();
    }

    serverSocket.close();
    
  }

}
