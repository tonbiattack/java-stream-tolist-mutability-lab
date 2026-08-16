package jp.tonbiattack.debuglab;

import java.util.List;

/**
 * Builds shipment lines from order lines.
 *
 * <p>BUG: this method filters with {@code Stream.toList()} and then adds an
 * insurance line. Since the returned list is unmodifiable, the add operation
 * throws {@link UnsupportedOperationException} when insurance is requested.</p>
 */
public final class ShipmentPlanner {

    public List<ShipmentLine> buildShipmentLines(
            List<ShipmentLine> requestedLines,
            boolean insuranceRequested) {
        List<ShipmentLine> shipmentLines = requestedLines.stream()
                .filter(ShipmentLine::shippable)
                .toList();

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
