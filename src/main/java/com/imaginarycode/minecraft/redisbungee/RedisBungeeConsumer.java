/**
 * Copyright © 2013 tuxed <write@imaginarycode.com>
 * This work is free. You can redistribute it and/or modify it under the
 * terms of the Do What The Fuck You Want To Public License, Version 2,
 * as published by Sam Hocevar. See http://www.wtfpl.net/ for more details.
 */
package com.imaginarycode.minecraft.redisbungee;

import com.imaginarycode.minecraft.redisbungee.consumerevents.ConsumerEvent;
import com.imaginarycode.minecraft.redisbungee.consumerevents.PlayerChangedServerConsumerEvent;
import com.imaginarycode.minecraft.redisbungee.consumerevents.PlayerLoggedInConsumerEvent;
import com.imaginarycode.minecraft.redisbungee.consumerevents.PlayerLoggedOffConsumerEvent;
import lombok.RequiredArgsConstructor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@RequiredArgsConstructor
public class RedisBungeeConsumer implements Runnable {
    private final RedisBungee plugin;
    private final BlockingQueue<ConsumerEvent> consumerQueue = new LinkedBlockingQueue<>();
    private boolean stopped = false;

    @Override
    public void run() {
        Jedis jedis = plugin.getPool().getResource();
        try {
            while (!stopped) {
                ConsumerEvent event;
                try {
                    event = consumerQueue.take();
                } catch (InterruptedException e) {
                    continue;
                }
                handle(event, jedis);
            }
            for (ConsumerEvent event : consumerQueue)
                handle(event, jedis);
            consumerQueue.clear();
        } finally {
            plugin.getPool().returnResource(jedis);
        }
    }

    private void handle(ConsumerEvent event, Jedis jedis) {
        if (event instanceof PlayerLoggedInConsumerEvent) {
            PlayerLoggedInConsumerEvent event1 = (PlayerLoggedInConsumerEvent) event;
            Pipeline pipeline = jedis.pipelined();
            pipeline.sadd("server:" + RedisBungee.getApi().getServerId() + ":usersOnline", event1.getPlayer().getUniqueId().toString());
            pipeline.hset("player:" + event1.getPlayer().getUniqueId().toString(), "online", "0");
            pipeline.hset("player:" + event1.getPlayer().getUniqueId().toString(), "ip", event1.getPlayer().getAddress().getAddress().getHostAddress());
            pipeline.hset("player:" + event1.getPlayer().getUniqueId().toString(), "name", event1.getPlayer().getName());
            pipeline.hset("uuids", event1.getPlayer().getName().toLowerCase(), event1.getPlayer().getUniqueId().toString());
            pipeline.sync();
        } else if (event instanceof PlayerLoggedOffConsumerEvent) {
            PlayerLoggedOffConsumerEvent event1 = (PlayerLoggedOffConsumerEvent) event;
            Pipeline pipeline = jedis.pipelined();
            jedis.hset("player:" + event1.getPlayer().getUniqueId().toString(), "online", String.valueOf(System.currentTimeMillis()));
            RedisUtil.cleanUpPlayer(event1.getPlayer().getUniqueId().toString(), pipeline);
            pipeline.sync();
        } else if (event instanceof PlayerChangedServerConsumerEvent) {
            PlayerChangedServerConsumerEvent event1 = (PlayerChangedServerConsumerEvent) event;
            // No use in pipelining this
            jedis.hset("player:" + event1.getPlayer().getUniqueId().toString(), "server", event1.getNewServer().getName());
        }
    }

    public void queue(ConsumerEvent event) {
        if (!stopped)
            consumerQueue.add(event);
    }

    public void stop() {
        stopped = true;
        while (!consumerQueue.isEmpty()) ;
    }
}
