package Project3;

import de.adesso.anki.AnkiConnector;
import de.adesso.anki.Vehicle;
import de.adesso.anki.messages.SdkModeMessage;

import java.util.Iterator;
import java.util.List;

import java.io.IOException;

public class IntersectionMain {

    public static void main(String[] args) throws IOException{

        final int PORT = 9000;
        final String HOST = "localhost";

        //This does the initial setup of the car
        System.out.println("Creating connection");
        AnkiConnector anki = new AnkiConnector(HOST, PORT);
        System.out.println("Looking for car");
        List<Vehicle> vehicleList = anki.findVehicles();

        if(vehicleList.isEmpty()){
            System.out.println("No cars found");
        }

        /**
         * This goes through each vehicle and sets the sdkmode message
         * The ankiConnectionTest has comments that this
         * must be done with every car.
         **/
        Iterator<Vehicle> iter = vehicleList.iterator();
        while(iter.hasNext()){
            Vehicle v = iter.next();
            v.connect();
            v.sendMessage(new SdkModeMessage());
        }

    }

}
