package maps.convert.osm2gml;

import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;

import java.awt.geom.Area;
import java.util.*;

public class ConnectBuildingsStep extends BaseModificationStep {

    private final double maxConnectDistance;
    private final double maxAngleDeviation;
    private final double entranceWidth;

    private record EntrancePlan(
            TemporaryIntersection entranceObject,
            Edge buildingEdge, Edge roadEdge,
            Node buildingNode1, Node buildingNode2,
            Node roadNode1, Node roadNode2
    ) {}

    public ConnectBuildingsStep(TemporaryMap map) {
        super(map);
        maxConnectDistance = ConvertTools.sizeOfMeters(map.getOSMMap(), 20); // 20 meters
        maxAngleDeviation = 45;
        entranceWidth = ConvertTools.sizeOfMeters(map.getOSMMap(), Constants.ROAD_WIDTH);
    }

    @Override
    public String getDescription() {
        return "Connecting buildings to roads";
    }

    @Override
    protected void step() {
        List<TemporaryBuilding> buildings = new ArrayList<>(map.getBuildings());

        // Find the best possible entrance for each building without modifying the map.
        List<EntrancePlan> plannedEntrances = findBestEntrances(buildings);

        // Apply the valid, non-conflicting plans to the map.
        List<TemporaryIntersection> finalEntrances = executePlans(plannedEntrances);

        if (!finalEntrances.isEmpty()) {
            map.resynchronizeStateFromObjects();
        }

        setProgress(buildings.size());
        setStatus("Created " + finalEntrances.size() + " new entrances for buildings.");
        visualizeDifference(Collections.emptyList(), finalEntrances, "Building Connection Results");
    }

    private List<EntrancePlan> findBestEntrances(List<TemporaryBuilding> buildings) {
        List<EntrancePlan> plans = new ArrayList<>();
        setProgressLimit(buildings.size());

        for (int i = 0; i < buildings.size(); i++) {
            TemporaryBuilding building = buildings.get(i);
            setProgress(i);

            if (isAlreadyConnected(building, map.getRoads())) continue;

            EntrancePlan bestPlan = findBestPlanForBuilding(building);
            if (bestPlan != null) {
                map.splitEdge(bestPlan.buildingEdge(), bestPlan.buildingNode1(), bestPlan.buildingNode2());
                map.splitEdge(bestPlan.roadEdge(), bestPlan.roadNode1(), bestPlan.roadNode2());

                plans.add(bestPlan);
            }
        }

        return plans;
    }

    private EntrancePlan findBestPlanForBuilding(TemporaryBuilding building) {
        EntrancePlan bestPlan = null;
        double bestAngleDeviation = Double.MAX_VALUE;

        for (DirectedEdge de : building.getEdges()) {
            Edge buildingEdge = de.getEdge();
            if (buildingEdge.getLine().getDirection().getLength() < entranceWidth) continue;

            for (TemporaryRoad road : map.getRoads()) {
                for (DirectedEdge roadDE : road.getEdges()) {
                    Edge roadEdge = roadDE.getEdge();

                    // Pre-computation and validation of a candidate pair
                    if (1 < map.getAttachedObjects(roadEdge).size()) continue;
                    if (roadEdge.getLine().getDirection().getLength() < entranceWidth) continue;

                    Point2D wallMidPoint = buildingEdge.getMidPoint();
                    Line2D roadLine = roadEdge.getLine();
                    Point2D connectingPoint = GeometryTools2D.getClosestPointOnSegment(roadLine, wallMidPoint);

                    Line2D entranceCenterLine = new Line2D(wallMidPoint, connectingPoint);
                    if (maxConnectDistance < entranceCenterLine.getDirection().getLength()) continue;

                    double angleDeviation = calculateAngleDeviation(entranceCenterLine, buildingEdge, roadEdge);
                    if (maxAngleDeviation < angleDeviation) continue;

                    if (angleDeviation < bestAngleDeviation) {
                        bestAngleDeviation = angleDeviation;
                        bestPlan = createPlan(building, buildingEdge, roadEdge);
                    }
                }
            }
        }

        return bestPlan;
    }

    private double calculateAngleDeviation(Line2D centerLine, Edge buildingEdge, Edge roadEdge) {
        double angleToBuilding = Math.abs(90.0 - GeometryTools2D.getAngleBetweenVectors(
                centerLine.getDirection(), buildingEdge.getLine().getDirection()));
        double angleToRoad = Math.abs(90.0 - GeometryTools2D.getAngleBetweenVectors(
                centerLine.getDirection(), roadEdge.getLine().getDirection()));
        return Math.max(angleToBuilding, angleToRoad);
    }

