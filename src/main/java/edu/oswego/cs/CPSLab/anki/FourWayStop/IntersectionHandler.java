package edu.oswego.cs.CPSLab.anki.FourWayStop;

import java.util.Queue;

/**
 * An interface to handle communication between cyber-physical vehicles.
 * @author Shakhar Dasgupta <sdasgupt@oswego.edu>
 * @author Benjamin Groman <bgroman@oswego.edu>
 */
public interface IntersectionHandler {
	//these three functions return false if an error was encountered
    boolean broadcast(VehicleInfo vehicleInfo);
    boolean clearIntersection();
    boolean notify(VehicleInfo target);
    
    //returns true if and only if a clear was actually received,
    //rather than a timeout
    boolean awaitClearIntersection(int timeout);
    //returns a queue of responders
    //null indicates the timeout was reached
    Queue<VehicleInfo> listenToBroadcast(int timeout);
    //returns the queue received from the previous master,
    //without the current vehicle
    //empty queue indicates no one else is at the intersection
    //null indicates the timeout was reached
    Queue<VehicleInfo> becomeMaster(int timeout);
}