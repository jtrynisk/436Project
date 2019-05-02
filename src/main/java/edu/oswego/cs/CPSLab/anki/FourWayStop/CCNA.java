package edu.oswego.cs.CPSLab.anki.FourWayStop;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class CCNA {
	//opcodes
	public static final int IGNORE_MESSAGE = 0;
	public static final int BROADCAST_MESSAGE = 1;
	public static final int CLEAR_MESSAGE = 2;
	public static final int NOTIFY_MESSAGE = 3;
	
	public static final int PORT = 9000;
	public static String MULTI_ADDRESS = "239.255.4.36";
	public MulticastSocket sock = null;
	
	private static int remainingCycles = 120000;
	public static AtomicBoolean killRequested = new AtomicBoolean(false);
	private static AtomicBoolean clearReceived = new AtomicBoolean(false);
	private static LinkedList<byte[]> outgoingData = new LinkedList<byte[]>();
	private static AtomicInteger broadcastListenersActive = new AtomicInteger(0);
	private static ArrayList<VehicleInfo> incomingVehicles = new ArrayList<VehicleInfo>();
	private static AtomicBoolean notificationReceived = new AtomicBoolean(false);
	private static Queue<VehicleInfo> waitingVehicles = null;
	private static String lastMACbroadcast = "";
	//sender functions
	//sends our info to everyone
	public void broadcast(VehicleInfo vi) {
		lastMACbroadcast = vi.MACid;
		sendPacket(BROADCAST_MESSAGE, vi, "Broadcast failed.");
	}
	//tells everyone that we are out of the intersection
	public void clearIntersection() {
		sendPacket(CLEAR_MESSAGE, null, "Clear alert failed.");
	}
	//the target will filter out notifications not meant for it
	public void notify(VehicleInfo sender) {
		sendPacket(NOTIFY_MESSAGE, sender, "Notification failed.");
	}
	
	
	
	
	//listener functions
	//blocks until a clear is received
	public void awaitClearIntersection() {
		clearReceived.set(false);
		while (clearReceived.get() == false) {
			try {
				Thread.sleep(2);
			}
			catch (InterruptedException e) {}
		}
		clearReceived.set(false);
	}
	//responders will be added to the queue if not already present
	//timeout is in milliseconds
	//this method should only ever be active in one thread
	public void listenForBroadcast(Queue<VehicleInfo> responders, int timeout) {
		//only clear the list if no one else is using it,
		//but atomically update the value so no one else clears it after us
		if (broadcastListenersActive.compareAndSet(0, 1)) {
			incomingVehicles.clear();
		}
		//add ourselves to the count if someone else is already waiting
		else {
			broadcastListenersActive.incrementAndGet();
		}
		//the first point we care about
		int startIndex = incomingVehicles.size();
		//wait the appropriate amount of time
		try {
			Thread.sleep(timeout);
		}
		//maybe we should try to wait the full time again?
		catch (InterruptedException e) {}
		//the last point we care about
		int endIndex = incomingVehicles.size();
		//grab all the vehicles we care about if they are valid
		for (int i = startIndex; i < endIndex; i++) {
			VehicleInfo vi = incomingVehicles.get(i);
			if (vi != null && !responders.contains(vi)) {
				responders.add(vi);
			}
		}
		//remove ourselves from the count of listeners
		broadcastListenersActive.decrementAndGet();
	}
	//the queue will be purged and then filled with whatever the previous master sent
	public void becomeMaster(Queue<VehicleInfo> laterVehicles) {
		//clear any prior flags
		notificationReceived.set(false);
		//wait for the flag to be flipped
		while (notificationReceived.get() == false) {
			try {
				Thread.sleep(2);
			}
			catch (InterruptedException e) {}
		}
		//grab the queue
		Queue<VehicleInfo> claimedVehicles = waitingVehicles;
		//check if we are actually supposed to be master now
		if (claimedVehicles.peek() != null && claimedVehicles.peek().MACid.equals(lastMACbroadcast)) {
			//purge the queue
			laterVehicles.clear();
			//fill it with the passed elements
			for (VehicleInfo vi : claimedVehicles) {
				laterVehicles.add(vi);
			}
			//take ourselves out of the queue
			laterVehicles.poll();
		}
		//if it wasn't for us, just recurse
		else {
			becomeMaster(laterVehicles);
		}
	}
	
	
	
	//auxiliary functions, for abstracting the multicast part
	public void sendPacket(int opcode, VehicleInfo vi, String failureMessage) {
		try {
			ByteArrayOutputStream data = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(data);
			oos.write(opcode);
			oos.writeObject(vi);
			outgoingData.addLast(data.toByteArray());
		}
		catch (IOException e) {
			//not much we can do here
			System.out.println(failureMessage);
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
		catch (IOException e) {
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
	//pulls the data out of a packet
	//waits only as long as the timeout (in milliseconds)
	//returns null if it timed out
	private byte[] receiveData(int timeout) {
		DatagramPacket pack = new DatagramPacket(new byte[512], 512);
		try {
			sock.setSoTimeout(timeout);
			sock.receive(pack);
			return pack.getData();
		}
		catch (Exception e) {
			return null;
		}
	}
	
	private class Loop extends Thread {
		private boolean constructionSuccessful = true;
		public Loop() {
			try {
				if (sock == null) {
					sock = new MulticastSocket(PORT);
					sock.joinGroup(InetAddress.getByName(MULTI_ADDRESS));
				}
			}
			catch (IOException e) {
				constructionSuccessful = false;
			}
		}
		public void run() {
			if (!constructionSuccessful) return;
			while (!killRequested.get() && remainingCycles > 0) {
				//send data if we have some
				if (!outgoingData.isEmpty()) {
					broadcastData(outgoingData.pollFirst());
				}
				//wait up to 10 milliseconds for a packet
				byte[] payload = receiveData(10);
				//handle the packet
				int opcode = extractOpcode(payload);
				//is broadcast
				if (opcode == 1 && broadcastListenersActive.get() > 0) {
					VehicleInfo vi = extractVehicleInfo(payload);
					if (vi != null) incomingVehicles.add(vi);
				}
				//is clear
				else if (opcode == 2) {
					clearReceived.set(true);
				}
				//is notify
				else if (opcode == 3) {
					VehicleInfo vi = extractVehicleInfo(payload);
					waitingVehicles = vi.otherVehicles;
					notificationReceived.set(true);
				}
				//is a packet that should be ignored
				else {}
				remainingCycles--;
			}
			try {
				sock.leaveGroup(InetAddress.getByName(MULTI_ADDRESS));
				sock.close();
			}
			catch (IOException e) {}
		}
	}
}
