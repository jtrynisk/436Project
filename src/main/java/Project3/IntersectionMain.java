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

public class IntersectionMain {

    public static void main(String[] args) throws IOException{

        final int PORT = 5000;
        final String HOST = "172.20.10.8";

        //This does the initial setup of the car
        System.out.println("Creating connection");
        AnkiConnector anki = new AnkiConnector(HOST, PORT);
        System.out.println("Looking for car");
        List<Vehicle> vehicleList = anki.findVehicles();

        if(vehicleList.isEmpty()){
            System.out.println("No cars found");
        }
        Vehicle temp = null;
        for(int i = 0; i < vehicleList.size(); i++){
            temp = vehicleList.get(i);
            if (temp.getAdvertisement().getModelId() == 9);
        }
        Vehicle v = temp;
        v.connect();
        v.sendMessage(new SdkModeMessage());

        v.sendMessage(new SetSpeedMessage(200, 200));
        v.addMessageListener(LocalizationIntersectionUpdateMessage.class,
                (message) -> transitionUpdateHandler(message, v));
    }



    public static void transitionUpdateHandler(LocalizationIntersectionUpdateMessage message, Vehicle v) {
        CCNA cc = new CCNA();
        VehicleInfo vi = new VehicleInfo();
        vi.MACid = v.getAddress();
        System.out.println(message.getIntersectionCode());
        if (message.getIntersectionCode() == 0){
            v.sendMessage(new SetSpeedMessage(0, 999999999));
            try {
                vi.isClear = false;
                vi.locationID = message.getIntersectionCode();
                vi.speed = 0;
                vi.timestamp = Instant.now();
                cc.broadcast(vi);
                Thread.sleep(3000);
            }catch (InterruptedException e){
                e.printStackTrace();
            }
            v.sendMessage(new SetSpeedMessage(200, 200));

        }
    }
}
