package edu.oswego.cs.CPSLab.anki.FourWayStop;
import java.io.*;
import java.net.*;
import java.util.*;
import java.time.*;

public class CCNA implements IntersectionHandler {
	//opcodes
	public static final int IGNORE_MESSAGE = 0;
	public static final int BROADCAST_MESSAGE = 1;
	public static final int CLEAR_MESSAGE = 2;
	public static final int NOTIFY_MESSAGE = 3;
	
	public static final int PORT = 9000;
	public static String MULTI_ADDRESS = "239.255.4.36";
	private MulticastSocket sock = null;
	
	/**Set by broadcast, used by becomeMaster and awaitClear.
	 * If no broadcast occurs before trying to become master,
	 * becomeMaster should timeout because nobody knows we exist,
	 * a prerequisite for assigning us master. 
	**/
	private String lastMACbroadcast = "";
	
	public CCNA() {
		try {
			sock = new MulticastSocket(PORT);
			sock.joinGroup(InetAddress.getByName(MULTI_ADDRESS));
		}
		catch (IOException e) {
			if (sock != null) sock.close();
		}
	}
	public void kill() {
		try {
			sock.leaveGroup(InetAddress.getByName(MULTI_ADDRESS));
		}
		catch (Exception e) {}
		sock.close();
	}
	//sender functions
	//sends our info to everyone
	public boolean broadcast(VehicleInfo vi) {
		lastMACbroadcast = vi.MACid;
		return sendPacket(BROADCAST_MESSAGE, vi, "Broadcast failed.");
	}
	//tells everyone that we are out of the intersection
	public boolean clearIntersection() {
		return sendPacket(CLEAR_MESSAGE, null, "Clear alert failed.");
	}
	//the target will filter out notifications not meant for it
	public boolean notify(VehicleInfo sender) {
		return sendPacket(NOTIFY_MESSAGE, sender, "Notification failed.");
	}
	
	
	
	
	//listener functions
	/**Blocks until a clear is received.
	 * Responds to incoming broadcasts with a master claim.
	 * timeout is in milliseconds.
	 * returns a list of all new arrivals, unless no one sent a broadcast or clear.
	 */
	public Queue<VehicleInfo> awaitClearIntersection(int timeout) {
		boolean clearReceived = false;
		Instant start = Instant.now();
		long cutoff = System.currentTimeMillis() + timeout;
		Queue<VehicleInfo> arrivals = new LinkedList<VehicleInfo>();
		while (!clearReceived) {
			//wait for as long as we have left
			byte[] packet = receiveData((int)(cutoff - System.currentTimeMillis()));
			if (packet == null) {
				//we timed out, so we're done
				return (arrivals.size() > 0) ? arrivals : null;
			}
			//screen ancient packets
			Instant then = extractInstant(packet);
			if (then == null || then.isBefore(start)) {
				continue;
			}
			int opcode = extractOpcode(packet);
			if (opcode == CLEAR_MESSAGE) {
				//we got what we were waiting for
				clearReceived = true;
			}
			else if (opcode == BROADCAST_MESSAGE && extractVehicleInfo(packet) != null) {
				arrivals.add(extractVehicleInfo(packet));
				//we need to respond that we're master
				VehicleInfo vi = new VehicleInfo();
				vi.isMaster = true;
				vi.MACid = lastMACbroadcast;
				sendPacket(BROADCAST_MESSAGE, vi, "Master assertion failed.");
			}
		}
		return arrivals;
	}
	//responders will be added to the queue if not already present
	//timeout is in milliseconds
	//this method should only ever be active in one thread
	public Queue<VehicleInfo> listenToBroadcast(int timeout) {
		boolean timedOut = false;
		Instant start = Instant.now();
		long cutoff = System.currentTimeMillis() + timeout;
		Queue<VehicleInfo> responders = new LinkedList<VehicleInfo>();
		while (!timedOut) {
			//wait for as long as we have left
			byte[] packet = receiveData((int)(cutoff - System.currentTimeMillis()));
			if (packet == null) {
				//we timed out, so we're done
				timedOut = true;
			}
			//screen ancient packets
			Instant then = extractInstant(packet);
			if (then == null || then.isBefore(start)) {
				continue;
			}
			if (extractOpcode(packet) == BROADCAST_MESSAGE) {
				VehicleInfo vi = extractVehicleInfo(packet);
				if (vi != null && !responders.contains(vi)) {
					responders.add(vi);
				}
			}
		}
		return responders;
	}
	//the queue will contain whatever the previous master sent
	public Queue<VehicleInfo> becomeMaster(int timeout) {
		boolean ourTurn = false;
		Instant start = Instant.now();
		long cutoff = System.currentTimeMillis() + timeout;
		while (!ourTurn) {
			//wait for as long as we have left
			byte[] packet = receiveData((int)(cutoff - System.currentTimeMillis()));
			if (packet == null) {
				//we timed out, so we're done
				return null;
			}
			//screen ancient packets
			Instant then = extractInstant(packet);
			if (then == null || then.isBefore(start)) {
				continue;
			}
			int opcode = extractOpcode(packet);
			if (opcode == NOTIFY_MESSAGE) {
				//check if it's for us
				VehicleInfo sender = extractVehicleInfo(packet);
				//if it's us, the check will have the side effect of removing us from the queue,
				//making it ready for return
				if (sender != null && sender.otherVehicles != null && sender.otherVehicles.poll().MACid.equals(lastMACbroadcast)) {
					return sender.otherVehicles;
				}
			}
		}
		//this should never be reached because receiveData will eventually return null
		return null;
	}
	
	
	
	//auxiliary functions, for abstracting the multicast part
	private boolean sendPacket(int opcode, VehicleInfo vi, String failureMessage) {
		try {
			ByteArrayOutputStream data = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(data);
			oos.write(opcode);
			oos.writeObject(vi);
			oos.writeObject(Instant.now());
			return broadcastData(data.toByteArray());
		}
		catch (IOException e) {
			//not much we can do here
			System.out.println(failureMessage);
			return false;
		}
	}
	//builds a packet and sends it to everyone it knows about
	//returns true if the packet was sent successfully
	private boolean broadcastData(byte[] data) {
		try {
			DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(MULTI_ADDRESS), PORT);
			sock.send(packet);
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}
	private int extractOpcode(byte[] data) {
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(data);
			ObjectInputStream ois = new ObjectInputStream(bais);
			return ois.readInt();
		}
		catch(IOException e) {
			return 0;
		}
	}
	private VehicleInfo extractVehicleInfo(byte[] data) {
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(data);
			ObjectInputStream ois = new ObjectInputStream(bais);
			ois.readInt();
			return (VehicleInfo)ois.readObject();
		}
		catch(Exception e) {
			return null;
		}
	}
	private Instant extractInstant(byte[] data) {
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(data);
			ObjectInputStream ois = new ObjectInputStream(bais);
			ois.readInt();//opcode
			ois.readObject();//vehicle info
			return (Instant)ois.readObject();
		}
		catch(Exception e) {
			return null;
		}
	}
	//pulls the data out of a packet
	//waits only as long as the timeout (in milliseconds)
	//returns null if it timed out
	private byte[] receiveData(int timeout) {
		if (timeout < 0) return null;
		DatagramPacket pack = new DatagramPacket(new byte[512], 512);
		try {
			sock.setSoTimeout(timeout);
			sock.receive(pack);
			return pack.getData();
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
			return null;
		}
	}
}
