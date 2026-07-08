package t.ic.starter.websupport;

import io.ic.starter.websupport.FormData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FormDataTest {
    @Test
    void testToString() {
        FormData formData = new FormData();
        formData.add("key1", "value1");
        formData.add("key2", "value2");

        String expected = "key1=value1&key2=value2";
        String actual = formData.toString();

        assertEquals(expected, actual);
    }
}
