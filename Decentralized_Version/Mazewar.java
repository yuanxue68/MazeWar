/*
Copyright (C) 2004 Geoffrey Alan Washburn
   
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.
   
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
   
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
USA.
 */

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JOptionPane;

import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

import javax.swing.BorderFactory;

//import MazewarServer.MazewarServerHandlerThread;






import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

/**
 * The entry point and glue code for the game. It also contains some helpful
 * global utility methods.
 * 
 * @author Geoffrey Washburn &lt;<a
 *         href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: Mazewar.java 371 2004-02-10 21:55:32Z geoffw $
 */

public class Mazewar extends JFrame {

  /**
   * The default width of the {@link Maze}.
   */
  private final int mazeWidth = 20;

  /**
   * The default height of the {@link Maze}.
   */
  private final int mazeHeight = 10;

  /**
   * The default random seed for the {@link Maze}. All implementations of the
   * same protocol must use the same seed value, or your mazes will be
   * different.
   */
  private final int mazeSeed = 42;

  /**
   * The {@link Maze} that the game uses.
   */
  private static Maze maze = null;

  /**
   * The {@link GUIClient} for the game.
   */
  private GUIClient guiClient = null;
  
  /**
   * The {@link remoteClient} for the game.
   */
  private static RemoteClient remoteClient = null;

  /**
   * The panel that displays the {@link Maze}.
   */
  private OverheadMazePanel overheadPanel = null;

  /**
   * The table the displays the scores.
   */
  private JTable scoreTable = null;

  /**
   * Create the textpane statically so that we can write to it globally using
   * the static consolePrint methods
   */
  private static final JTextPane console = new JTextPane();
  
  private Socket socket = null;
  private static ObjectOutputStream toServer = null;
  private static ObjectInputStream fromServer = null;
  private MazewarPacket packetToServer = null, packetFromServer = null;
  private static String clientName = null;
  private static CopyOnWriteArrayList<MazewarPacket> packetList = new CopyOnWriteArrayList<MazewarPacket>();
  
  private static CopyOnWriteArrayList<ClientDataStructure> clients = new CopyOnWriteArrayList<ClientDataStructure>();
  private static ServerSocket serverSocket = null;
  private static int myClientID;
  private static AtomicInteger token = new AtomicInteger(-1);
  private static AtomicInteger quitAck = new AtomicInteger();
  private static CopyOnWriteArrayList<MazewarPacket> broadcastQueue = new CopyOnWriteArrayList<MazewarPacket>();
  private static boolean isMissileServer=false;
//  private static AtomicInteger waitingToDie = new AtomicInteger();
  
  
  /**
   * Write a message to the console followed by a newline.
   * 
   * @param msg
   *          The {@link String} to print.
   */
  public static synchronized void consolePrintLn(String msg) {
    console.setText(console.getText() + msg + "\n");
  }

  /**
   * Write a message to the console.
   * 
   * @param msg
   *          The {@link String} to print.
   */
  public static synchronized void consolePrint(String msg) {
    console.setText(console.getText() + msg);
  }

  /**
   * Clear the console.
   */
  public static synchronized void clearConsole() {
    console.setText("");
  }

