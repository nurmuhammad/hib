package rvc.hib;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class BeanUtil {

    // start reflection methods invokes
    public static Object invoke(Object data, String field) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method meth;
        Object obj = data;
        String[] flds = field.split("[.]");
        for (String fld : flds) {
            meth = obj.getClass().getMethod(methodGet(fld));
            obj = meth.invoke(obj);
        }
        return obj;
    }

    public static void setValue(Object data, String field, Object value)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        setValue(data, field, value, value.getClass());
    }

    public static void setValue(Object data, String field, Object value, Class clazz)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method meth;
        Object obj = data;
        String[] flds = field.split("[.]");
        for (int i = 0; i < flds.length - 1; i++) {
            meth = obj.getClass().getMethod(methodGet(flds[i]));
            obj = meth.invoke(obj);
        }
        meth = obj.getClass().getMethod(methodSet(flds[flds.length - 1]), clazz);
        meth.invoke(obj, value);
    }

    public static String methodGet(String field) {
        return "get" + field.substring(0, 1).toUpperCase() + field.substring(1);
    }

    public static String methodSet(String field) {
        return "set" + field.substring(0, 1).toUpperCase() + field.substring(1);
    }
    // end reflection methods invokes

}
