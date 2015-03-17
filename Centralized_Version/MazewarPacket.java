import java.io.Serializable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;


public class MazewarPacket implements Serializable {
  
  /* define packet formats */
  public static final int NULL = 0;
  public static final int CONNECT = 100;
  public static final int DISCONNECT = 200;
  public static final int CMD = 300;
  public static final int CLIENTS = 400;
  public static final int MISSILE = 50;
  public static final int ERROR = -1;
  
  /* available commands if type = CMD */
  public static final int FORWARD = 400;
  public static final int BACKUP = 500;
  public static final int TURNLEFT = 600;
  public static final int TURNRIGHT = 700;
  public static final int FIRE = 800;

  /* the packet payload */
  
  /* initialized to be a null packet */
  public int type = NULL;
  
  /* the command */
  public int command = NULL;
  
  /* client id of the sender */
  public String name = "";
  
  /* sequence number */
  public Integer seqNo;
  
  /* A list of clients */
  public CopyOnWriteArrayList<String> clients = new CopyOnWriteArrayList<String>();

}
