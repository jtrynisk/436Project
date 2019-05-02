package Project3;

import de.adesso.anki.AnkiConnector;
import de.adesso.anki.RoadmapScanner;
import de.adesso.anki.Vehicle;
import de.adesso.anki.messages.*;
import de.adesso.anki.roadmap.Roadmap;

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

        Vehicle v = vehicleList.get(0);
        v.connect();
        v.sendMessage(new SdkModeMessage());
        RoadmapScanner rs = new RoadmapScanner(v);
        Roadmap rm = new Roadmap();
        boolean isComplete = false;
        /* This will build a roadmap but currently never exits the while loop.
        while(!isComplete) {
            rs.startScanning();
            isComplete = rs.isComplete();
        }
        rm = rs.getRoadmap();
        rs.stopScanning();
        v.sendMessage(new SetSpeedMessage(0, 200));
        try {
            Thread.sleep(10000);
        }catch(InterruptedException e){
            e.printStackTrace();
        }*/
        v.sendMessage(new SetSpeedMessage(200, 200));
        v.addMessageListener(LocalizationIntersectionUpdateMessage.class,
                (message) -> transitionUpdateHandler(message, v));
    }



    public static void transitionUpdateHandler(LocalizationIntersectionUpdateMessage message, Vehicle v) {
        System.out.println(message.getIntersectionCode());
        if (message.getIntersectionCode() == 0){
            v.sendMessage(new SetSpeedMessage(0, 999999999));
            try {
                Thread.sleep(3000);
            }catch (InterruptedException e){
                e.printStackTrace();
            }
            v.sendMessage(new SetSpeedMessage(200, 200));

        }
    }
}
