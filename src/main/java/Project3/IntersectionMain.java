package Project3;

import de.adesso.anki.AnkiConnector;
import de.adesso.anki.RoadmapScanner;
import de.adesso.anki.Vehicle;
import de.adesso.anki.messages.*;
import de.adesso.anki.roadmap.Roadmap;
import edu.oswego.cs.CPSLab.anki.FourWayStop.CCNA;

import java.util.Iterator;
import java.util.List;

import java.io.IOException;

public class IntersectionMain {

    public static void main(String[] args) throws IOException{

        final int PORT = 5000;
        final String HOST = "192.168.43.243";

        //This does the initial setup of the car
        System.out.println("Creating connection");
        AnkiConnector anki = new AnkiConnector(HOST, PORT);
        System.out.println("Looking for car");
        List<Vehicle> vehicleList = anki.findVehicles();

        if(vehicleList.isEmpty()){
            System.out.println("No cars found");
        }

        Iterator<Vehicle> iter = vehicleList.iterator();


        for(int i = 0; i < vehicleList.size(); i++){
            Vehicle temp = iter.next();
            if (temp.getAdvertisement().getIdentifier() == 'a'){}
                //v = temp;

        }
        Vehicle v = vehicleList.get(0);
        v.connect();
        v.sendMessage(new SdkModeMessage());

        v.sendMessage(new SetSpeedMessage(200, 200));
        v.addMessageListener(LocalizationIntersectionUpdateMessage.class,
                (message) -> transitionUpdateHandler(message, v));
    }



    public static void transitionUpdateHandler(LocalizationIntersectionUpdateMessage message, Vehicle v) {
        CCNA cc = new CCNA();
        System.out.println(message.getIntersectionCode());
        if (message.getIntersectionCode() == 0){
            v.sendMessage(new SetSpeedMessage(0, 999999999));
            try {
                //Needs vehicle info
                //cc.broadcast();
                Thread.sleep(3000);
            }catch (InterruptedException e){
                e.printStackTrace();
            }
            v.sendMessage(new SetSpeedMessage(200, 200));

        }
    }
}
