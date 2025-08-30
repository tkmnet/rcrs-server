package maps.convert.osm2gml;

import maps.convert.ConvertStep;
import maps.gml.debug.OSMNodeShapeInfo;
import maps.osm.OSMNode;
import rescuecore2.log.Logger;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.gui.ShapeDebugFrame;

import java.awt.*;
import java.util.*;
import java.util.List;

public class MergeIntersectionStep extends ConvertStep {
    private final TemporaryMap map;
    private final double mergeDistance;

    public MergeIntersectionStep(TemporaryMap map) {
        super();
        this.map = map;

        // Merge intersection within 10 meters of each other.
        this.mergeDistance = ConvertTools.sizeOf1Metre(map.getOSMMap()) * 10.0;
    }

    @Override
    public String getDescription() {
        return "Merging nearby intersections";
    }

    @Override
    protected void step() {
        List<OSMIntersectionInfo> originalIntersections = new ArrayList<>(map.getOSMIntersectionInfo());
        Collection<OSMRoadInfo> originalRoads = new ArrayList<>(map.getOSMRoadInfo());

        if (originalIntersections.size() < 2) {
            setStatus("Not enough intersections to merge.");
            return;
        }

        // Visualize the state BEFORE merging
        visualizeNetwork(originalIntersections, originalRoads, "Before Merging", Color.BLUE);

        // Group nearby intersections.
        List<Set<OSMIntersectionInfo>> groupedIntersections = groupIntersections(originalIntersections);

        // Create new centroid intersections and create a map from old to new.
        List<OSMIntersectionInfo> newIntersections = new ArrayList<>();
        Map<OSMIntersectionInfo, OSMIntersectionInfo> oldToNewMap = new HashMap<>();

        for (Set<OSMIntersectionInfo> group : groupedIntersections) {
            OSMIntersectionInfo newCentroid = createCentroidIntersection(group);
            newIntersections.add(newCentroid);
            for (OSMIntersectionInfo old : group) {
                oldToNewMap.put(old, newCentroid);
            }
        }

        // Clear the internal state of all new intersections before rebuilding.
        for (OSMIntersectionInfo intersection : newIntersections) {
            intersection.clearRoadSegments();
        }

        // Re-link all roads to the new centroid intersections.
        Set<OSMRoadInfo> finalRoads = new HashSet<>();
        for (OSMRoadInfo road : originalRoads) {
            OSMIntersectionInfo oldStart = map.getRoadStartIntersection(road);
            OSMIntersectionInfo oldEnd = map.getRoadEndIntersection(road);

            OSMIntersectionInfo newStart = oldToNewMap.get(oldStart);
            OSMIntersectionInfo newEnd = oldToNewMap.get(oldEnd);

            // Discard invalid roads or roads that were internal to a merged group.
            if (newStart == null || newEnd == null || newStart.getUnderlyingNode().equals(newEnd.getUnderlyingNode())) {
                continue;
            }

            road.setFrom(newStart.getUnderlyingNode());
            road.setTo(newEnd.getUnderlyingNode());

            newStart.addRoadSegment(road);
            newEnd.addRoadSegment(road);

            finalRoads.add(road);
        }

        // Update the map with the simplified graph.
        map.setOSMInfo(newIntersections, new ArrayList<>(finalRoads), map.getOSMBuildingInfo());

        // Log, and visualize the state AFTER merging
        String status = "Merged " + originalIntersections.size() + " intersections into " + newIntersections.size();
        setStatus(status);
        Logger.info(status);
        visualizeNetwork(newIntersections, finalRoads, "After Merging", Color.RED);
    }

    private List<Set<OSMIntersectionInfo>> groupIntersections(List<OSMIntersectionInfo> intersections) {
        List<Set<OSMIntersectionInfo>> groups = new ArrayList<>();
        Set<OSMIntersectionInfo> visited = new HashSet<>();
        for (OSMIntersectionInfo startNode : intersections) {
            if (visited.contains(startNode)) continue;

            Set<OSMIntersectionInfo> currentGroup = new HashSet<>();
            Queue<OSMIntersectionInfo> queue = new ArrayDeque<>();
            queue.add(startNode);
            visited.add(startNode);

            while (!queue.isEmpty()) {
                OSMIntersectionInfo current = queue.poll();
                currentGroup.add(current);
                for (OSMIntersectionInfo neighbour : intersections) {
                    if (!visited.contains(neighbour) && isNear(current.getLocation(), neighbour.getLocation())) {
                        visited.add(neighbour);
                        queue.add(neighbour);
                    }
                }
            }
            groups.add(currentGroup);
        }
        return groups;
    }

    private OSMIntersectionInfo createCentroidIntersection(Set<OSMIntersectionInfo> group) {
        if (group.isEmpty()) return null;
        if (group.size() == 1) return group.iterator().next();

        double totalLon = 0, totalLat = 0;
        long representativeId = -1;

        for (OSMIntersectionInfo i : group) {
            Point2D loc = i.getLocation();
            totalLon += loc.getX();
            totalLat += loc.getY();
            if (representativeId == -1) representativeId = i.getUnderlyingNode().getID();
        }

        double centroidLon = totalLon / group.size();
        double centroidLat = totalLat / group.size();

        OSMNode centroidNode = new OSMNode(-Math.abs(representativeId), centroidLat, centroidLon);
        return new OSMIntersectionInfo(centroidNode);
    }

    private boolean isNear(Point2D p1, Point2D p2) {
        if (p1 == null || p2 == null) return false;
        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        double distSq = dx * dx + dy * dy;
        return distSq <= mergeDistance * mergeDistance;
    }

    private void visualizeNetwork(Collection<OSMIntersectionInfo> intersections, Collection<OSMRoadInfo> roads, String title, Color colour) {
        List<ShapeDebugFrame.ShapeInfo> shapes = new ArrayList<>();
        Set<OSMNode> intersectionNodes = new HashSet<>();
        for (OSMIntersectionInfo intersection : intersections) {
            intersectionNodes.add(intersection.getUnderlyingNode());
        }
        for (OSMNode node : intersectionNodes) {
            shapes.add(new OSMNodeShapeInfo(node, "Intersection", colour, true));
        }
        for (OSMRoadInfo road : roads) {
            if (road.getFrom() == null || road.getTo() == null) continue;
            Line2D line = new Line2D(
                    new Point2D(road.getFrom().getLongitude(), road.getFrom().getLatitude()),
                    new Point2D(road.getTo().getLongitude(), road.getTo().getLatitude())
            );
            shapes.add(new ShapeDebugFrame.Line2DShapeInfo(line, "Road", colour, false, false));
        }
        debug.show(title, shapes);
    }
}
