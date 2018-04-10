package rvc.hib;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.hibernate.resource.transaction.spi.TransactionStatus;

import javax.persistence.*;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@MappedSuperclass
@Data
@Slf4j
public abstract class HibModel implements Serializable {

    public enum Status {
        ACTIVE, PENDING, INACTIVE, APPROVED, REJECTED, DELETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "created")
    public Integer created;

    @Column(name = "changed")
    public Integer changed;

    @Transient
    private transient Map<String, Object> cacheAttributes;

    @PrePersist
    @PreUpdate
    void pre() {
        if (this.created == null) {
            this.created = (int) Instant.now().getEpochSecond();
        }
        this.changed = (int) Instant.now().getEpochSecond();
        clearCache();
    }

    @PostRemove
    void post() {
        clearCache();
    }

    public void clearCache() {
        if (cacheAttributes == null) return;
        cacheAttributes.clear();
    }

    protected <T> T attribute(String attribute) {
        if (cacheAttributes == null) {
            cacheAttributes = new HashMap<>();
        }
        return (T) cacheAttributes.get(attribute);
    }

    protected <T> T attribute(String attribute, T model) {
        if (cacheAttributes == null) {
            cacheAttributes = new HashMap<>();
        }
        return (T) cacheAttributes.put(attribute, model);
    }

    protected <T> T cache(String key, Attribute attribute) {
        if (cacheAttributes == null) {
            cacheAttributes = new HashMap<>();
        }
        return (T) cacheAttributes.computeIfAbsent(key, s -> attribute.get());
    }

    @Transient
    public <T> T get(String field) {
        try {
            java.lang.reflect.Field fld = getClass().getField(field);
            fld.setAccessible(true);
            return (T) fld.get(this);
        } catch (NoSuchFieldException e) {
            try {
                return (T) BeanUtil.invoke(this, field);
            } catch (Exception e1) {
                log.error(className() + ".get(" + field + ")", e1.getMessage(), e1);
                throw new RuntimeException(e);
            }
        } catch (IllegalAccessException e) {
            log.error(className() + ".get(" + field + ")", e.getMessage(), e);
            return null;
        }
    }

