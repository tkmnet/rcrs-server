package maps.convert.osm2gml;

import maps.convert.ConvertStep;
import maps.gml.debug.CustomStrokeLineInfo;
import maps.gml.debug.OSMNodeShapeInfo;
import maps.osm.OSMNode;
import rescuecore2.log.Logger;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.misc.gui.ShapeDebugFrame;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * This step simplifies the road network by removing pseudo-nodes.
 */
public class RemovePseudoNodesStep extends ConvertStep {
    private final TemporaryMap map;

    // Angle threshold in degrees. If the angle is less than this, consider it a straight line.
    private static final double STRAIGHT_LINE_ANGLE_THRESHOLD = 10.0;

    public RemovePseudoNodesStep(TemporaryMap map) {
        super();
        this.map = map;
    }

    @Override
    public String getDescription() {
        return "Removing pseudo-nodes from straight roads";
    }

    @Override
    protected void step() {
        int totalRemoved = 0;
        int pass = 0;

        // Store the initial state for the final visualization
        List<OSMIntersectionInfo> initialIntersections = new ArrayList<>(map.getOSMIntersectionInfo());
        List<OSMRoadInfo> initialRoads = new ArrayList<>(map.getOSMRoadInfo());

        while (true) {
            pass++;
            int removedInThisPass = processPseudoNodes();
            if (removedInThisPass == 0) {
                // No nodes were removed in a full pass, so the process has converged
                break;
            }
            totalRemoved += removedInThisPass;
            Logger.info("Pass " + pass + ": Removed " + removedInThisPass + " pseudo-nodes. Total removed " + totalRemoved);

            // Safety break to prevent potential infinite loops in complex scenarios
            if (20 < pass) {
                Logger.error("Exceeded 20 passes in RemovePseudoNodesStep. Aborting.");
                break;
            }
        }

        // Visualize the difference between the initial and final state
        visualizeNetworkDifference(initialIntersections, initialRoads, map.getOSMIntersectionInfo(), map.getOSMRoadInfo(), "Pseudo-Nodes Removal Results");

        setStatus("Removed " + totalRemoved + " pseudo-nodes.");
    }

    private int processPseudoNodes() {
        // We must work with copies as we will be modifying the map's lists
        List<OSMIntersectionInfo> intersections = new ArrayList<>(map.getOSMIntersectionInfo());
        List<OSMRoadInfo> roads = new ArrayList<>(map.getOSMRoadInfo());
        int removedCount = 0;

        // Find all pseudo-nodes in the current graph
        List<OSMIntersectionInfo> pseudoNodes = new ArrayList<>();
        for (OSMIntersectionInfo intersection : intersections) {
            if (getConnectedRoads(intersection, roads).size() == 2) {
                pseudoNodes.add(intersection);
            }
        }

        if (pseudoNodes.isEmpty()) {
            return 0;
        }

        for (OSMIntersectionInfo intersection : pseudoNodes) {
            // The intersection might have been removed already as part of another merge
            if (!intersections.contains(intersection)) {
                continue;
            }

            List<OSMRoadInfo> connectedRoads = getConnectedRoads(intersection, roads);
            if (connectedRoads.size() != 2) continue; // State changed, skip

            OSMRoadInfo road1 = connectedRoads.get(0);
            OSMRoadInfo road2 = connectedRoads.get(1);

            if (isStraight(intersection, road1, road2)) {
                OSMNode nodeToRemove = intersection.getUnderlyingNode();
                OSMNode node1 = road1.getFrom().equals(nodeToRemove) ? road1.getTo() : road1.getFrom();
                OSMNode node2 = road2.getFrom().equals(nodeToRemove) ? road2.getTo() : road2.getFrom();

                // Create a new road connecting the outer nodes
                OSMRoadInfo newRoad = new OSMRoadInfo(node1, node2);

                // Remove old entities and add the new one
                roads.remove(road1);
                roads.remove(road2);
                roads.add(newRoad);
                intersections.remove(intersection);

                removedCount++;
            }
        }

        if (0 < removedCount) {
            map.setOSMInfo(intersections, roads, map.getOSMBuildingInfo());
        }

        return removedCount;
    }

    private List<OSMRoadInfo> getConnectedRoads(OSMIntersectionInfo intersection, List<OSMRoadInfo> allRoads) {
        List<OSMRoadInfo> result = new ArrayList<>();
        OSMNode intersectionNode = intersection.getUnderlyingNode();
        for (OSMRoadInfo road : allRoads) {
            if (road.getFrom().equals(intersectionNode) || road.getTo().equals(intersectionNode)) {
                result.add(road);
            }
        }
        return result;
    }

