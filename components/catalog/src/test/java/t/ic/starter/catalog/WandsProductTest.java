package t.ic.starter.catalog;

import io.ic.starter.catalog.WandsProduct;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WandsProductTest {

    @Test
    void searchTextConcatenatesNameDescriptionFeatures() {
        var product = new WandsProduct(1, "solid wood bed", "Beds", "Furniture", "a sturdy frame", "material:wood");
        assertEquals("solid wood bed a sturdy frame material:wood", product.searchText());
    }

    @Test
    void searchTextHandlesNullFields() {
        var product = new WandsProduct(1, "lamp", null, null, null, null);
        assertEquals("lamp", product.searchText());
    }

    @Test
    void searchTextCollapsesWhitespace() {
        var product = new WandsProduct(1, "  desk ", null, null, "  wide   oak ", null);
        assertEquals("desk wide oak", product.searchText());
    }
}
