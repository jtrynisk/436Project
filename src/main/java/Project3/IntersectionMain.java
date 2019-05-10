package Project3;

import de.adesso.anki.AnkiConnector;
import de.adesso.anki.RoadmapScanner;
import de.adesso.anki.Vehicle;
import de.adesso.anki.messages.*;
import de.adesso.anki.roadmap.Roadmap;
import edu.oswego.cs.CPSLab.anki.FourWayStop.CCNA;
import edu.oswego.cs.CPSLab.anki.FourWayStop.VehicleInfo;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import java.io.IOException;
import java.util.Queue;

public class IntersectionMain {
	private static CCNA netAdapter;
	private static boolean inIntersection = false;
    public static void main(String[] args) throws IOException{

        final int PORT = 5000;
        final String HOST = (args.length > 0) ? args[0] : "172.20.10.8";
        netAdapter = new CCNA();

        //This does the initial setup of the car
        System.out.println("Creating connection");
        AnkiConnector anki = new AnkiConnector(HOST, PORT);
        System.out.println("Looking for car");
        List<Vehicle> vehicleList = anki.findVehicles();

        if(vehicleList.isEmpty()){
            System.out.println("No cars found");
            netAdapter.kill();
            anki.close();
            return;
        }
        Vehicle v = vehicleList.get(0);
        v.connect();
        v.sendMessage(new SdkModeMessage());
        
        Scanner s = new Scanner(System.in);
        //move to rightmost lane
        try {
        	v.sendMessage(new SetOffsetFromRoadCenterMessage(0));
        	System.out.println("Press enter for current lane, type left or right to shift lanes.");
        	String inp = s.nextLine();
        	if (inp.equalsIgnoreCase("right")) {
        		v.sendMessage(new ChangeLaneMessage(32, 100, 100));
        	}
        	else if (inp.equals("left")) {
        		v.sendMessage(new ChangeLaneMessage(-32, 100, 100));
        	}
        }
        catch (Exception e) {
        	v.sendMessage(new ChangeLaneMessage(32, 100, 100));
        }
        v.addMessageListener(LocalizationIntersectionUpdateMessage.class,
                (message) -> transitionUpdateHandler(message, v, netAdapter));
        v.sendMessage(new SetSpeedMessage(200, 200));
        boolean exitRequested = false;
        String response = "";
        while(!exitRequested) {
        	response = s.nextLine();
        	switch(response) {
        	case "exit":
        		exitRequested = true;
        		break;
        	case "go":
        		v.sendMessage(new SetSpeedMessage(200, 200));
        		break;
        	case "u":
        		v.sendMessage(new TurnMessage(3, 1));
        	case "stop:":
        		v.sendMessage(new SetSpeedMessage(0, 12500));
        		break;
        	case "right":
        		v.sendMessage(new ChangeLaneMessage(32, 100, 100));
        		break;
        	case "left":
        		v.sendMessage(new ChangeLaneMessage(-32, 100, 100));
        		break;
        	case "faster":
        		v.sendMessage(new SetSpeedMessage(250, 200));
        		break;
        	}
        }
        //clean up
        s.close();
        netAdapter.kill();
        v.disconnect();
        anki.close();
    }

    /**
     * This takes an intersection message, and a vehicle and has the car stop,
     * broadcast and listen for a message. It will then continue to navigate
     * the track.
     * @param message - the intersection message
     * @param v - the vehicle passed to the function
     */
    public static void transitionUpdateHandler(LocalizationIntersectionUpdateMessage message, Vehicle v, CCNA netAdapter) {
        if (message.getIntersectionCode() == 0){
        	VehicleInfo ourInfo = new VehicleInfo();
            ourInfo.MACid = v.getAddress();
            v.sendMessage(new SetSpeedMessage(0, 999999999));
            inIntersection = true;
            ourInfo.isClear = false;
            ourInfo.locationID = message.getIntersectionCode();
            ourInfo.speed = 0;
            ourInfo.timestamp = Instant.now();
            ourInfo.otherVehicles = null;
            netAdapter.broadcast(ourInfo);
            Queue<VehicleInfo> atIntersection = netAdapter.listenToBroadcast(3000);
            boolean waiting = false;
            if(atIntersection.isEmpty()) {
                System.out.println("No one at intersection");
                //v.sendMessage(new SetSpeedMessage(200, 200));
            }
            else{
                System.out.println("Intersection not empty");
                boolean otherMaster = false;
                for (VehicleInfo ve : atIntersection) {
                    if (ve.isMaster) {
                        otherMaster = true;
                    }
                }
                if (otherMaster) {
                    ourInfo.otherVehicles = netAdapter.becomeMaster(10000);
                    waiting = true;
                    //v.sendMessage(new SetSpeedMessage(200, 200));
                }
                else{
                	ourInfo.otherVehicles = atIntersection;
                    //v.sendMessage(new SetSpeedMessage(200, 200));
                }
            }
            //take master
            ourInfo.isMaster = true;
            netAdapter.broadcast(ourInfo);
            if (waiting) {
            	//give it 10 seconds tops (adjustable)
            	ourInfo.otherVehicles.addAll(netAdapter.awaitClearIntersection(10000));
            }
            v.sendMessage(new SetSpeedMessage(200, 200));
            netAdapter.notify(ourInfo);
        }
        else if(message.getIntersectionCode() == 1 && inIntersection){
        	inIntersection = false;
            System.out.println("Cleared Intersection");
            netAdapter.clearIntersection();
        }
    }

}