  /**
   * Static method for performing cleanup before exiting the game.
   * @throws Exception 
   */
  public static void quit(String name, MazewarPacket quitPacket) throws Exception {
    // TODO: I have to notify the server here that I am quitting...
    MazewarPacket disconnectPacket = new MazewarPacket();
    disconnectPacket.type = MazewarPacket.QUIT_NAMESERVICE;
    disconnectPacket.name = name;
    
    for (int i = 0; i < clients.size(); i++) {
      if (!clients.get(i).client.equals(name)) {
        clients.get(i).outputStream.writeObject(disconnectPacket);
      }
    }
    
    for (int i = 0; i < clients.size(); i++) {
      if (!clients.get(i).client.equals(name)) {
        clients.get(i).outputStream.close();
        clients.get(i).socket.close();
      }
    }
    
    try {
      toServer.writeObject(quitPacket);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    while (quitAck.get() != 1);
    
    //toServer.close();
    //fromServer.close();
    
    assert(token.get() == -1);
    System.exit(0);
  }
  public static void quit() {
    System.out.println("I am quitting WITHOUT arguments");
    System.exit(0);
  }
  public static Maze getMaze()
  {
    return maze;
  }
  /**
   * The place where all the pieces are put together.
   */
  public Mazewar(String hostname, int port, int clientPort) {
    super("ECE419 Mazewar");
    consolePrintLn("ECE419 Mazewar started!");

    // Create the maze
    maze = new MazeImpl(new Point(mazeWidth, mazeHeight), mazeSeed);
    assert (maze != null);

    // Have the ScoreTableModel listen to the maze to find
    // out how to adjust scores.
    ScoreTableModel scoreModel = new ScoreTableModel();
    assert (scoreModel != null);
    maze.addMazeListener(scoreModel);

    // Throw up a dialog to get the GUIClient name.
    String name = JOptionPane.showInputDialog("Enter your name");
    if ((name == null) || (name.length() == 0)) {
      Mazewar.quit();
    }
    clientName = name;

    // You may want to put your network initialization code somewhere in
    // here.
    try {
      socket = new Socket(hostname, port);
      toServer = new ObjectOutputStream(socket.getOutputStream());
      
      packetToServer = new MazewarPacket();
      packetToServer.type = MazewarPacket.ENTER_NAMESERVICE;
      packetToServer.name = name;
      packetToServer.hostname = InetAddress.getLocalHost().getHostName();
      //packetToServer.hostname = "localhost";
      packetToServer.port = clientPort;

      toServer.writeObject(packetToServer);

      /* print server reply */
      fromServer  = new ObjectInputStream(socket.getInputStream());
      packetFromServer = (MazewarPacket) fromServer.readObject();
      if (packetFromServer.type == MazewarPacket.ERROR) {
        System.exit(1);
      }
      
      for (int i = 0; i < packetFromServer.clients.size(); i++) {
        clients.add(packetFromServer.clients.get(i));
      }
      if (packetFromServer.clients.size() == 0) {
        assert(packetFromServer.seqNo != -1);
        token.set(packetFromServer.seqNo);
      }
      
      assert(clients.size() >= 0);
      
      myClientID = packetFromServer.clientID;
      serverSocket = new ServerSocket(clientPort);
      for (int i = 0; i < clients.size(); i++) {
        (new Thread(new socketReceiver(serverSocket.accept(), null, 0))).start();
      }
      
      ClientDataStructure c = new ClientDataStructure();
      c.client = name;
      c.clientID = packetFromServer.clientID;
      c.hostname = hostname;
      c.port = clientPort;
      clients.add(c);
      
    } catch (UnknownHostException e) {
      System.err.println("ERROR: Don't know where to connect!!");
      System.exit(1);
    } catch (IOException e) {
      System.err.println("ERROR: Couldn't get I/O for the connection.");
      System.exit(1);
    } catch (ClassNotFoundException e) {
      System.err.println("ERROR: Class Not Found Exception.");
      e.printStackTrace();
      System.exit(1);
    }
    
    // Create the GUIClient and remote clients
    for (int i = 0; i < clients.size(); i++) {
      if (clients.get(i).client.equals(name)) {
        guiClient = new GUIClient(name);
        maze.addClient(guiClient);
        this.addKeyListener(guiClient);
        while (!guiClient.getOrientation().equals(Direction.North))
          guiClient.turnLeft();
      } else {
        remoteClient = new RemoteClient(clients.get(i).client);
        maze.addClient(remoteClient);
        while (!remoteClient.getOrientation().equals(Direction.North))
          remoteClient.turnLeft();
        //this.addKeyListener(remoteClient);
      }
    }
    
    /*
    // Create the GUIClient and connect it to the KeyListener queue
    guiClient = new GUIClient(name);
    maze.addClient(guiClient);
    this.addKeyListener(guiClient);

    // Use braces to force constructors not to be called at the beginning of the
    // constructor.
    {
      maze.addClient(new RobotClient("Norby"));
      maze.addClient(new RobotClient("Robbie"));
      maze.addClient(new RobotClient("Clango"));
      maze.addClient(new RobotClient("Marvin"));
    }
    */

    // Create the panel that will display the maze.
    overheadPanel = new OverheadMazePanel(maze, guiClient);
    assert (overheadPanel != null);
    maze.addMazeListener(overheadPanel);

    // Don't allow editing the console from the GUI
    console.setEditable(false);
    console.setFocusable(false);
    console.setBorder(BorderFactory.createTitledBorder(BorderFactory
        .createEtchedBorder()));

    // Allow the console to scroll by putting it in a scrollpane
    JScrollPane consoleScrollPane = new JScrollPane(console);
    assert (consoleScrollPane != null);
    consoleScrollPane.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createEtchedBorder(), "Console"));

