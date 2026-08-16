package jp.tonbiattack.debuglab;

/**
 * A requested line that may be included in a shipment.
 */
public record ShipmentLine(String sku, int quantity, boolean shippable) {

    public static ShipmentLine insurance() {
        return new ShipmentLine("SHIPPING-INSURANCE", 1, true);
    }
}
