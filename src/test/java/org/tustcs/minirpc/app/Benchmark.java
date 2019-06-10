package org.tustcs.minirpc.app;


import org.tustcs.minirpc.client.HelloService;
import org.tustcs.minirpc.client.RpcClient;
import org.tustcs.minirpc.registry.ServiceDiscovery;

public class Benchmark {

    public static void main(String[] args) throws InterruptedException {

        ServiceDiscovery serviceDiscovery = new ServiceDiscovery("127.0.0.1:2181");
        final RpcClient rpcClient = new RpcClient(serviceDiscovery);

        int threadNum = 20;
        final int requestNum = 10000;
        Thread[] threads = new Thread[threadNum];

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < threadNum; ++i) {
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < requestNum; i++) {
                        final HelloService syncClient = rpcClient.create(HelloService.class);
                        String result = syncClient.hello(Integer.toString(i));
                        if (!result.equals("Hello! " + i))
                            System.out.print("error = " + result);
                    }
                }
            });
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long timeCost = (System.currentTimeMillis() - startTime);
        String msg = String.format("total:%sms, req/s=%s", timeCost, ((double) (requestNum * threadNum)) / timeCost * 1000);
        System.out.println(msg);

        rpcClient.stop();
    }
}