    private boolean isStraight(OSMIntersectionInfo intersection, OSMRoadInfo road1, OSMRoadInfo road2) {
        OSMNode centre = intersection.getUnderlyingNode();
        OSMNode other1 = road1.getFrom().equals(centre) ? road1.getTo() : road1.getFrom();
        OSMNode other2 = road2.getFrom().equals(centre) ? road2.getTo() : road2.getFrom();

        Point2D pCentre = new Point2D(centre.getLongitude(), centre.getLatitude());
        Point2D p1 = new Point2D(other1.getLongitude(), other1.getLatitude());
        Point2D p2 = new Point2D(other2.getLongitude(), other2.getLatitude());

        Vector2D v1 = p1.minus(pCentre);
        Vector2D v2 = p2.minus(pCentre);

        double angle = GeometryTools2D.getAngleBetweenVectors(v1, v2);

        return Math.abs(180 - angle) < STRAIGHT_LINE_ANGLE_THRESHOLD;
    }

    private void visualizeNetworkDifference(Collection<OSMIntersectionInfo> beforeIntersections, Collection<OSMRoadInfo> beforeRoads, Collection<OSMIntersectionInfo> afterIntersections, Collection<OSMRoadInfo> afterRoads, String title) {
        List<ShapeDebugFrame.ShapeInfo> shapes = new ArrayList<>();
        Set<OSMRoadInfo> beforeRoadSet = new HashSet<>(beforeRoads);
        Set<OSMRoadInfo> afterRoadSet = new HashSet<>(afterRoads);
        Set<OSMIntersectionInfo> beforeIntersectionSet = new HashSet<>(beforeIntersections);
        Set<OSMIntersectionInfo> afterIntersectionSet = new HashSet<>(afterIntersections);

        final Color colourForOld = Color.decode("#1976d2");
        final Color colourForNew = Color.decode("#E57373");

        // Kept items (draw in gray)
        Set<OSMRoadInfo> keptRoads = new HashSet<>(beforeRoads);
        Set<OSMIntersectionInfo> keptIntersections = new HashSet<>(beforeIntersections);
        keptRoads.retainAll(afterRoadSet);
        keptIntersections.retainAll(afterIntersectionSet);
        drawRoads(shapes, keptRoads, "Kept Road", Color.BLACK);
        drawIntersections(shapes, keptIntersections, "Kept Intersections", Color.BLACK, true);

        // Removed items (draw in blue, dashed)
        Set<OSMRoadInfo> removedRoads = new HashSet<>(beforeRoadSet);
        Set<OSMIntersectionInfo> removedIntersections = new HashSet<>(beforeIntersectionSet);
        removedRoads.removeAll(afterRoadSet);
        removedIntersections.removeAll(afterIntersectionSet);
        drawRoads(shapes, removedRoads, "Removed Road", colourForOld, new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f}, 0.0f));
        drawIntersections(shapes, removedIntersections, "Removed Intersection", colourForOld, true);

        // Added items (draw in red, thick)
        Set<OSMRoadInfo> addedRoads = new HashSet<>(afterRoads);
        Set<OSMIntersectionInfo> addedIntersections = new HashSet<>(afterIntersectionSet);
        addedRoads.removeAll(beforeRoadSet);
        addedIntersections.removeAll(beforeIntersections);
        drawRoads(shapes, addedRoads, "Added Road", colourForNew, new BasicStroke(2.0f));
        drawIntersections(shapes, addedIntersections, "Added Intersection", colourForNew, true);

        debug.show(title, shapes);
    }

    private void drawRoads(List<ShapeDebugFrame.ShapeInfo> shapes, Collection<OSMRoadInfo> roads, String name, Color colour, Stroke stroke) {
        for (OSMRoadInfo road : roads) {
            if (road.getFrom() == null || road.getTo() == null) continue;
            Line2D line = new Line2D(
                    new Point2D(road.getFrom().getLongitude(), road.getFrom().getLatitude()),
                    new Point2D(road.getTo().getLongitude(), road.getTo().getLatitude())
            );
            shapes.add(new CustomStrokeLineInfo(line, name, colour, stroke));
        }
    }

    private void drawIntersections(List<ShapeDebugFrame.ShapeInfo> shapes, Collection<OSMIntersectionInfo> intersections, String name, Color colour, boolean square) {
        for (OSMIntersectionInfo intersection : intersections) {
            shapes.add(new OSMNodeShapeInfo(intersection.getUnderlyingNode(), name, colour, square));
        }
    }

    private void drawRoads(List<ShapeDebugFrame.ShapeInfo> shapes, Collection<OSMRoadInfo> roads, String name, Color color) {
        drawRoads(shapes, roads, name, color, new BasicStroke(1.0f));
    }
}
