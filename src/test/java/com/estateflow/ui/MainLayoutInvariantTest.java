package com.estateflow.ui;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the shell and floating-assistant bounds that determine scene-graph mouse picking. */
class MainLayoutInvariantTest {
    @Test
    void mainShellUsesIndependentScrollableAreasAndNonPickingOverlays() throws Exception {
        Document main = load("/fxml/main.fxml");
        Element content = byFxId(main, "contentScroll");
        assertEquals("true", content.getAttribute("fitToWidth"));
        assertEquals("false", content.getAttribute("fitToHeight"));
        assertEquals("NEVER", content.getAttribute("hbarPolicy"));
        assertEquals("AS_NEEDED", content.getAttribute("vbarPolicy"));
        assertEquals(2, main.getElementsByTagName("ScrollPane").getLength(),
                "The shell should have only its main viewport and independent sidebar viewport.");
        assertEquals("false", byFxId(main, "assistantLayer").getAttribute("pickOnBounds"));
        assertEquals("true", byFxId(main, "toastBox").getAttribute("mouseTransparent"));
    }

    @Test
    void assistantRootStaysWithinItsCompactHitTestArea() throws Exception {
        Document widget = load("/fxml/assistant-widget.fxml");
        Element dock = byFxId(widget, "dock");
        assertEquals("false", dock.getAttribute("pickOnBounds"));
        assertFiniteBound(dock, "maxWidth", 420);
        assertFiniteBound(dock, "maxHeight", 620);
        assertEquals("false", byFxId(widget, "dockContent").getAttribute("pickOnBounds"));
        assertEquals("false", byFxId(widget, "launcherWrap").getAttribute("pickOnBounds"));
        assertEquals("true", byFxId(widget, "notificationDot").getAttribute("mouseTransparent"));
    }

    private static void assertFiniteBound(Element node, String attribute, double maximum) {
        String value = node.getAttribute(attribute);
        assertDoesNotThrow(() -> Double.parseDouble(value), attribute + " must be a finite numeric bound");
        assertTrue(Double.parseDouble(value) <= maximum, attribute + " must not grow to the viewport size");
    }

    private static Document load(String resource) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        try (InputStream in = MainLayoutInvariantTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "Missing FXML resource " + resource);
            return factory.newDocumentBuilder().parse(in);
        }
    }

    private static Element byFxId(Document document, String id) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (id.equals(element.getAttribute("fx:id"))) return element;
        }
        fail("FXML element with fx:id=" + id + " was not found");
        return null;
    }
}