    public void set(String field, Object value) {
        try {
            java.lang.reflect.Field fld = getClass().getField(field);
            fld.setAccessible(true);
            fld.set(this, value);
        } catch (NoSuchFieldException e) {
            try {
                BeanUtil.setValue(this, field, value);
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e1) {
                log.error(className() + ".set(" + field + ")", e1.getMessage(), e1);
                throw new RuntimeException(e);
            }
        } catch (IllegalAccessException e) {
            log.error(className() + ".set(" + field + ")", e.getMessage(), e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (this.getClass() != obj.getClass()) return false;
        HibModel o = (HibModel) obj;
        if (o.getId() == null && getId() == null) return this == o;
        return this.getId().equals(o.getId());
    }

    public <T extends HibModel> T saveOrUpdate() {
        return tx((session -> {
            session.saveOrUpdate(this);
            return this;
        }));
    }

    public <T extends HibModel> T save() {
        return tx((session -> {
            session.save(this);
            return this;
        }));
    }

    public <T extends HibModel> T update() {
        return tx((session -> {
            session.update(this);
            return this;
        }));
    }

    public <T extends HibModel> T delete() {
        return tx((session -> {
            session.delete(this);
            return this;
        }));
    }

    public <T extends HibModel> T persist() {
        return tx((session -> {
            session.persist(this);
            return this;
        }));
    }

    public <T extends HibModel> T merge() {
        return tx((session -> {
            session.merge(this);
            return this;
        }));
    }


    /*
     *  Example: user.update("lastLogin", $.now(), "lastIp", $.ip());
     */
    public void update(Object... params) {
        tx(
                (session) -> {
                    StringBuilder builder = new StringBuilder();
                    builder.append("update ").append(className()).append(" set ");
                    for (int i = 0; i < params.length; i = i + 2) {
                        Object o = params[i];
                        builder.append(o).append("=:").append(o);
                        if (i < params.length - 2)
                            builder.append(", ");
                    }
                    builder.append(" where id = :id");
                    org.hibernate.query.Query query = session.createQuery(builder.toString());
                    for (int i = 0; i < params.length; i += 2) {
                        Object o = params[i];
                        Object o2 = params[i + 1];
                        query.setParameter(o.toString(), o2);
                    }
                    query.setParameter("id", id);
                    return query.executeUpdate();
                }
        );
    }

    public static <T> T lazy(T lazy) {
        if (lazy instanceof HibernateProxy) {
            LazyInitializer lazyInitializer = ((HibernateProxy) lazy).getHibernateLazyInitializer();

            if (lazyInitializer.isUninitialized()) {
                try {
                    Hibernate.initialize(lazy);
                    return lazy;
                } catch (HibernateException ignored) {
                }

                if (lazyInitializer.getSession() == null) {
                    SharedSessionContractImplementor session = (SharedSessionContractImplementor) HibernateUtil.getSession();
                    Transaction tx = session.getTransaction();
                    if (tx.getStatus() != TransactionStatus.ACTIVE) {
                        tx.begin();
                    }
                    lazyInitializer.setSession(session);
                    lazyInitializer.initialize();
                    tx.commit();
                }
            }
        }

        return lazy;
    }

    public static Long lazyId(Object entity) {
        if (entity instanceof HibernateProxy) {
            LazyInitializer lazyInitializer = ((HibernateProxy) entity).getHibernateLazyInitializer();
            if (lazyInitializer.isUninitialized()) {
                return (Long) lazyInitializer.getIdentifier();
            }
        } else if (entity instanceof HibModel) {
            return ((HibModel) entity).getId();
        }
        return null;
    }

    private Class<? extends HibModel> clazz() {
        return getClass();
    }

    private String className() {
        return getClass().getSimpleName();
    }

    public <T extends HibModel> T byId(Long id) {

        if (id == null) return null;
        return tx((session) -> session.get(clazz(), id));
    }

    public <T extends HibModel> T load(Long id) {
        if (id == null) return null;
        return tx((session) -> session.load(clazz(), id));
    }

    public <T extends HibModel> T findFirst(String where, Object... params) {
        return tx(
                (session) -> {
                    org.hibernate.query.Query query = session.createQuery("from " + className() + " where " + where);
                    for (int i = 0; i < params.length; i++) {
                        query.setParameter(i, params[i]);
                    }
                    query.setMaxResults(1);
                    return query.uniqueResultOptional().orElse(null);
                }
        );
    }

    public <T extends HibModel> List<T> findAll() {
        return tx((session) -> session.createQuery("from " + className()).list());
    }

    public <T extends HibModel> List<T> findAll(String orderBy) {
        if (orderBy == null || orderBy.trim().length() == 0) return findAll();
        String orderBy2 = orderBy.toLowerCase().startsWith("order") ? orderBy : "order by " + orderBy;
        return tx((session) -> session.createQuery("from " + className() + " " + orderBy2).list());
    }

    public <T extends HibModel> List<T> find(String where, Object... params) {
        return tx(
                (session) -> {
                    org.hibernate.query.Query query = session.createQuery("from " + className() + " where " + where);
                    for (int i = 0; i < params.length; i++) {
                        query.setParameter(i, params[i]);
                    }
                    return query.list();
                });
    }

    public String deleteById(Long id) {
        return tx(session -> {
            Object o = session.load(clazz(), id);
            session.delete(o);
            return "deleted";
        });
    }

    public long count() {
        return tx(session -> session.createQuery("select count(*) from " + className()).uniqueResult());
    }

    public long count(String where, Object... params) {
        return tx((session) -> {
            org.hibernate.query.Query query = session.createQuery("select count(*) from " + className() + " where " + where);
            for (int i = 0; i < params.length; i++) {
                query.setParameter(i, params[i]);
            }
            return query.uniqueResult();
        });
    }

    public <T extends HibModel> List<T> where(String where, int limit, int offset, Object... params) {
        return tx(
                (session) -> {
                    org.hibernate.query.Query query = session.createQuery("from " + className() + " where " + where);
                    for (int i = 0; i < params.length; i++) {
                        query.setParameter(i, params[i]);
                    }
                    query.setFirstResult(offset);
                    query.setMaxResults(limit);
                    return query.list();
                });
    }

    public static org.hibernate.query.Query query(String query, Object... params) {
        org.hibernate.query.Query q = HibernateUtil.getSession().createQuery(query);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i, params[i]);
        }
        return q;
    }

    public static <T> T tx(Result r) {
        Session session = HibernateUtil.getSession();
        Transaction tx = session.getTransaction();
        T result;
        try {
            if (tx.getStatus() != TransactionStatus.ACTIVE) {
                tx.begin();
            }
            result = (T) r.execute(session);
            tx.commit();
        } catch (Exception e) {
            log.error("Error when calling tx method. ", e);
            tx.rollback();
            result = null;
        }
        return result;
    }

    public interface Result {
        Object execute(Session session);
    }

    public interface Attribute {
        Object get();
    }
}