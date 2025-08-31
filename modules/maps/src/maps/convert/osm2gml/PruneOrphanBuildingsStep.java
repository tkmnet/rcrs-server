package maps.convert.osm2gml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This step removes any buildings that are not connected to the main road network.
 * Such buildings are considered "orphaned" and are invalid in the simulation.
 */
public class PruneOrphanBuildingsStep extends BaseModificationStep {

    public PruneOrphanBuildingsStep(TemporaryMap map) {
        super(map);
    }

    @Override
    public String getDescription() {
        return "Pruning orphan buildings";
    }

    @Override
    protected void step() {
        Collection<TemporaryBuilding> initialBuildings = new ArrayList<>(map.getBuildings());

        if (initialBuildings.isEmpty()) {
            setStatus("No buildings to prune.");
            return;
        }

        List<TemporaryBuilding> buildingsToRemove = new ArrayList<>();

        for (TemporaryBuilding building : initialBuildings) {
            boolean isConnected = false;
            for (DirectedEdge de : building.getEdges()) {
                Edge edge = de.getEdge();
                if (1 < map.getAttachedObjects(edge).size()) isConnected = true;
            }
            if (isConnected) continue;
            buildingsToRemove.add(building);
        }

        for (TemporaryBuilding building : buildingsToRemove) {
            map.removeBuilding(building);
        }
        map.resynchronizeStateFromObjects();

        visualizeDifference(initialBuildings, map.getBuildings(), "Orphaned Building Pruning Results");
    }
}
