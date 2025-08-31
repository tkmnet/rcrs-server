package maps.convert.osm2gml;

import java.awt.geom.Area;
import java.util.*;

import static maps.convert.osm2gml.ConvertTools.areaToTemporaryPassableShapes;

public class MergePassableShapesStep extends BaseModificationStep {

    public MergePassableShapesStep(TemporaryMap map) {
        super(map);
    }

    @Override
    public String getDescription() {
        return "Merging overlapping passable shapes";
    }

    @Override
    protected void step() {
        Collection<TemporaryObject> initialPassableShapes = new ArrayList<>(map.getAllPassableShapes());

        // Group roads that are geometrically connected (i.e., overlapping).
        List<List<TemporaryObject>> groups = findOverlappingRoadGroups(initialPassableShapes);

        List<TemporaryObject> passableShapesToAdd = new ArrayList<>();
        List<TemporaryObject> passableShapesToRemove = new ArrayList<>();

        for (List<TemporaryObject> group : groups) {
            // We only merge if a group contains more than one passable shape.
            if (1 < group.size()) {
                Area combinedArea = new Area();
                for (TemporaryObject object : group) {
                    if (object.getShape() != null) {
                        combinedArea.add(new Area(object.getShape()));
                    }
                }

                // Convert the combined area back to new passable shapes. ignoring any holes.
                List<TemporaryObject> mergedPassableShapes = areaToTemporaryPassableShapes(combinedArea, group.get(0), map);

                if (!mergedPassableShapes.isEmpty()) {
                    passableShapesToRemove.addAll(group);
                    passableShapesToAdd.addAll(mergedPassableShapes);
                }
            }
        }

        if (passableShapesToRemove.isEmpty()) {
            setStatus("No overlapping passable shapes found to merge.");
            visualizeDifference(initialPassableShapes, initialPassableShapes, "Passable Shapes Merging Results - No Changes");
            return;
        }

        // Apply changes to the map
        for (TemporaryObject object : passableShapesToRemove) {
            map.removeTemporaryObject(object);
        }
        for (TemporaryObject object : passableShapesToAdd) {
            map.addTemporaryObject(object);
        }

        // After these major changes, a full resynchronization is required.
        map.resynchronizeStateFromObjects();

        setStatus("Merged " + passableShapesToRemove.size() + " old passable shapes into " + passableShapesToAdd.size() + " new passable shapes.");
        visualizeDifference(initialPassableShapes, map.getAllPassableShapes(), "Passable Shapes Merging Results");
    }

    private List<List<TemporaryObject>> findOverlappingRoadGroups(Collection<TemporaryObject> passableShapes) {
        List<List<TemporaryObject>> allGroups = new ArrayList<>();
        Set<TemporaryObject> visited = new HashSet<>();
        List<TemporaryObject> objectList = new ArrayList<>(passableShapes);

        for (int i = 0; i < objectList.size(); i++) {
            TemporaryObject startObject = objectList.get(i);
            if (visited.contains(startObject)) continue;

            List<TemporaryObject> currentGroup = new ArrayList<>();
            Queue<TemporaryObject> queue = new ArrayDeque<>();

            queue.add(startObject);
            visited.add(startObject);

            while (!queue.isEmpty()) {
                TemporaryObject current = queue.poll();
                currentGroup.add(current);

                for (int j = i; j < objectList.size(); j++) {
                    TemporaryObject neighbour = objectList.get(j);
                    if (!visited.contains(neighbour) && !current.equals(neighbour)) {
                        if (current.getBounds().intersects(neighbour.getBounds())) {
                            Area intersection = new Area(current.getShape());
                            intersection.intersect(new Area(neighbour.getShape()));
                            if (!intersection.isEmpty()) {
                                visited.add(neighbour);
                                queue.add(neighbour);
                            }
                        }
                    }
                }
            }
            allGroups.add(currentGroup);
        }
        return allGroups;
    }
}
