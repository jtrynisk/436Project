package Project3;

import de.adesso.anki.AnkiConnector;
import de.adesso.anki.Vehicle;
import de.adesso.anki.messages.*;
import de.adesso.anki.messages.LightsPatternMessage.LightConfig;
import edu.oswego.cs.CPSLab.anki.FourWayStop.CCNA;
import edu.oswego.cs.CPSLab.anki.FourWayStop.VehicleInfo;

import java.time.Instant;
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
        if (!netAdapter.isOpen()) {
        	System.out.println("Multicast unavailable.");
        	return;
        }

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
        System.out.println("Please place the vehicle in the rightmost lane, the press enter.");
        String inp = s.nextLine();
        
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
        	case "lights":
        		LightConfig lc = new LightConfig(LightsPatternMessage.LightChannel.FRONT_GREEN, LightsPatternMessage.LightEffect.THROB, 50, 0, 10);
        		LightConfig lc2 = new LightConfig(LightsPatternMessage.LightChannel.ENGINE_RED, LightsPatternMessage.LightEffect.FADE, 50, 0, 10);
                LightsPatternMessage lpm = new LightsPatternMessage();
                lpm.add(lc);
                lpm.add(lc2);
                v.sendMessage(lpm);
        	}
        }
        //clean up
        s.close();
        netAdapter.kill();
        v.disconnect();
        anki.close();
    }

    /**
     * This method takes a a message that updates when it gets to an intersection, a vehicle so we can set
     * the speed and tell it to stop, the netadapter goes through and communicates with the other vehicles in order
     * to negotiatie four way intersections.
     * @param message
     * @param v
     * @param netAdapter
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
