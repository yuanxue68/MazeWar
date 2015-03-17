import java.awt.event.KeyEvent;
import java.io.IOException;

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

/**
 * A skeleton for those {@link Client}s that correspond to clients on other
 * computers.
 * 
 * @author Geoffrey Washburn &lt;<a
 *         href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: RemoteClient.java 342 2004-01-23 21:35:52Z geoffw $
 */

public class RemoteClient extends Client {

  /**
   * Create a remotely controlled {@link Client}.
   * 
   * @param name
   *          Name of this {@link RemoteClient}.
   */
  public RemoteClient(String name) {
    super(name);
  }
  public void keyPressed(KeyEvent e) {
      int event = 0;
      // If the user pressed Q, invoke the cleanup code and quit.
      if((e.getKeyChar() == 'q') || (e.getKeyChar() == 'Q')) {
        Mazewar.quit(this.getName());
        // Up-arrow moves forward.
      } else if(e.getKeyCode() == KeyEvent.VK_UP) {
        forward();
        event = MazewarPacket.FORWARD;
        // Down-arrow moves backward.
      } else if(e.getKeyCode() == KeyEvent.VK_DOWN) {
        backup();
        event = MazewarPacket.BACKUP;
        // Left-arrow turns left.
      } else if(e.getKeyCode() == KeyEvent.VK_LEFT) {
        turnLeft();
        event = MazewarPacket.TURNLEFT;
        // Right-arrow turns right.
      } else if(e.getKeyCode() == KeyEvent.VK_RIGHT) {
        turnRight();
        event = MazewarPacket.TURNRIGHT;
        // Spacebar fires.
      } else if(e.getKeyCode() == KeyEvent.VK_SPACE) {
        //fire();
        event = MazewarPacket.FIRE;
      }
      try {
        Mazewar.sendEvent(event);
      } catch (IOException e1) {
        e1.printStackTrace();
        System.exit(-1);
      }
  }

  /**
   * May want to fill in code here.
   */
}
