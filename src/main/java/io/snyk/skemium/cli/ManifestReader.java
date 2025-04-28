package io.snyk.skemium.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.IVersionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;

/**
 * Provide easy access to the key/value attributes, present in the final Jar's MANIFEST.MF resource file.
 * <p>
 * It loads the content of the manifest at construction time: accessor methods just return values from memory.
 * <p>
 * This also implements {@link IVersionProvider} interface, so it can be used with {@link picocli.CommandLine.Command}
 * to return the correct version.
 */
public class ManifestReader implements IVersionProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ManifestReader.class);

    private static final Map<String, String> MANIFEST_VALUES = new HashMap<>();
    private static final String MANIFEST_RESOURCE_PATH = "META-INF/MANIFEST.MF";

    public static final String MANIFEST_KEY_PRJ_NAME = "Project-Name";
    public static final String MANIFEST_KEY_PRJ_VER = "Project-Version";

    public static final ManifestReader SINGLETON = new ManifestReader();

    private synchronized static void initManifestValues() {
        // Only initialize this map once
        if (MANIFEST_VALUES.isEmpty()) {
            LOG.debug("Reading internal Manifest ({})", MANIFEST_RESOURCE_PATH);
            try {
                final Enumeration<URL> resources = CommandLine.class.getClassLoader().getResources(MANIFEST_RESOURCE_PATH);
                while (resources.hasMoreElements()) {
                    final URL url = resources.nextElement();
                    try (final InputStream is = url.openStream()) {
                        final Manifest manifest = new Manifest(is);
                        manifest.getMainAttributes().forEach((attName, attValue) -> {
                            LOG.trace("  Attribute: {} = {}", attName, attValue);
                            MANIFEST_VALUES.put(attName.toString(), attValue.toString());
                        });
                    }
                }
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * A public Constructor is provided for Picocli as implementor of the {@link IVersionProvider} interface.
     * Normal users should just use {@link #SINGLETON} instead.
     */
    public ManifestReader() {
        initManifestValues();
    }

    /**
     * Get value of an attribute in the Manifest.
     *
     * @param manifestAttributeKey Key of attribute in the Manifest.
     * @return Manifest Attribute Value, or {@code null} if not present.
     */
    public String getAttribute(final String manifestAttributeKey) {
        return MANIFEST_VALUES.get(manifestAttributeKey);
    }

    /**
     * Get keys of the attributes found in Manifest.
     *
     * @return {@link Set} of attribute keys.
     */
    public Set<String> getAttributeKeys() {
        return MANIFEST_VALUES.keySet();
    }

    public String[] getVersion() {
        return new String[]{getAttribute(MANIFEST_KEY_PRJ_VER)};
    }
}
