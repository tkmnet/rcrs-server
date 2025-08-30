package maps.convert.osm2gml;

import maps.convert.ConvertStep;
import rescuecore2.misc.gui.ShapeDebugFrame;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.util.*;
import java.util.List;

/**
 * This step cleans up overlaps between buildings and roads by subtracting
 * the building shapes from the road shapes.
 */
public class CleanOverlapsStep extends ConvertStep {
    private final TemporaryMap map;

    public CleanOverlapsStep(TemporaryMap map) {
        this.map = map;
    }


    @Override
    public String getDescription() {
        return "Cleaning building/road overlaps";
    }

    @Override
    protected void step() {
        Collection<TemporaryBuilding> buildings = map.getBuildings();
        Collection<TemporaryRoad> initialRoads = new ArrayList<>(map.getRoads());

        if (buildings.isEmpty() || initialRoads.isEmpty()) {
            setStatus("No buildings or roads to process.");
            return;
        }

        int cleanedCount = 0;
        List<TemporaryRoad> newRoads = new ArrayList<>();
        List<TemporaryRoad> roadsToRemove = new ArrayList<>();

        for (TemporaryRoad road : initialRoads) {
            Area roadArea = new Area(road.getShape());
            boolean modified = false;
            for (TemporaryBuilding building : buildings) {
                // Quick check using bounds for performance
                if (!road.getBounds().intersects(building.getBounds())) {
                    continue;
                }

                Area buildingArea = new Area(building.getShape());
                Area intersection = new Area(roadArea);
                intersection.intersect(buildingArea);

                if (!intersection.isEmpty()) {
                    // Subtract the building's shape from the road
                    roadArea.subtract(buildingArea);
                    modified = true;
                }
            }
            if (modified) {
                roadsToRemove.add(road);
                newRoads.addAll(areaToTemporaryRoads(roadArea));
                cleanedCount++;
            }
        }

        // Apply the changes to the map
        for (TemporaryRoad road : roadsToRemove) {
            map.removeRoad(road);
        }
        for (TemporaryRoad road : newRoads) {
            map.addRoad(road);
        }

        // After making major geometric changes, we must resynchronize
        // the map's entire low-level state with the new high-level objects.
        map.resynchronizeStateFromObjects();

        setStatus("Cleaned " + cleanedCount + " roads that overlapped with buildings.");

        visualizeDifference(initialRoads, map.getRoads(), buildings, "Building/Road Overlap Cleanup");
    }

    private List<TemporaryRoad> areaToTemporaryRoads(Area area) {
        List<TemporaryRoad> result = new ArrayList<>();
        PathIterator it = area.getPathIterator(null);
        double[] coords = new double[6];
        List<DirectedEdge> currentPath = new ArrayList<>();
        Node firstNode = null;
        Node lastNode = null;

        while (!it.isDone()) {
            int type = it.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    // If a path was being built, close it first (for multi-part areas)
                    if (firstNode != null && !currentPath.isEmpty()) {
                        if (!lastNode.equals(firstNode)) {
                            currentPath.add(map.getDirectedEdge(lastNode, firstNode));
                        }
                        if (2 < currentPath.size()) {
                            result.add(new TemporaryRoad(currentPath));
                        }
                    }
                    currentPath = new ArrayList<>();
                    firstNode = map.getNode(coords[0], coords[1]);
                    lastNode = firstNode;
                    break;
                case PathIterator.SEG_LINETO:
                    Node nextNode = map.getNode(coords[0], coords[1]);
                    if (lastNode != null && !lastNode.equals(nextNode)) {
                        currentPath.add(map.getDirectedEdge(lastNode, nextNode));
                    }
                    lastNode = nextNode;
                    break;
                case PathIterator.SEG_CLOSE:
                    if (firstNode != null && lastNode != null && !lastNode.equals(firstNode)) {
                        currentPath.add(map.getDirectedEdge(lastNode, firstNode));
                    }
                    if (2 < currentPath.size()) {
                        result.add(new TemporaryRoad(currentPath));
                    }
                    currentPath = new ArrayList<>();
                    firstNode = null;
                    lastNode = null;
                    break;
            }
            it.next();
        }
        return result;
    }

    private void visualizeDifference(Collection<TemporaryRoad> beforeRoads, Collection<TemporaryRoad> afterRoads, Collection<TemporaryBuilding> buildings, String title) {
        List<ShapeDebugFrame.ShapeInfo> shapes = new ArrayList<>();
        Set<TemporaryRoad> beforeRoadSet = new HashSet<>(beforeRoads);
        Set<TemporaryRoad> afterRoadSet = new HashSet<>(afterRoads);

        final Color colourForOld   = new Color(229, 115, 115, 128); // Transparent red
        final Color colourForNew   = new Color(129, 199, 132, 128); // Transparent green
        final Color buildingColour = new Color(173, 216, 230, 128); // Transparent light

        // Unchanged roads (draw in a natural colour)
        Set<TemporaryRoad> keptRoads = new HashSet<>(beforeRoadSet);
        keptRoads.retainAll(afterRoadSet);
        for (TemporaryRoad road : keptRoads) {
            shapes.add(new TemporaryObjectInfo(road, "Kept Road", Color.DARK_GRAY, Color.LIGHT_GRAY));
        }

        // Remove roads (the original, overlapping versions)
        Set<TemporaryRoad> removeRoads = new HashSet<>(beforeRoadSet);
        removeRoads.removeAll(afterRoadSet);
        for (TemporaryRoad road : removeRoads) {
            shapes.add(new TemporaryObjectInfo(road, "Removed Road (Original)", Color.RED.darker(), colourForOld));
        }

        // Added roads (the new, cleaned versions)
        Set<TemporaryRoad> addedRoads = new HashSet<>(afterRoadSet);
        addedRoads.removeAll(beforeRoadSet);
        for (TemporaryRoad road : addedRoads) {
            shapes.add(new TemporaryObjectInfo(road, "Added Road (Cleaned)", Color.GREEN.darker(), colourForNew));
        }

        // Draw buildings on top for context
        for (TemporaryBuilding building : buildings) {
            shapes.add(new TemporaryObjectInfo(building, "Building", Color.BLUE.darker(), buildingColour));
        }

        debug.show(title, shapes);
    }
}
