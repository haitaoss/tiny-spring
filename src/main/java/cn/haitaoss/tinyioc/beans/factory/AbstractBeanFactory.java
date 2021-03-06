package cn.haitaoss.tinyioc.beans.factory;

import cn.haitaoss.tinyioc.beans.BeanDefinition;
import cn.haitaoss.tinyioc.beans.BeanPostProcessor;
import cn.haitaoss.tinyioc.beans.BeanReference;
import cn.haitaoss.tinyioc.beans.ConstructorArgument;
import cn.haitaoss.tinyioc.beans.converter.ConverterFactory;
import cn.haitaoss.tinyioc.beans.lifecycle.InitializingBean;
import cn.haitaoss.tinyioc.context.AbstractApplicationContext;
import cn.haitaoss.tinyioc.context.ApplicationContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2021-04-17 11:48
 *
 */
public abstract class AbstractBeanFactory implements BeanFactory {
    private final List<String> beanDefinitionNames = new ArrayList<>();  // 记录所有bean的名字，用于提前创建bean还有就是收集beanpostProcessor
    private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(); // 容器
    private final List<BeanPostProcessor> beanPostProcessors = new ArrayList<>(); // 记录容器中所有的beanPostProcessor
    // 三级缓存 解决循环引用缺少代理的问题
    protected Map<String, Object> secondCache = new HashMap<>();
    protected Map<String, Object> thirdCache = new HashMap<>();
    protected Map<String, Object> firstCache = new HashMap<>();
    // 解析基本数据类型
    protected ConverterFactory converterFactory = new ConverterFactory();
    protected AbstractApplicationContext context;

    public AbstractApplicationContext getContext() {
        return context;
    }

    public void setContext(AbstractApplicationContext context) {
        this.context = context;
    }

    public ConverterFactory getConverterFactory() {
        return converterFactory;
    }

    public Map<String, BeanDefinition> getBeanDefinitionMap() {
        return beanDefinitionMap;
    }

    public Map<String, Object> getSecondCache() {
        return secondCache;
    }

    public Map<String, Object> getThirdCache() {
        return thirdCache;
    }

    public Map<String, Object> getFirstCache() {
        return firstCache;
    }

    /**
     * 创建单例bean
     * @author haitao.chen
     * email
     * date 2021/4/17 3:14 下午
     * @param name
     * @return java.lang.Object
     */
    @Override
    public Object getBean(String name) throws Exception {
        BeanDefinition beanDefinition = beanDefinitionMap.get(name);

        ApplicationContext context = this.getContext();
        // 当前容器中找不到从父容器中获取bean
        while (beanDefinition == null && context.getParent() != null) {
            ApplicationContext parent = context.getParent();
            Object object = parent.getBean(name);
            if (object != null) {
                return object;
            } else {
                context = parent;
            }
        }

        if (beanDefinition == null) {
            throw new IllegalArgumentException("No bean named " + name + " is defined");
        }

        Object bean = beanDefinition.getBean(); // null
        // 如果bean为null 或者不是单例bean
        if (bean == null || !beanDefinition.isSingleton()) {
            bean = doCreateBean(name, beanDefinition);
            bean = initializeBean(bean, name); // 代理操作
            // 将操作过的bean重新设置到beanDefinition中
            beanDefinition.setBean(bean); // 修改beandefinition 里面的bean
        }
        return bean;
    }

    protected Object initializeBean(Object bean, String name) throws Exception {
        //  初始化前操作
        for (BeanPostProcessor beanPostProcessor : beanPostProcessors) { // beanPostProcessors.size() = 0
            bean = beanPostProcessor.postProcessBeforeInitialization(bean, name);
        }
        // bean 的初始化操作
        try {
            Method method = bean.getClass().getMethod("init_method", null);
            method.invoke(bean, null);
        } catch (Exception e) {

        }

        // 初始化后的操作
        for (BeanPostProcessor beanPostProcessor : beanPostProcessors) {
            bean = beanPostProcessor.postProcessAfterInitialization(bean, name);
        }

        // 初始化完成放入三级缓存
        if (thirdCache.containsKey(name)) {
            firstCache.put(name, bean);
        }

        return bean;
    }

    private Object createBeanInstance(BeanDefinition beanDefinition) throws Exception {
        if (beanDefinition.getConstructorArgument().isEmpty()) {
            return beanDefinition.getBeanClass().newInstance();
        } else {
            List<ConstructorArgument.ValueHolder> valueHolders = beanDefinition.getConstructorArgument().getArgumentValues();
            Class clazz = Class.forName(beanDefinition.getBeanClassName());
            Constructor[] cons = clazz.getConstructors();
            for (Constructor constructor : cons) {
                // 这里省去判断参数类型
                if (constructor.getParameterCount() == valueHolders.size()) {
                    Object[] params = new Object[valueHolders.size()];
                    for (int i = 0; i < params.length; i++) {
                        params[i] = valueHolders.get(i).getValue();
                        if (params[i] instanceof BeanReference) {
                            BeanReference ref = (BeanReference) params[i];
                            String refName = ref.getName();
                            if (thirdCache.containsKey(refName) && !firstCache.containsKey(refName)) {
                                throw new IllegalAccessException("构造函数循环依赖" + refName);
                            } else {
                                params[i] = getBean(refName);
                            }
                        }
                    }
                    return constructor.newInstance(params);
                }
            }
        }
        return null;
    }

    public void registerBeanDefinition(String name, BeanDefinition beanDefinition) throws Exception {
        beanDefinitionMap.put(name, beanDefinition);
        beanDefinitionNames.add(name);
    }

    public void preInstantiateSingletons() throws Exception {
        for (String beanName : beanDefinitionNames) {
            getBean(beanName);
        }
    }

    protected Object doCreateBean(String name, BeanDefinition beanDefinition) throws Exception {
        Object bean = createBeanInstance(beanDefinition);
        // thirdCache中放置的全是空构造方法构造出的实例
        thirdCache.put(name, bean);
        beanDefinition.setBean(bean);
        applyPropertyValues(name, bean, beanDefinition);
        // 解析类里面的注解
        injectAnnotation(name, bean, beanDefinition);
        // 生命周期
        if (bean instanceof InitializingBean) {
            ((InitializingBean) bean).afterPropertiesSet();
        }
        return bean;
    }

    protected void applyPropertyValues(String name, Object bean, BeanDefinition beanDefinition) throws Exception {

    }

    protected void injectAnnotation(String name, Object bean, BeanDefinition beanDefinition) throws Exception {

    }

    public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) throws Exception {
        this.beanPostProcessors.add(beanPostProcessor);
    }

    /**
     * 从容器中获取BeanPostProcessor 的子类或者子接口
     * @author haitao.chen
     * email
     * date 2021/4/17 9:28 下午
     * @param type
     * @return java.util.List
     */
    public List getBeansForType(Class type) throws Exception {
        // BeanPostProcessor.class
        List beans = new ArrayList<Object>();
        for (String beanDefinitionName : beanDefinitionNames) {
            // class2是不是class1的子类或者子接口
            // class1.isAssignableFrom(class2) AspectJAwareAdvisorAutoProxyCreator
            if (type.isAssignableFrom(beanDefinitionMap.get(beanDefinitionName).getBeanClass())) {
                // 创建出这个bean
                beans.add(getBean(beanDefinitionName));
            }
        }
        // 收集父容器里面
        ApplicationContext context = this.context;
        ApplicationContext parent = null;
        while (context.getParent() != null) {
            parent = context.getParent();
            beans.addAll(parent.getBeanFactory().getBeansForType(type));
            context = parent;
        }
        return beans;
    }
}
