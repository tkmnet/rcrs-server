package maps.convert.osm2gml;

import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;

import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SpatialGrid {

    private final Map<GridPoint, Set<Edge>> grid;
    private final double cellWidth;
    private final double cellHeight;
    private final double minX;
    private final double minY;

    public SpatialGrid(Rectangle2D bounds, double cellSize) {
        this.minX = bounds.getMinX();
        this.minY = bounds.getMinY();
        this.cellWidth = cellSize;
        this.cellHeight = cellSize;
        this.grid = new HashMap<>();
    }

    private record GridPoint(int x, int y) {}

    private record CellBounds(int minX, int minY, int maxX, int maxY) {}

    private CellBounds getEdgeCellBounds(Edge edge) {
        Line2D line = edge.getLine();
        Point2D start = line.getOrigin();
        Point2D end = line.getEndPoint();

        int startX = getXCell(start.getX());
        int startY = getYCell(start.getY());
        int endX = getXCell(end.getX());
        int endY = getYCell(end.getY());

        return new CellBounds(
            Math.min(startX, endX),
            Math.min(startY, endY),
            Math.max(startX, endX),
            Math.max(startY, endY)
        );
    }

    public void add(Edge edge) {
        CellBounds bounds = getEdgeCellBounds(edge);

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                addToCell(x, y, edge);
            }
        }
    }

    public Set<Edge> getNearbyEdges(Edge edge) {
        Set<Edge> nearby = new HashSet<>();
        CellBounds bounds = getEdgeCellBounds(edge);

        // Search the 3x3 area around the edge's bounding box.
        for (int x = bounds.minX() - 1; x <= bounds.maxX() + 1; x++) {
            for (int y = bounds.minY() - 1; y <= bounds.maxY() + 1; y++) {
                Set<Edge> cellContent = getFromCell(x, y);
                if (cellContent != null) {
                    nearby.addAll(cellContent);
                }
            }
        }
        return nearby;
    }

    private int getXCell(double x) {
        return (int) Math.floor((x - minX) / cellWidth);
    }

    private int getYCell(double y) {
        return (int) Math.floor((y - minY) / cellHeight);
    }

    private void addToCell(int x, int y, Edge edge) {
        GridPoint key = new GridPoint(x, y);
        grid.computeIfAbsent(key, k -> new HashSet<>()).add(edge);
    }

    private Set<Edge> getFromCell(int x, int y) {
        return grid.get(new GridPoint(x, y));
    }

}
