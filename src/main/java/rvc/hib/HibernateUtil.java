package rvc.hib;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.jpa.event.spi.JpaIntegrator;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Properties;

/**
 * @author nurmuhammad
 */

@Slf4j
public class HibernateUtil {

    private static String PATH = "hibernate.file";
    private static SessionFactory sessionFactory;

    public static SessionFactory getSessionFactory() {
        if (sessionFactory != null) {
            return sessionFactory;
        }

        String hibernatePath = Config.get(PATH);

        Properties hibernateProperties = new Properties();

        log.info("----------Hibernate path:" + hibernatePath + "-------------");
        try (FileInputStream f = new FileInputStream(hibernatePath)) {
            hibernateProperties.load(f);
        } catch (Exception e) {
            log.error("Hibernate configuration load: ", e);
        }

        BootstrapServiceRegistryBuilder registryBuilder = new BootstrapServiceRegistryBuilder();
        registryBuilder.applyIntegrator(new JpaIntegrator());
        StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder(registryBuilder.build())
                .applySettings(hibernateProperties);

        final StandardServiceRegistry registry = builder.build();

        MetadataSources sources = new MetadataSources(registry);
        String s = (String) hibernateProperties.get("hibernate.annotated.classes");
        Arrays.stream(s.split(",")).forEach(sources::addAnnotatedClassName);
        Metadata metadata = sources.getMetadataBuilder()
                .applyImplicitNamingStrategy(ImplicitNamingStrategyJpaCompliantImpl.INSTANCE)
                .build();

        sessionFactory = metadata.buildSessionFactory();

        return sessionFactory;
    }

    public static Session getSession() {
        SessionFactory sessionFactory = getSessionFactory();
//        sessionFactory.getStatistics() //TODO enable-disable statistics by admin
        Session session = null;
        try {
//            logger.info("Trying to get current session object.");
            if (sessionFactory.getCurrentSession() != null) {
                session = sessionFactory.getCurrentSession();
            }
        } catch (Exception e) {
            log.warn("no current session context. ", e);
            try {
                log.info("Trying to open session object.");
                session = sessionFactory.openSession();
            } catch (HibernateException e1) {
                log.error("Error when opening session.", e);
                e1.printStackTrace();
            }
        }
        assert session != null;
        return session;
    }
}
