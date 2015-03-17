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

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

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
  private RemoteClient remoteClient = null;

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
   */
  public static void quit(String name) {
  MazewarPacket disconnectPacket = new MazewarPacket();
  disconnectPacket.type = MazewarPacket.DISCONNECT;
  disconnectPacket.name = name;
  try {
    toServer.writeObject(disconnectPacket);
  } catch (IOException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }

    System.exit(0);
  }
  public static void quit() {
  System.exit(0);
    }
  public static Maze getMaze()
  {
    return maze;
  }
  /**
   * The place where all the pieces are put together.
   */
  public Mazewar(String hostname, int port) {
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
      packetToServer.type = MazewarPacket.CONNECT;
      packetToServer.name = name;
      toServer.writeObject(packetToServer);

      /* print server reply */
      fromServer  = new ObjectInputStream(socket.getInputStream());
      packetFromServer = (MazewarPacket) fromServer.readObject();
      if (packetFromServer.type == MazewarPacket.ERROR) {
        System.err.println("ERROR: Server returned error packet. Exiting.");
        System.exit(1);
      }

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
    assert(packetFromServer.type == MazewarPacket.CLIENTS);
    for (int i = 0; i < packetFromServer.clients.size(); i++) {
      if (packetFromServer.clients.get(i).equals(name)) {
        guiClient = new GUIClient(name);
        maze.addClient(guiClient);
        this.addKeyListener(guiClient);
        while(!guiClient.getOrientation().equals(Direction.North))
          guiClient.turnLeft();
      } else {
        remoteClient = new RemoteClient(packetFromServer.clients.get(i));
        maze.addClient(remoteClient);
        while(!remoteClient.getOrientation().equals(Direction.North))
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
    MazewarPacket packetToServer = new MazewarPacket();
    packetToServer.type = MazewarPacket.CMD;
    packetToServer.command = event;
    packetToServer.name = clientName;
    
    toServer.writeObject(packetToServer);
  }
  
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
            
            System.out.println("excuting a packet on the client.");
            System.out.println(clientName);
            System.out.println("here is the type");
            System.out.println(packet.type);

            if (packet.type == MazewarPacket.NULL) {
            } else if (packet.type == MazewarPacket.CONNECT) {
            } else if (packet.type == MazewarPacket.DISCONNECT) {
              if(packet.name.equals(clientName)) {
                return;
              } else {
                System.out.println("remove "+packet.name);
                Iterator clientsIt = maze.getClients();
                while(clientsIt.hasNext()) {
                  Client client=(Client) clientsIt.next();
                  System.out.println("client is  "+client.getName());
                  if(client.getName().equals(packet.name)) {
                    maze.removeClient(client);
                    break;
                  }
                }
              }
            } else if (packet.type == MazewarPacket.CMD) {
              System.out.println("moving");
              System.out.println("Here is the packet.command");
              System.out.println(packet.command);
              Client movingClient = null;
              Iterator clientsIt = maze.getClients();
              while(clientsIt.hasNext()) {
                Client client=(Client) clientsIt.next();
                if (client.getName().equals(packet.name)) {
                  movingClient=client;
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
    
    if(args.length == 2 ) {
      hostname = args[0];
      port = Integer.parseInt(args[1]);
    } else {
      System.err.println("ERROR: Invalid arguments!");
      System.exit(-1);
    }

    /* Create the GUI */
    new Mazewar(hostname, port);
    
    (new Thread(new receiver())).start();
    (new Thread(new execute())).start();

  }
}
