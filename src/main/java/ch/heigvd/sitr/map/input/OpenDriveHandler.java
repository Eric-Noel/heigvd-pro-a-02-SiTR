package ch.heigvd.sitr.map.input;

// These imports come from JAXB generated classes
import ch.heigvd.sitr.autogen.opendrive.OpenDRIVE;
import ch.heigvd.sitr.autogen.opendrive.OpenDRIVE.Controller;
import ch.heigvd.sitr.autogen.opendrive.OpenDRIVE.Controller.Control;
import ch.heigvd.sitr.autogen.opendrive.OpenDRIVE.Road;
import ch.heigvd.sitr.autogen.opendrive.OpenDRIVE.Road.Lanes.LaneSection;
import ch.heigvd.sitr.autogen.opendrive.OpenDRIVE.Road.PlanView.Geometry;
import ch.heigvd.sitr.autogen.opendrive.Lane.Link;

import ch.heigvd.sitr.map.Lane;
import ch.heigvd.sitr.map.Lane.LaneSectionType;
import ch.heigvd.sitr.map.RoadNetwork;
import ch.heigvd.sitr.map.RoadSegment;
import ch.heigvd.sitr.map.roadmappings.LaneGeometries;
import ch.heigvd.sitr.map.roadmappings.RoadGeometry;
import ch.heigvd.sitr.map.roadmappings.RoadMapping;
import ch.heigvd.sitr.map.roadmappings.RoadMappingUtils;

import javax.xml.transform.stream.StreamSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is used to parse OpenDrive file (.xodr) and to load the road network
 */
public class OpenDriveHandler {
    private static final Logger LOG = Logger.getLogger(OpenDriveHandler.class.getName());

    // Mapping the signal-ids of single traffic lights to controller
    private final Map<String, Controller> signalIdsToController = new HashMap<>();

    /**
     * This method reads the OpenDRIVE format file and create the road network
     *
     * @param roadNetwork   The road network that will be built from the OpenDrive file
     * @param openDriveFile The OpenDrive file with .xodr extension
     */
    public static void loadRoadNetwork(RoadNetwork roadNetwork, StreamSource openDriveFile) {
        // Generate data from openDRIVE xml file
        OpenDriveUnmarshaller openDriveUnmarshaller = new OpenDriveUnmarshaller();
        OpenDRIVE openDriveNetwork = openDriveUnmarshaller.load(openDriveFile);

        // From these data, create the road network
        OpenDriveHandler openDriveHandler = new OpenDriveHandler();
        openDriveHandler.create(openDriveNetwork, roadNetwork);
    }

    /**
     * This method creates the road network from the data generated by JAXB
     *
     * @param openDriveNetwork Generated data from the OpenDRIVE XML file
     * @param roadNetwork The road network that will be built from generated data
     */
    private void create(OpenDRIVE openDriveNetwork, RoadNetwork roadNetwork) {
        createControllerMapping(openDriveNetwork, roadNetwork);
        createRoadSegments(openDriveNetwork, roadNetwork);
    }

    /**
     * This method creates the controller mapping
     *
     * @param openDriveNetwork Generated data from the OpenDRIVE XML file
     * @param roadNetwork The road network that will be built from generated data
     */
    private void createControllerMapping(OpenDRIVE openDriveNetwork, RoadNetwork roadNetwork) {
        for (Controller controller : openDriveNetwork.getController()) {
            for (Control control : controller.getControl()) {
                if (signalIdsToController.put(control.getSignalId(), controller) != null) {
                    throw new IllegalArgumentException("trafficlight id=" + control.getSignalId()
                            + " is referenced more than once in xodr <controller> definitions.");
                }
            }
        }
        LOG.log(Level.INFO, "registered {0} traffic light signals in road network.",
                signalIdsToController.size());
    }

    /**
     * This method creates all road segments in the road network
     *
     * @param openDriveNetwork Generated data from the OpenDRIVE XML file
     * @param roadNetwork The road network that will be built from generated data
     */
    private void createRoadSegments(OpenDRIVE openDriveNetwork, RoadNetwork roadNetwork) {
        /* A supprimer si les lien entre route fonctionn
        Link q = openDriveNetwork.getRoad().get(1).getLanes().getLaneSection().get(0).getRight().getLane().get(0).getLink();
        int w = 0;
        if(q != null){
            w = q.getPredecessor().getId();
        }
        String e = String.valueOf(w);
        LOG.log(Level.INFO, e);*/
        for (Road road : openDriveNetwork.getRoad()) {
            final RoadMapping roadMapping = createRoadMappings(road);
            for (LaneSectionType laneType : Lane.LaneSectionType.values()) {
                if (hasLaneSectionType(road, laneType)) {
                    RoadSegment roadSegment = createRoadSegment(laneType, road, roadMapping);
                    if (roadSegment == null) {
                        throw new IllegalStateException("could not create roadSegment for road="
                                + road.getId());
                    }
                    roadNetwork.add(roadSegment);
                    LOG.log(Level.INFO, "created roadSegment={0} with laneCount={1}",
                            new Object[]{roadSegment.getUserId(), roadSegment.getLaneCount()});
                }
            }
        }
        LOG.log(Level.INFO, "created {0} roadSegments.", roadNetwork.size());
    }

