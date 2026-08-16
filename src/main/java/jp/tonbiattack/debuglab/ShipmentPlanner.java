package jp.tonbiattack.debuglab;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds shipment lines from order lines.
 *
 * <p>The insurance post-processing step structurally modifies its working list.
 * The collector therefore explicitly creates an {@link ArrayList}, rather than
 * relying on an unspecified implementation or on {@code Stream.toList()}.</p>
 */
public final class ShipmentPlanner {

    public List<ShipmentLine> buildShipmentLines(
            List<ShipmentLine> requestedLines,
            boolean insuranceRequested) {
        List<ShipmentLine> shipmentLines = requestedLines.stream()
                .filter(ShipmentLine::shippable)
                .collect(Collectors.toCollection(ArrayList::new));

        System.out.printf(
                "beforeAdd: class=%s, lines=%s, insuranceRequested=%s%n",
                shipmentLines.getClass().getName(),
                shipmentLines,
                insuranceRequested);

        if (insuranceRequested) {
            shipmentLines.add(ShipmentLine.insurance());
        }
        return shipmentLines;
    }
}
