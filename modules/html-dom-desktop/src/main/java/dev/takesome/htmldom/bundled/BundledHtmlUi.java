package dev.takesome.htmldom.bundled;

import dev.takesome.htmldom.desktop.Dom;
import dev.takesome.htmldom.logging.HtmlDomLog;
import dev.takesome.htmldom.logging.HtmlDomLogger;

import javax.swing.JDialog;
import javax.swing.WindowConstants;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Starts the bundled HtmlDom desktop showcase in an ownerless desktop dialog. */
public final class BundledHtmlUi {
    private static final String ROOT = "html-dom/bundled/";
    private static final HtmlDomLogger LOG = HtmlDomLog.logger(BundledHtmlUi.class);

    private BundledHtmlUi() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            LOG.info("HtmlDom desktop UI is ready, but the environment is headless.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JDialog window = new JDialog((java.awt.Frame) null, "HtmlDom Desktop HTML-like UI", false);
            window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            window.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    System.exit(0);
                }
            });
            window.setContentPane(Dom.classpath(ROOT + "showcase.ui.html")
                    .resourceBase(ROOT)
                    .script(ROOT + "showcase.lua")
                    .panel());
            window.setMinimumSize(new Dimension(1080, 720));
            window.setSize(1280, 860);
            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }

}