    /**
     * This method checks if the road has a lane section type
     *
     * @param road The road to check
     * @param laneType The lane section type
     * @return True if the road has the lane section type, false otherwise
     */
    private boolean hasLaneSectionType(Road road, LaneSectionType laneType) {
        if (!road.isSetLanes()) {
            throw new IllegalArgumentException("road=" + road.getId() +  " defined without " +
                    "lanes.");
        }
        if (laneType == LaneSectionType.LEFT) {
            return road.getLanes().getLaneSection().get(0).isSetLeft();
        }
        if (laneType == LaneSectionType.RIGHT) {
            return road.getLanes().getLaneSection().get(0).isSetRight();
        }
        return false;
    }

    /**
     * This method create a road segment in the road network
     *
     * @param laneType The road's lane type
     * @param road The road generated with JAXB from OpenDRIVE xodr file
     * @param roadMapping The created road mapping
     * @return The created road segment
     */
    private RoadSegment createRoadSegment(LaneSectionType laneType, Road road,
                                          RoadMapping roadMapping) {
        LaneSection laneSection = road.getLanes().getLaneSection().get(0);
        List<ch.heigvd.sitr.autogen.opendrive.Lane> lanes = (laneType == LaneSectionType.LEFT) ?
                laneSection.getLeft().getLane() : laneSection.getRight().getLane();
        int successorRoadId = Integer.valueOf(road.getLink().getSuccessor().getElementId());

        // TODO (tum) manage reverse direction
        final RoadSegment roadSegment = new RoadSegment(roadMapping.getRoadLength(),
                lanes.size(), roadMapping, successorRoadId, lanes);

        // Set user id
        String userId = road.getId();
        roadSegment.setUserId(userId);

        // Set road name
        roadSegment.setRoadName(road.getName());

        // Create lanes type
        for (ch.heigvd.sitr.autogen.opendrive.Lane lane : lanes) {
            int laneIndex = laneIdToLaneIndex(lane.getId());
            setLaneType(laneIndex, lane, roadSegment);
        }

        // TODO (tum) Manage road objects here
        // TODO (tum) Manage road signals here
        return roadSegment;
    }

    /**
     * This method creates the road mappings
     *
     * @param road The road to map
     * @return The created road mapping
     */
    private RoadMapping createRoadMappings(Road road) {
        // Create the lane geometry
        LaneSection firstLaneSection = road.getLanes().getLaneSection().get(0);
        LaneGeometries laneGeometries = new LaneGeometries();

        if (firstLaneSection.isSetLeft()) {
            int laneCount = firstLaneSection.getLeft().getLane().size();
            double laneWidth =
                    firstLaneSection.getLeft().getLane().get(0).getWidth().get(0).getA();
            laneGeometries.setLeft(new LaneGeometries.LaneGeometry(laneCount, laneWidth));
        }

        int laneCount = firstLaneSection.getRight().getLane().size();
        double laneWidth = firstLaneSection.getRight().getLane().get(0).getWidth().get(0).getA();
        laneGeometries.setRight(new LaneGeometries.LaneGeometry(laneCount, laneWidth));

        // Create the road geometry
        List<RoadGeometry> roadGeometries =
                createRoadGeometries(road.getPlanView().getGeometry(), laneGeometries);

        // Create the road mapping
        return RoadMappingUtils.create(roadGeometries);
    }

    /**
     * This method creates the road geometries
     *
     * @param geometries OpenDRIVE road geometry
     * @param laneGeometries lane geometries
     * @return List of created road geometries
     */
    private List<RoadGeometry> createRoadGeometries(List<Geometry> geometries,
                                                    LaneGeometries laneGeometries) {
        List<RoadGeometry> roadGeometries = new ArrayList<>(geometries.size());

        for (Geometry geometry : geometries) {
            RoadGeometry roadGeometry = new RoadGeometry(geometry, laneGeometries);
            roadGeometries.add(roadGeometry);
        }

        return roadGeometries;
    }

    private static void setLaneType(int laneNumber, ch.heigvd.sitr.autogen.opendrive.Lane lane,
                                    RoadSegment roadSegment) {
        if (lane.getType().equals(Lane.Type.TRAFFIC.getOpenDriveIdentifier())) {
            roadSegment.setLaneType(laneNumber, Lane.Type.TRAFFIC);
        } else if (lane.getType().equals(Lane.Type.ENTRANCE.getOpenDriveIdentifier())) {
            roadSegment.setLaneType(laneNumber, Lane.Type.ENTRANCE);
        } else if (lane.getType().equals(Lane.Type.RESTRICTED.getOpenDriveIdentifier())) {
            roadSegment.setLaneType(laneNumber, Lane.Type.RESTRICTED);
        } else if (lane.getType().equals(Lane.Type.EXIT.getOpenDriveIdentifier())) {
            roadSegment.setLaneType(laneNumber, Lane.Type.EXIT);
        } else if (lane.getType().equals(Lane.Type.SHOULDER.getOpenDriveIdentifier())) {
            roadSegment.setLaneType(laneNumber, Lane.Type.SHOULDER);
        }
    }

    /**
     * Returns the lane used in roadsegment's (positive integer) from the xodr convention (using
     * laneId 0 and laneId 0 for left and right driving directions.
     *
     * @param xodrLaneId Lane id specified in xodr file
     * @return lane defined as positive integer.
     */
    private static int laneIdToLaneIndex(int xodrLaneId) {
        return Math.abs(xodrLaneId);
    }
}