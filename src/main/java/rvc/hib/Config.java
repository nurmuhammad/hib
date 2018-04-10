package rvc.hib;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author nurmuhammad
 */

@Slf4j
public class Config {

    static String FILE = "config.properties";
    public static Config instance = new Config();
    private Properties properties;

    private Config() {
        properties = new Properties();
        reload();
    }

    public void reload() {
        log.info("Init Config class file");
        properties.clear();

        String config = System.getProperty("rvc.configuration");
        try (final FileInputStream in = new FileInputStream(config)) {
            properties.load(in);
            log.info("Reading successfully.");
        } catch (Exception e) {
            log.info("1: Can't read the config.properties file. Method: System.getProperty()");
        }

        if (properties.isEmpty()) {
            String path = jarDir() + File.separator + FILE;
            try (final FileInputStream in = new FileInputStream(path)) {
                properties.load(in);
                log.info("Reading successfully.");
            } catch (Exception e) {
                log.info("2: Can't read the config.properties file. Method: jarDir() " + path);
            }
        }

        if (properties.isEmpty()) {
            try (final InputStream stream = getClass().getClassLoader().getResourceAsStream(FILE)) {
                properties.load(stream);
                log.info("Reading successfully.");
            } catch (Exception e) {
                log.info("3: Can't read the config.properties file. Method: getResourceAsStream()");
            }
        }

        if (properties.isEmpty()) {
            try (final InputStream stream = getClass().getResourceAsStream("/config.properties")) {
                properties.load(stream);
                log.info("Reading successfully.");
            } catch (Exception e) {
                log.error("4: Can't read the config.properties file. Method: getResourceAsStream(/" + FILE + ")", e);
            }
        }

        if (!properties.isEmpty()) {
            log.info("Reading from config.properties stream is successfully.");
        }
    }

    public static String jarDir() {
        String jar = Config.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        return new File(jar).getParent();
    }

    public static Config getInstance() {
        return instance;
    }

    public static String get(String key) {
        return getInstance().properties.getProperty(key);
    }

    public static String get(String key, String defaultValue) {
        return getInstance().properties.getProperty(key, defaultValue);
    }

    public static int get(String key, int defaultValue) {
        try {
            String toret = getInstance().properties.getProperty(key, String.valueOf(defaultValue));

            return Integer.valueOf(toret);
        } catch (NumberFormatException e) {
            log.warn("Getting key by '" + key + "', throws exception. ", e);
            return defaultValue;
        }
    }

}
