package org.tustcs.minirpc.app;


import org.apache.thrift.TException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.tustcs.minirpc.client.DemoService;
import org.tustcs.minirpc.client.HelloService;
import org.tustcs.minirpc.client.RpcClient;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:client-spring.xml")
public class ServiceTest {

    @Autowired
    private RpcClient rpcClient;

    @Test
    public void helloTest1() {
        HelloService helloService = RpcClient.create(HelloService.class);
        String result = helloService.hello("World");
        Assert.assertEquals("Hello! World", result);
    }

    @Test
    public void demoTest() throws TException {
        DemoService.Iface demoService = RpcClient.create(DemoService.Iface.class);
        String result = demoService.sayHello("World");
        System.out.println(result);
    }


//    @After
//    public void setTear() {
//        if (rpcClient != null) {
//            rpcClient.stop();
//        }
//    }

}
