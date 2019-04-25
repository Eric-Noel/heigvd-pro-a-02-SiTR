/*
 * Filename : Simulation.java
 * Creation date : 07.04.2019
 */

package ch.heigvd.sitr.model;

import ch.heigvd.sitr.gui.simulation.Displayer;
import ch.heigvd.sitr.gui.simulation.SimulationWindow;
import ch.heigvd.sitr.map.RoadNetwork;
import ch.heigvd.sitr.map.input.OpenDriveHandler;
import ch.heigvd.sitr.vehicle.Vehicle;
import ch.heigvd.sitr.vehicle.VehicleController;
import lombok.Getter;

import java.io.File;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Simulation class handles all global simulation settings and values
 * The main simulation loop runs here as well
 *
 * @author Luc Wachter
 */
public class Simulation {
    // The displayable component we need to repaint
    private Displayer window;
    // List of vehicles generated by traffic generator
    private LinkedList<Vehicle> vehicles = new LinkedList<>();

    // Rate at which the redrawing will happen in milliseconds
    private static final int UPDATE_RATE = 40;

    // Road network
    private final RoadNetwork roadNetwork;

    // the ratio px/m
    @Getter
    private double scale;

    /**
     * Constructor
     *
     * @param scale the ratio px/m
     */
    public Simulation(double scale) {
        this.scale = scale;

        // Manual hard coded tests
        VehicleController vehicleController = new VehicleController("timid.xml");
        VehicleController vehicleController2 = new VehicleController("careful.xml");

        Vehicle wall = new Vehicle(vehicleController, 1, 1, 0, null);
        Vehicle v1 = new Vehicle("regular.xml", vehicleController);
        Vehicle v2 = new Vehicle("regular.xml", vehicleController2);

        v1.setPosition(40);
        v1.setFrontVehicle(wall);
        vehicles.add(v1);

        v2.setPosition(0);
        v2.setFrontVehicle(v1);
        vehicles.add(v2);

        wall.setPosition(100);
        vehicles.add(wall);

        // Create a roadNetwork instance and then parse the OpenDRIVE XML file
        roadNetwork = new RoadNetwork();
        // TODO : Remove hard coded openDriveFilename
        parseOpenDriveXml(roadNetwork, "src/main/resources/map/simulation/simple_road.xodr");
    }

    /**
     * Main display loop, runs in a fixed rate timer loop
     */
    public void loop() {
        // Launch main window
        window = SimulationWindow.getInstance();

        // Schedule a task to run immediately, and then
        // every UPDATE_RATE per second
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (Vehicle vehicle : vehicles) {
                    vehicle.update(0.5);
                    vehicle.draw(scale);
                    // DEBUG
                    System.out.println(vehicle);
                }

                // TODO (tum) WTF we shouldn't do that
                // Print the road network
                roadNetwork.draw();

                // Callback to paintComponent()
                window.repaint();
            }
        }, 0, UPDATE_RATE);
    }

    /**
     * Convert meters per second to kilometers per hour
     *
     * @param mps The amount of m/s to convert
     * @return the corresponding amount of km/h
     */
    public static double mpsToKph(double mps) {
        // m/s => km/h : x * 3.6
        return mps * 3.6;
    }

    /**
     * Convert kilometers per hour to meters per second
     *
     * @param kph The amount of km/h to convert
     * @return the corresponding
     */
    public static double kphToMps(double kph) {
        // km/h => m/s : x / 3.6
        return kph / 3.6;
    }

    /**
     * Convert m to px
     * @param scale the ratio px/m
     * @param m the number of m
     * @return the number of px
     */
    public static int mToPx(double scale, double m) {
        return (int)Math.round(m * scale);
    }

    /**
     * Convert px to m
     * @param scale the ratio px/m
     * @param px the number of px
     * @return the number of px
     */
    public static double pxToM(double scale, int px) {
        return px / scale;
    }

    /**
     * Convert m to px
     * @param m the number of m
     * @return the number of px
     */
    public int mToPx(double m) {
        return Simulation.mToPx(scale, m);
    }

    /**
     * Convert px to m
     * @param px the number of px
     * @return the number of px
     */
    public double pxToM(int px) {
        return Simulation.pxToM(scale, px);
    }

    /**
     * Parse the OpenDrive XML file
     * @param roadNetwork The Road network that will contains OpenDrive road network
     * @param openDriveFilename The OpenDrive filename
     */
    public void parseOpenDriveXml(RoadNetwork roadNetwork, String openDriveFilename) {
        // TODO (TUM) Add some logs here
        File openDriveFile = new File(openDriveFilename);
        OpenDriveHandler.loadRoadNetwork(roadNetwork, openDriveFile);
    }
}
