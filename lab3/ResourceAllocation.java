package lab3;

import java.util.Arrays;
import netsim.protocol.*;
import java.util.Hashtable;
import java.util.PriorityQueue;

public class ResourceAllocation extends netsim.protocol.ProtocolAdapter
{
  private int clock;
  
  // The table of timestamps.
  private Hashtable<String,Integer> table = new Hashtable<String,Integer>();
  
  // Our request queue is a queue of messages, because the message class
  // already contains all the information that a queue element would contain 
  // anyway.
  private PriorityQueue<MyMessage> queue = new PriorityQueue<MyMessage>();
  
  // These two state variables keep track of whether this node has the 
  // resource, and whether it is currently waiting for a request to be served.
  private boolean hasResource = false;
  private boolean hasSentRequest = false;
  
  // Visible values for the GUI.
  VisibleString[] visibleQueue = new VisibleString[3];
  VisibleInteger visibleClock;

  public void initiate(NodeInterface myNode)
  {
    super.initiate(myNode);
    visibleClock = myNode.createVisibleInteger("Clock",clock);
    visibleClock.setEditable(true);
    for ( int c = 0; c < visibleQueue.length ; ++c )
    {
      visibleQueue[c] = myNode.createVisibleString("Element" + (c+1),"");
    }
  }

  public String toString()
  {
    return "ResourceAllocation";
  }

  protected void receiveMessage(Message message, InLink inLink) throws Exception
  {
    MyMessage msg = (MyMessage)message;

    if( msg.type == Type.REQUEST )
    {
      recvRequest(msg);
    }
    else if( msg.type == Type.ACK )
    {
      recvAck(msg);
    }
    else if( msg.type == Type.RELEASE )
    {
      recvRelease(msg);
    }
	
    hasResource = checkResource();
    if ( hasResource ) 
    {
      myNode.setActive();
    }
  }

  public void trigg() throws Exception
  {
    // We're setting most of the state variables here (and not inside the "send"
    // methods) because we want to confine them to as few places as possible to 
    // avoid errors.
    if( hasSentRequest )
    {
      sendRelease();
      hasResource = false;
      hasSentRequest = false;
      myNode.setIdle();
    }
    else
    {
      sendRequest();
      hasSentRequest = true;
      myNode.setWaken();
    }
  }

  private class MyMessage implements Message, Comparable<MyMessage>
  {
    String sender;
    int time;
    Type type;
    private MyMessage(String sender, int time, Type type)
    {
      this.sender = sender;
      this.time = time;
      this.type = type;
    }

    public Message clone()
    {
      return new MyMessage(sender, time, type);
    }

    public String getTag()
    {
      String str = "";
      if( type == Type.REQUEST )
      {
        str = "Request,";
      }
      else if( type == Type.ACK )
      {
        str = "Ack,";
      }
      else if( type == Type.RELEASE )
      {
        str = "Release,";
      }

      return str + sender + "," + time ;
    }

    public int compareTo(MyMessage req)
    {
      if(this.time == req.time) 
      {
        return this.sender.compareTo(req.sender);
      }
      return this.time - req.time;
    }
  }

  public enum Type 
  {
    REQUEST,
      ACK,
      RELEASE
  }

  private void sendRequest() throws NetworkBroken
  {
    myNode.writeLogg("Send Request");
    tickClock();
    MyMessage msg = new MyMessage(myNodeName, clock, Type.REQUEST);
    myNode.sendToAllOutlinks(msg);
    queue.add(msg);
    updateQueue();
  }

  private void recvRequest(MyMessage msg) throws NetworkBroken, NotFound
  {
    myNode.writeLogg("Receive Request");
    clock = Math.max(clock,msg.time);
    queue.add( msg );
    updateQueue();
    table.put( msg.sender, msg.time );
    tickClock();
    sendAck(msg.sender);
  }

  private void recvAck(MyMessage msg) 
  {
    myNode.writeLogg("Receive Ack");
    clock = Math.max(clock,msg.time);
    table.put( msg.sender, msg.time );
    tickClock();
  }

  private void sendAck(String sender) throws NetworkBroken, NotFound
  {
    myNode.writeLogg("Send Ack");
    myNode.sendTo(sender,new MyMessage(myNodeName, clock, Type.ACK ));
    tickClock();
  }

  private void sendRelease() throws NetworkBroken
  {
    myNode.writeLogg("Send Release");
    tickClock();
    MyMessage msg = new MyMessage(myNodeName, clock, Type.RELEASE);
    myNode.sendToAllOutlinks(msg);
    removeRequest(myNodeName);
    updateQueue();
  }

  private void recvRelease(MyMessage msg) throws NetworkBroken, NotFound
  {
    myNode.writeLogg("Receive Release");
    clock = Math.max(clock,msg.time);
    removeRequest(msg.sender);
    updateQueue();
    table.put( msg.sender, msg.time );
    tickClock();
  }
  
  // Remove the first request by the given sender. Since a node can not send two
  // requests without sending a release in-between, there will only be at most 
  // one request from any given sender in the queue at any given time.
  private void removeRequest(String targetSender) {
    MyMessage target = null; // The request to remove; will be set below.
    for ( MyMessage request : queue ) {
      if ( targetSender.equals(request.sender) ) {
        target = request;
        break;
      }
    }
    queue.remove(target);
  }

  // Return true if this node can claim the resource.
  private boolean checkResource()
  {
	// If this node sent the first request in the queue, check if all the 
	// timestamps are up-to-date. If one or more timestamps have not yet 
	// arrived at all, we wait for them.
    if( queue.peek() != null && 
        myNodeName.equals(queue.peek().sender) &&
        myNode.getOutLinks().length == table.size() )
    {
      for (int i : table.values()) 
      {
        if (queue.peek().time >= i) 
        {
          return false;
        }
      }
      return true;
    }
    return false;
  }
  
  private void tickClock()
  {
    clock++;
    visibleClock.setValue(clock);
  }

  private void updateQueue()
  {
    for( VisibleString v : visibleQueue ) 
    {
      v.setValue("");
    }

    MyMessage[] reqArray = queue.toArray( new MyMessage[0] );
    Arrays.sort(reqArray);
    for (int i = 0; i < visibleQueue.length; ++i ) 
    {
      if( reqArray.length > i )
      {
        String str = reqArray[i].sender + ":" + reqArray[i].time;
        visibleQueue[i].setValue(str);
      }
    }
  }
}

