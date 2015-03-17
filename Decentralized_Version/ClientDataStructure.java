import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientDataStructure implements Serializable {  
  String client;
  int clientID;
  String hostname;
  int port;
  Socket socket;
  ObjectOutputStream outputStream;
  Mazewar.socketReceiver sr;
}