    // Create the score table
    scoreTable = new JTable(scoreModel);
    assert (scoreTable != null);
    scoreTable.setFocusable(false);
    scoreTable.setRowSelectionAllowed(false);

    // Allow the score table to scroll too.
    JScrollPane scoreScrollPane = new JScrollPane(scoreTable);
    assert (scoreScrollPane != null);
    scoreScrollPane.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createEtchedBorder(), "Scores"));

    // Create the layout manager
    GridBagLayout layout = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    getContentPane().setLayout(layout);

    // Define the constraints on the components.
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1.0;
    c.weighty = 3.0;
    c.gridwidth = GridBagConstraints.REMAINDER;
    layout.setConstraints(overheadPanel, c);
    c.gridwidth = GridBagConstraints.RELATIVE;
    c.weightx = 2.0;
    c.weighty = 1.0;
    layout.setConstraints(consoleScrollPane, c);
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.weightx = 1.0;
    layout.setConstraints(scoreScrollPane, c);

    // Add the components
    getContentPane().add(overheadPanel);
    getContentPane().add(consoleScrollPane);
    getContentPane().add(scoreScrollPane);

    // Pack everything neatly.
    pack();

    // Let the magic begin.
    setVisible(true);
    overheadPanel.repaint();
  }
  
  public static void sendEvent (int event) throws IOException {
    MazewarPacket broadcastPacket = new MazewarPacket();
    if (event == MazewarPacket.QUIT_NAMESERVICE) {
      broadcastPacket.type = MazewarPacket.QUIT_NAMESERVICE;
    } else {
      broadcastPacket.type = MazewarPacket.CMD;
    }
    
    broadcastPacket.command = event;
    broadcastPacket.name = clientName;
    
    broadcastQueue.add(broadcastPacket);
  }
  
  public static class missileTick implements Runnable {
    @Override 
    public synchronized void run(){
      try {
        while (true) {
          System.out.println("Ticking...");
          MazewarPacket packetMissile = new MazewarPacket();
          packetMissile.type = MazewarPacket.MISSILE;
          broadcastQueue.add(packetMissile);
          //packetQueue.add(packetMissile);
          Thread.sleep(200);
        }
      } catch (Exception e) {
        System.err.println("ERROR: encountered during missileTick. Exiting.");
        e.printStackTrace();
        System.exit(1);
      }
    }
  }
  
  public static class socketReceiver implements Runnable {
    private Socket socket = null;
    private ObjectInputStream fromClient = null;
    private int cid;
    public volatile boolean keepAlive;
    private int option;
    private ClientDataStructure newClient;
    
    private socketReceiver (Socket socket, ClientDataStructure newClient, int option) throws IOException {
      this.socket = socket;
      this.keepAlive = true;
      this.option = option;
      this.newClient = newClient;
    }
    
    @Override
    public void run () {
      try {
        if (option == 0) {
          this.fromClient = new ObjectInputStream(this.socket.getInputStream());
          MazewarPacket packetFromClient = (MazewarPacket) fromClient.readObject();
          for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).clientID == packetFromClient.clientID) {
              cid = clients.get(i).clientID;
              clients.get(i).socket = this.socket;
              clients.get(i).outputStream = new ObjectOutputStream(this.socket.getOutputStream());
              clients.get(i).sr = this;
              MazewarPacket reply = new MazewarPacket();
              reply.type = MazewarPacket.NULL;
              reply.clientID = myClientID;
              clients.get(i).outputStream.writeObject(reply);
              break;
            }
          }
        } else if (option == 1) {
          MazewarPacket packetToClient = new MazewarPacket();
          packetToClient.type = MazewarPacket.CONNECT;
          packetToClient.name = clientName;
          packetToClient.clientID = myClientID;
          packetToClient.hostname = InetAddress.getLocalHost().getHostName();
          //packetToClient.hostname = "localhost";
          newClient.sr = this;
          newClient.socket = this.socket;
          newClient.outputStream = new ObjectOutputStream(this.socket.getOutputStream());
          newClient.outputStream.writeObject(packetToClient);
          clients.add(newClient);
          /*
          clients.get(clients.size() - 1).socket = this.socket;
          clients.get(clients.size() - 1).outputStream = new ObjectOutputStream(this.socket.getOutputStream());
          clients.get(clients.size() - 1).outputStream.writeObject(packetToClient);
          */
          // Wait for reply
          this.fromClient = new ObjectInputStream(this.socket.getInputStream());
          MazewarPacket packetFromClient = (MazewarPacket) fromClient.readObject();
          assert(packetFromClient.type == MazewarPacket.NULL);
          this.cid = packetFromClient.clientID;
        } else {
          System.out.print("uhoh, unknown option.");
        }
        
        
        MazewarPacket packet;
