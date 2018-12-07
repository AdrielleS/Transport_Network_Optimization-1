package services.osrm;

import beans.BusStop;
import beans.Itinerary;
import beans.ItineraryBusStop;
import org.json.JSONArray;
import org.json.JSONObject;
import services.TripSimulator;

public class OsrmTripSimulator extends TripSimulator {

    public OsrmTripSimulator(Itinerary itinerary, int numberOfTrips, int radius) throws Exception{
        super(itinerary, numberOfTrips, radius);
    }

    public Double[] evaluate(Double[] vars) throws Exception {
        Itinerary itinerary = new Itinerary(null, '1', "1", "Solution");
        BusStop v;
        int i = 0, order = 0;
        while (i < vars.length) {
            v = new BusStop(Integer.toString(i), vars[i], vars[i+1]);
            ItineraryBusStop ibs = new ItineraryBusStop(v, itinerary, order);
            itinerary.addItineraryBusStop(ibs);
            order++;
            i += 2;
        }

        OsrmAPIRequester apiRequester = new OsrmAPIRequester();
        JSONArray jsonArray = apiRequester.requestRoute(itinerary.turnIntoBusStopList(itinerary.getStops()));

        JSONArray allLegs = new JSONArray();
        for (i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = (JSONObject) jsonArray.get(i);
            JSONArray legs = jsonObject.getJSONArray("routes").getJSONObject(0).getJSONArray("legs");
            for (int j = 0; j < legs.length(); j++) {
                allLegs.put(legs.get(j));
            }
        }

        double averageWalkingTime = 0;
        double averageTripTime = 0;
        for (int t = 0; t < getNumberOfTrips(); t++) {
            double[] p1 = randomLocationBeta(itinerary);
            double[] p2 = randomLocationBeta(itinerary);
            ItineraryBusStop bs1 = findNearestStop(p1, itinerary);
            ItineraryBusStop bs2 = findNearestStop(p2, itinerary);

            ItineraryBusStop start, end;
            double[] startP = new double[2];
            double[] endP = new double[2];
            JSONObject startWalkJson, endWalkJson;
            if (bs1.getSequenceValue() < bs2.getSequenceValue()) {
                start = bs1;
                end = bs2;
                startP[0] = p1[0];
                startP[1] = p1[1];
                endP[0] = p2[0];
                endP[1] = p2[1];
            } else {
                start = bs2;
                end = bs1;
                startP[0] = p2[0];
                startP[1] = p2[1];
                endP[0] = p1[0];
                endP[1] = p1[1];
            }

            if (start.equals(end)) {
                --t;
            } else {
                startWalkJson = apiRequester.walkingRoute(startP[0], startP[1], start.getBusStop().getLatitude(), start.getBusStop().getLongitude());
                endWalkJson = apiRequester.walkingRoute(end.getBusStop().getLatitude(), end.getBusStop().getLongitude(), endP[0], endP[1]);

                if (!startWalkJson.getString("code").equals("OK") || !endWalkJson.getString("code").equals("OK")) {
                    --t;
                } else {
                    double startWalkDuration = startWalkJson.getJSONArray("routes").getJSONObject(0).getDouble("duration");
                    double endWalkDuration = endWalkJson.getJSONArray("routes").getJSONObject(0).getDouble("duration");

                    double totalTravelTime = 0;
                    for (i = start.getSequenceValue(); i < end.getSequenceValue(); i++) {
                        double duration = allLegs.getJSONObject(i).getDouble("duration");
                        totalTravelTime += duration;
                    }

                    averageTripTime += totalTravelTime;
                    averageWalkingTime += startWalkDuration + endWalkDuration;
                }
            }
        }
        averageTripTime /= getNumberOfTrips();
        averageWalkingTime /= getNumberOfTrips();

        double distanceAverage = 0, d;
        for (i = 0; i < allLegs.length(); i++) {
            d = allLegs.getJSONObject(i).getDouble("distance");
            distanceAverage += d;
        }
        distanceAverage = distanceAverage/allLegs.length();

        double stopsDistanceVariance = 0;
        for (i = 0; i < allLegs.length(); i++) {
            d = allLegs.getJSONObject(i).getDouble("distance");
            stopsDistanceVariance += Math.pow( ((int) d) - distanceAverage, 2);
        }
        stopsDistanceVariance = stopsDistanceVariance/allLegs.length();

        Double[] objectives = new Double[3];
        objectives[0] = averageTripTime;
        objectives[1] = averageWalkingTime;
        objectives[2] = stopsDistanceVariance;

        //System.out.print("averageTripTime: " + averageTripTime + "\naverageWalkingTime: " + averageWalkingTime + "\nstopsDistanceVariance: " + stopsDistanceVariance + "\n");
        //System.out.print("Legs size: " + allLegs.length() + "\n");
        //System.out.print("Distance average: " + distanceAverage + "\n");

        return objectives;
    }

}