    private EntrancePlan createPlan(TemporaryBuilding building, Edge buildingEdge, Edge roadEdge) {
        Point2D wallMidPoint = buildingEdge.getMidPoint();
        Line2D roadLine = roadEdge.getLine();
        Point2D connectingPoint = GeometryTools2D.getClosestPointOnSegment(roadLine, wallMidPoint);

        // Safely calculate the entrance roof on the road, sliding if necessary.
        double distFromStart = GeometryTools2D.getDistance(roadLine.getOrigin(), connectingPoint);
        double distFromEnd = roadLine.getDirection().getLength() - distFromStart;
        double halfWidth = entranceWidth / 2.0;
        if (distFromStart < halfWidth) {
            double slideAmount = halfWidth - distFromStart;
            connectingPoint = connectingPoint.plus(roadLine.getDirection().normalised().scale(slideAmount));
        } else if (distFromEnd < halfWidth) {
            double slideAmount = halfWidth - distFromEnd;
            connectingPoint = connectingPoint.plus(roadLine.getDirection().normalised().scale(-slideAmount));
        }

        // Define the entrance base on the building wall.
        Vector2D wallVector = buildingEdge.getLine().getDirection().normalised();
        Node b1 = map.getNode(wallMidPoint.plus(wallVector.scale(-halfWidth)));
        Node b2 = map.getNode(wallMidPoint.plus(wallVector.scale(halfWidth)));

        // Define the entrance top using these safe distances.
        Vector2D roadVector = roadLine.getDirection().normalised();
        Node r1 = map.getNode(connectingPoint.plus(roadVector.scale(-halfWidth)));
        Node r2 = map.getNode(connectingPoint.plus(roadVector.scale(halfWidth)));

        // Create entrance shape
        List<DirectedEdge> entranceEdges = new ArrayList<>();
        if (0 < wallVector.dot(roadVector)) {
            entranceEdges.add(map.getDirectedEdge(b1, b2));
            entranceEdges.add(map.getDirectedEdge(b2, r2));
            entranceEdges.add(map.getDirectedEdge(r2, r1));
            entranceEdges.add(map.getDirectedEdge(r1, b1));
        } else {
            entranceEdges.add(map.getDirectedEdge(b1, b2));
            entranceEdges.add(map.getDirectedEdge(b2, r1));
            entranceEdges.add(map.getDirectedEdge(r1, r2));
            entranceEdges.add(map.getDirectedEdge(r2, b1));
        }

        TemporaryIntersection entrance = new TemporaryIntersection(entranceEdges);

        // Final collision check.
        if (hasCollision(building, roadEdge, entrance)) {
            return null;
        }

        return new EntrancePlan(entrance, buildingEdge, roadEdge, b1, b2, r1, r2);
    }

    private List<TemporaryIntersection> executePlans(List<EntrancePlan> plans) {
        List<TemporaryIntersection> finalEntrances = new ArrayList<>();

        for (EntrancePlan plan : plans) {
            finalEntrances.add(plan.entranceObject());
        }
        for (TemporaryIntersection entrance : finalEntrances) {
            map.addIntersection(entrance);
        }

        return finalEntrances;
    }

    private boolean isAlreadyConnected(TemporaryBuilding building, Collection<TemporaryRoad> roads) {
        Set<Edge> buildingEdges = new HashSet<>();
        for (DirectedEdge de : building.getEdges()) {
            buildingEdges.add(de.getEdge());
        }
        for (TemporaryRoad road : roads) {
            for (DirectedEdge de : road.getEdges()) {
                if (buildingEdges.contains(de.getEdge())) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasCollision(TemporaryBuilding building, Edge roadEdge, TemporaryIntersection potentialEntrance) {
        if (potentialEntrance.getShape() == null) return false;
        Area entranceArea = new Area(potentialEntrance.getShape());

        TemporaryRoad parentRoadObject = null;
        for (TemporaryRoad p : map.getRoads()) {
            for (DirectedEdge de : p.getEdges()) {
                if (de.getEdge().equals(roadEdge)) {
                    parentRoadObject = p;
                    break;
                }
            }
        }

        for (TemporaryObject otherObject : map.getAllObjects()) {
            if (otherObject.getShape() == null) continue;

            Area otherArea = new Area(otherObject.getShape());
            otherArea.intersect(entranceArea);

            if (otherArea.isEmpty()) continue;
            if (!otherObject.equals(building) && !otherObject.equals(parentRoadObject)) return true;

            // A significant collision was found
            if (isSignificantOverlap(otherArea, map)) return true;
        }

        return false;
    }

    private boolean isSignificantOverlap(Area intersectionArea, TemporaryMap map) {
        TemporaryObject dummy = new TemporaryIntersection(new ArrayList<>());
        List<TemporaryObject> polygons = ConvertTools.areaToTemporaryPassableShapes(intersectionArea, dummy, map);
        return !polygons.isEmpty();
    }
}
