package app.livosphere.buildlogic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class SetContentInventoryTest {
    @Test public void collectsNestedQualifiedReferencesButIgnoresCommentsAndEscapedLiterals() throws Exception {
        Path xml = Files.createTempFile("set-content-", ".xml");
        try {
            Files.writeString(xml, "<resources><!-- @drawable/commented -->"
                    + "<declare-styleable name=\"Sentinel\"><attr name=\"preview\">@pkg.example:drawable/preview</attr></declare-styleable>"
                    + "<item type=\"string\" name=\"framework\">@android:string/ok</item>"
                    + "<string name=\"literal\">\\@drawable/not_a_ref</string></resources>");
            SetContentInventory inventory = new SetContentInventory();
            inventory.resource(xml, "values/references.xml");
            assertTrue(inventory.references.contains("pkg.example:drawable/preview"));
            assertTrue(inventory.references.contains("android:string/ok"));
            assertFalse(inventory.references.contains("drawable/commented"));
            assertFalse(inventory.references.contains("drawable/not_a_ref"));
        } finally {
            Files.deleteIfExists(xml);
        }
    }
}
