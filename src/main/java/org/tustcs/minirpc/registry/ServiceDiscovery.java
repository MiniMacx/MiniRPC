package org.tustcs.minirpc.registry;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tustcs.minirpc.client.ConnectManage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * zooKeeper 客户端服务发现操作类
 */
public class ServiceDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscovery.class);

    private CountDownLatch latch = new CountDownLatch(1);

    private volatile List<String> dataList = new ArrayList<>();

    private String registryAddress;
    private ZooKeeper zookeeper;

    public ServiceDiscovery(String registryAddress) {
        this.registryAddress = registryAddress;
        zookeeper = connectServer();
        if (zookeeper != null) {
            watchNode(zookeeper);
        }
    }

    private ZooKeeper connectServer() {
        ZooKeeper zk = null;
        try {
            zk = new ZooKeeper(registryAddress, Constant.ZK_SESSION_TIMEOUT, event -> {
                if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                    latch.countDown();
                }
            });
            latch.await();
        } catch (IOException | InterruptedException e) {
            logger.error("", e);
        }
        return zk;
    }

    private void watchNode(final ZooKeeper zk) {
        try {
            List<String> nodeList = zk.getChildren(Constant.ZK_REGISTRY_PATH,
                    event -> {
                        if (event.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
                            watchNode(zk);
                        }
                    });
            List<String> dataList = new ArrayList<>();
            for (String node : nodeList) {
                byte[] bytes = zk.getData(Constant.ZK_REGISTRY_PATH + "/" + node,
                        false, null);
                dataList.add(new String(bytes));
            }
            this.dataList = dataList;
            updateconnectedserver();
        } catch (KeeperException | InterruptedException e) {
            logger.error(e.getMessage());
        }
    }

    private void updateconnectedserver() {
        ConnectManage.getInstance().updateConnectedServer(this.dataList);
    }

    public void stop() {
        if (zookeeper != null) {
            try {
                zookeeper.close();
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }
        }
    }
}