//        while (this.socket.isClosed() == false) {
        try {
          while ((packet = (MazewarPacket) fromClient.readObject()) != null) {
            
            
            
            if (packet.type == MazewarPacket.QUIT_NAMESERVICE) {
              break;
            } else if (packet.type == MazewarPacket.TOKEN) {
              token.set(packet.seqNo);
            } else if (packet.type == MazewarPacket.CMD) {
              packetList.add(packet);
            } else if(packet.type == MazewarPacket.MISSILE){
              packetList.add(packet);
            }else {
              System.out.println("Uh-oh, encountered an unknown type. Better check this...");
            }
          }
        }
        catch(Exception e)
        {
          
        }
        while(this.keepAlive);
        
//        this.socket.close();
        
        synchronized(clients) {
        
        
          for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).clientID == cid) {
              /*
              MazewarPacket packet = new MazewarPacket();
              packet.type = MazewarPacket.QUIT_ACK;
              System.out.println("HELLO!");
              System.out.println(clients.get(i).client);
              System.out.println("HARO!!");
              clients.get(i).outputStream.writeObject(packet);
              System.out.println(1);
              */
              clients.get(i).outputStream.close();
              clients.get(i).socket.close();
              this.fromClient.close();
              
              Iterator clientsIt = maze.getClients();
              while(clientsIt.hasNext()) {
                Client client = (Client) clientsIt.next();
                if(client.getName().equals(clients.get(i).client)) {
                  maze.removeClient(client);
                  break;
                }
              }
              clients.remove(i);
              
              MazewarPacket packetToServer = new MazewarPacket();
              packetToServer.type = MazewarPacket.QUIT_ACK;
              toServer.writeObject(packetToServer);
              break;
            }
          }
        
        }
        
        
        
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(-1);
      }
    }
  }
  
  /*
  private static class receiver implements Runnable {
    @Override
    public synchronized void run () {
      try {
        while (true) {
          MazewarPacket packetFromServer = (MazewarPacket) fromServer.readObject();
          packetList.add(packetFromServer);

        }
      } catch (Exception e) {
        System.err.println("ERROR: encountered during packet receiving. Exiting.");
        e.printStackTrace();
        System.exit(1);
      }
      
    }
  }
  */
  
  private static class execute implements Runnable {
    @Override
    public synchronized void run () {
      try {
        while (true) {
          if (!packetList.isEmpty()) {
            MazewarPacket packet = null;
            int lowestIndex = 0;
            for (int i = 0; i < packetList.size(); i++) {
              if (packetList.get(i).seqNo < packetList.get(lowestIndex).seqNo) {
                lowestIndex = i;
              }
            }
            packet = packetList.get(lowestIndex);
            packetList.remove(lowestIndex);
            
            if (packet.type == MazewarPacket.NULL) {
            } else if (packet.type == MazewarPacket.CONNECT) {
            } else if (packet.type == MazewarPacket.DISCONNECT) {
              /*
              if(packet.name.equals(clientName)) {
                return;
              } else {
                Iterator clientsIt = maze.getClients();
                while(clientsIt.hasNext()) {
                  Client client=(Client) clientsIt.next();
                  if(client.getName().equals(packet.name)) {
                    maze.removeClient(client);
                    break;
                  }
                }
              }
              */
            } else if (packet.type == MazewarPacket.CMD) {
              Client movingClient = null;
              Iterator clientsIt = maze.getClients();
              while(clientsIt.hasNext()) {
                Client client=(Client) clientsIt.next();
                if (client.getName().equals(packet.name)) {
                  movingClient = client;
                  break;
                }
              }
              switch(packet.command) {
                case MazewarPacket.FORWARD:
                  movingClient.forward();
                  continue;
                case MazewarPacket.BACKUP:
                  movingClient.backup();
                  continue;
                case MazewarPacket.TURNLEFT:
                  movingClient.turnLeft();
                  continue;
                case MazewarPacket.TURNRIGHT:
                  movingClient.turnRight();
                  continue;
                case MazewarPacket.FIRE:
                  movingClient.fire();
                  continue;
              }
            } else if (packet.type == MazewarPacket.CLIENTS) {
              /*
              assert(!packet.clients.isEmpty());
              ArrayList<String> clientsToAdd = new ArrayList<String>();
              
              for (int i = 0; i < packet.clients.size(); i++) {
                Iterator clientsIt = maze.getClients();
                boolean exists = false;
                while (clientsIt.hasNext()) {
                  Client it = (Client)clientsIt.next();
                  if (it.getName().equals(packet.clients.get(i))) {
                    exists = true;
                    break;
                  }
                }
                if (!exists) {
                  clientsToAdd.add(packet.clients.get(i));
                }
              }
              for (int i = 0; i < clientsToAdd.size(); i++) {
                RemoteClient newclient = new RemoteClient(clientsToAdd.get(i));
                maze.addClient(newclient);
                
                while(!newclient.getOrientation().equals(Direction.North)) {
                  newclient.turnLeft();
                }
              }
              */
            } else if (packet.type == MazewarPacket.ERROR) {
            } else if (packet.type == MazewarPacket.MISSILE) {
              MazeImpl missile = (MazeImpl) maze;
              missile.missileTick();
              
            } else {
            }
          }
        }
      } catch (Exception e) {
        System.err.println("ERROR: encountered during packet execute. Exiting.");
        e.printStackTrace();
        System.exit(1);
      }
      
    }
  }
  
  private static class broadcast implements Runnable {
    @Override
    public synchronized void run () {
      try {
        while (true) {
          if (token.get() != -1) {
            
            synchronized(clients) {
              if (!broadcastQueue.isEmpty()) {
                MazewarPacket broadcastPacket = broadcastQueue.remove(0);
                broadcastPacket.seqNo = token.getAndIncrement();
                
                
                if (broadcastPacket.type == MazewarPacket.QUIT_NAMESERVICE) {
                  token.set(-1);
                  Mazewar.quit(clientName, broadcastPacket);
                }
                
                for (int i = 0; i < clients.size(); i++) {
                  
                  if (!clients.get(i).client.equals(clientName)) {
                    clients.get(i).outputStream.writeObject(broadcastPacket);
                  }
                }
                packetList.add(broadcastPacket);
              }
              if (clients.size() != 1) {
                // send the token
                MazewarPacket tokenPacket = new MazewarPacket();
                tokenPacket.type = MazewarPacket.TOKEN;
                tokenPacket.seqNo = token.incrementAndGet();
                token.set(-1);
                for (int i = 0; i < clients.size(); i++) {
                  if (clients.get(i).client.equals(clientName)) {
                    if (i == clients.size() - 1) {
                      clients.get(0).outputStream.writeObject(tokenPacket);
                    } else {
                      clients.get(i + 1).outputStream.writeObject(tokenPacket);
                    }
                    break;
                  }
                }
              }
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
  
  private static class serverListener implements Runnable {
    @Override
    public synchronized void run () {
      try {
        while (true) {
          MazewarPacket p = (MazewarPacket) fromServer.readObject();

          if (p.type == MazewarPacket.ENTER_NAMESERVICE) {
            
            ClientDataStructure newClient = new ClientDataStructure();
            newClient.client = p.clients.get(0).client;
            newClient.clientID = p.clients.get(0).clientID;
            newClient.hostname = p.clients.get(0).hostname;
            /*
            clients.add(new ClientDataStructure());
            clients.get(clients.size() - 1).client = p.clients.get(0).client;
            clients.get(clients.size() - 1).clientID = p.clients.get(0).clientID;
            clients.get(clients.size() - 1).hostname = p.clients.get(0).hostname;
            System.out.println(p.clients.get(0).hostname);
            System.out.println(p.clients.get(0).port);
            */
            Thread.sleep(1000);
            //newClient.sr = new socketReceiver(new Socket(p.clients.get(0).hostname, p.clients.get(0).port), 1);
            (new Thread(new socketReceiver(new Socket(p.clients.get(0).hostname, p.clients.get(0).port), newClient, 1))).start();
            
            remoteClient = new RemoteClient(p.clients.get(0).client);
            maze.addClient(remoteClient);
            while (!remoteClient.getOrientation().equals(Direction.North))
              remoteClient.turnLeft();
            
          } else if (p.type == MazewarPacket.QUIT_NAMESERVICE) {
            for (int i = 0; i < clients.size(); i++) {
              if (clients.get(i).client == p.name) {
                clients.get(i).sr.keepAlive = false;
                break;
              }
            }
            
            
            
          } else if (p.type == MazewarPacket.QUIT_ACK) {
            quitAck.set(1);
            toServer.close();
            fromServer.close();
            return;
          } else if (p.type == MazewarPacket.TOKEN) {
            assert(token.get() == -1);
            assert(p.seqNo != -1);
            token.set(p.seqNo);
          } else if(p.type == MazewarPacket.MISSILE){
            if (!isMissileServer) {
              isMissileServer = true;
              (new Thread(new missileTick())).start();
            }
          }else {
            System.out.println("Uhoh, unknown packet type. Better check if there are any errors.");
          }
        }
      } catch (Exception e) {
        System.err.println("ERROR: encountered during server listening. Exiting.");
        e.printStackTrace();
        //System.exit(1);
      }
    }
  }
  
  private static class tokenPrint implements Runnable {
    @Override
    public synchronized void run () {
      try {
        while (true) {
          System.out.println(token.get());
        }
      } catch (Exception e) {
        System.err.println("ERROR: encountered during token print. Exiting.");
        e.printStackTrace();
        System.exit(1);
      }
    }
  }
  
  /**
   * Entry point for the game.
   * 
   * @param args
   *          Command-line arguments.
   */
  public static void main(String args[]) {
    /* variables for hostname/port */
    String hostname = "localhost";
    int port = 4444;
    int clientPort = 4445;
    
    if(args.length == 3) {
      hostname = args[0];
      port = Integer.parseInt(args[1]);
      clientPort = Integer.parseInt(args[2]);
    } else {
      System.err.println("ERROR: Invalid arguments!");
      System.exit(-1);
    }
    
    /* Create the GUI */
    new Mazewar(hostname, port, clientPort);
    
    (new Thread(new execute())).start();
    (new Thread(new broadcast())).start();
    System.out.println("ServerListerner");
    
    (new Thread(new serverListener())).start();
    //(new Thread(new tokenPrint())).start();
    
    /*
    (new Thread(new receiver())).start();
    (new Thread(new execute())).start();
    */
  }
}
