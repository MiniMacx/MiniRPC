package org.tustcs.minirpc.client;

import org.tustcs.minirpc.client.proxy.ObjectProxy;
import org.tustcs.minirpc.registry.ServiceDiscovery;

import java.lang.reflect.Proxy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * rpc客户端调用类
 */
public class RpcClient {

    private ServiceDiscovery serviceDiscovery;
    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(16, 16,
            600L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(65536));

    public RpcClient(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new ObjectProxy<T>(interfaceClass)
        );
    }

    public static void submit(Runnable task) {
        threadPoolExecutor.submit(task);
    }

    public void stop() {
        threadPoolExecutor.shutdown();
        serviceDiscovery.stop();
        ConnectManage.getInstance().stop();
    }
}

