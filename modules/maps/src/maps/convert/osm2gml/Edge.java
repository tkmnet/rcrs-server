package maps.convert.osm2gml;

import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;

/**
   An edge. An edge is a line between two nodes.
 */
public class Edge extends ManagedObject {
    private Node start;
    private Node end;
    private Line2D line;

    /**
       Construct a new Edge.
       @param id The ID of this object.
       @param start The start node.
       @param end The end node.
     */
    public Edge(long id, Node start, Node end) {
        super(id);
        this.start = start;
        this.end = end;
        line = new Line2D(start.getCoordinates(), end.getCoordinates());
    }

    /**
       Get the start node.
       @return The start node.
    */
    public Node getStart() {
        return start;
    }

    /**
       Get the end node.
       @return The end node.
    */
    public Node getEnd() {
        return end;
    }

    /**
       Get the line represented by this edge.
       @return The line.
    */
    public Line2D getLine() {
        return line;
    }

    /**
     * Calculate the midpoint of this edge.
     * @return A new Point2D representing the midpoint.
     */
    public Point2D getMidPoint() {
        Point2D p1 = start.getCoordinates();
        Point2D p2 = end.getCoordinates();
        return new Point2D((p1.getX() + p2.getX()) / 2.0, (p1.getY() + p2.getY()) / 2.0);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Edge ");
        result.append(getID());
        result.append(" from ");
        result.append(start);
        result.append(" to ");
        result.append(end);
        return result.toString();
    }
}
