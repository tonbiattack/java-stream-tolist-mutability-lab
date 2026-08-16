package jp.tonbiattack.debuglab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShipmentPlannerTest {

    private final ShipmentPlanner planner = new ShipmentPlanner();

    @Test
    void requestedInsurance_isAddedAfterNonShippableLinesAreFiltered() {
        List<ShipmentLine> requestedLines = List.of(
                new ShipmentLine("BOOK", 1, true),
                new ShipmentLine("CANCELLED-PEN", 1, false));

        List<ShipmentLine> actual = planner.buildShipmentLines(requestedLines, true);

        assertEquals(2, actual.size(), "発送対象の本体行と保険行を返すべき");
        assertEquals("BOOK", actual.getFirst().sku());
        assertEquals("SHIPPING-INSURANCE", actual.get(1).sku());
    }

    @Test
    void streamToList_resultIsNotStructurallyModifiable() {
        List<String> codes = List.of("BOOK", "PEN").stream().toList();

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> codes.add("ERASER"));

        System.out.printf("streamToListClass=%s, exception=%s%n",
                codes.getClass().getName(),
                exception.getClass().getName());
    }

    @Test
    void nonShippableLines_areRemovedWhenNoPostProcessingIsNeeded() {
        List<ShipmentLine> requestedLines = List.of(
                new ShipmentLine("BOOK", 1, true),
                new ShipmentLine("CANCELLED-PEN", 1, false));

        List<ShipmentLine> actual = planner.buildShipmentLines(requestedLines, false);

        assertEquals(1, actual.size());
        assertEquals("BOOK", actual.getFirst().sku());
        assertFalse(actual.contains(new ShipmentLine("CANCELLED-PEN", 1, false)));
        assertTrue(actual.getFirst().shippable());
    }
}
