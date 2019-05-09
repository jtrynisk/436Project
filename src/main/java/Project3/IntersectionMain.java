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

import java.io.IOException;
import java.util.Queue;

public class IntersectionMain {
	private static CCNA netAdapter;
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
        }
        Vehicle v = vehicleList.get(0);
        v.connect();
        v.sendMessage(new SdkModeMessage());

        v.sendMessage(new SetSpeedMessage(200, 200));
        //move to rightmost lane
        try {
        	System.out.println("Press enter for right lane, type anything else first for middle right lane.");
        	byte[] inp = new byte[256];
        	System.in.read(inp);
        	if (inp[0] == 10) {
        		v.sendMessage(new ChangeLaneMessage(100, 100, 64));
        	}
        	else {
        		v.sendMessage(new ChangeLaneMessage(100, 100, 32));
        	}
        }
        catch (Exception e) {
        	v.sendMessage(new ChangeLaneMessage(100, 100, 64));
        }
        v.addMessageListener(LocalizationIntersectionUpdateMessage.class,
                (message) -> transitionUpdateHandler(message, v, netAdapter));
    }

    /**
     * This takes an intersection message, and a vehicle and has the car stop,
     * broadcast and listen for a message. It will then continue to navigate
     * the track.
     * @param message - the intersection message
     * @param v - the vehicle passed to the function
     */
    public static void transitionUpdateHandler(LocalizationIntersectionUpdateMessage message, Vehicle v, CCNA netAdapter) {
        VehicleInfo ourInfo = new VehicleInfo();
        ourInfo.MACid = v.getAddress();
        if (message.getIntersectionCode() == 0){
            v.sendMessage(new SetSpeedMessage(0, 999999999));
            ourInfo.isClear = false;
            ourInfo.locationID = message.getIntersectionCode();
            ourInfo.speed = 0;
            ourInfo.timestamp = Instant.now();
            ourInfo.otherVehicles = null;
            netAdapter.broadcast(ourInfo);
            Queue<VehicleInfo> atIntersection = netAdapter.listenToBroadcast(3000);
            if(atIntersection.isEmpty()) {
                System.out.println("No one at intersection");
                v.sendMessage(new SetSpeedMessage(200, 200));
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
                    ourInfo.otherVehicles.addAll(netAdapter.awaitClearIntersection(10000));
                    v.sendMessage(new SetSpeedMessage(200, 200));
                }
                else{
                    v.sendMessage(new SetSpeedMessage(200, 200));
                }
            }
            ourInfo.isMaster = true;
            netAdapter.broadcast(ourInfo);
            v.sendMessage(new SetSpeedMessage(200, 200));
        }
        else if(message.getIntersectionCode() == 1){
            System.out.println("Cleared Intersection");
            netAdapter.clearIntersection();
        }
    }

}
