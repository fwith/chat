package lexek.wschat.proxy.twitch;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import lexek.wschat.chat.*;
import lexek.wschat.db.model.UserAuthDto;
import lexek.wschat.security.AuthenticationManager;
import lexek.wschat.services.MessageConsumerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class OutboundMessageHandler implements MessageConsumerService {
    private static final long FIVE_MINUTES = TimeUnit.MINUTES.toMillis(5);
    private static final AttributeKey<Long> lastMessageAttrKey = AttributeKey.valueOf("__last_message");
    private final Logger logger = LoggerFactory.getLogger(OutboundMessageHandler.class);
    private final Map<Long, Boolean> checkedUsers = new HashMap<>();
    private final Map<String, Channel> connections;
    private final Bootstrap outboundBootstrap;
    private final String channel;
    private final AuthenticationManager authenticationManager;
    private final Room room;

    public OutboundMessageHandler(Map<String, Channel> connections,
                                  String channel,
                                  AuthenticationManager authenticationManager,
                                  EventLoopGroup eventLoopGroup,
                                  Room room) {
        this.connections = connections;
        this.channel = channel;
        this.authenticationManager = authenticationManager;
        this.room = room;

        this.outboundBootstrap = new Bootstrap();
        this.outboundBootstrap.group(eventLoopGroup);
        if (Epoll.isAvailable()) {
            this.outboundBootstrap.channel(EpollSocketChannel.class);
        } else {
            this.outboundBootstrap.channel(NioSocketChannel.class);
        }
        this.outboundBootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        this.outboundBootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        final ChannelHandler stringEncoder = new StringEncoder(CharsetUtil.UTF_8);
        final ChannelHandler stringDecoder = new StringDecoder(CharsetUtil.UTF_8);
        final ChannelHandler handler = new Handler();
        this.outboundBootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                ChannelPipeline pipeline = channel.pipeline();
                pipeline.addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
                pipeline.addLast(stringEncoder);
                pipeline.addLast(stringDecoder);
                pipeline.addLast(handler);
            }
        });
        eventLoopGroup.scheduleAtFixedRate(new ConnectionCleanupTask(), 10, 5, TimeUnit.MINUTES);
    }

    @Override
    public void consume(Connection connection, Message message, BroadcastFilter filter) {
        if (filter.getType() == BroadcastFilter.Type.ROOM && filter.getData() == room &&
                message.getType() == MessageType.MSG && message.get(Message.Keys.ROOM).equals("#main")) {
            UserCredentials userCredentials = null;
            if (needsFetchingConnectionData(connection.getUser())) {
                userCredentials = fetchConnectionDataForUser(connection.getUser());
            }

            if (userCredentials != null) {
                String id = userCredentials.getId();
                Channel channel = connections.get(id.toLowerCase());
                if (channel == null || !channel.isActive()) {
                    try {
                        channel = createConnection(id, userCredentials.getToken());
                    } catch (InterruptedException e) {
                        logger.warn("", e);
                    }
                }
                if (channel != null) {
                    channel.attr(lastMessageAttrKey).set(System.currentTimeMillis());
                    channel.writeAndFlush("PRIVMSG #" + this.channel + " :" + message.get(Message.Keys.TEXT) + "\r\n");
                }
            }
        }
    }

    private boolean needsFetchingConnectionData(User user) {
        Boolean tmp = checkedUsers.get(user.getId());
        return user.getRole().compareTo(GlobalRole.USER) >= 0 && (tmp == null || tmp);
    }

    private UserCredentials fetchConnectionDataForUser(User user) {
        UserCredentials userCredentials = null;
        logger.debug("fetching connection data for user {}", user.getName());
        boolean r = false;
        UserAuthDto auth = authenticationManager.getAuthDataForUser(user.getId(), "twitch.tv");
        if (auth != null) {
            String token = auth.getAuthenticationKey();
            String extName = auth.getAuthenticationName();
            logger.debug("fetched data for user {} with ext name {}", user.getName(), extName, token);
            if (token != null && extName != null) {
                userCredentials = new UserCredentials(extName, token);
            }
            r = true;
        }
        checkedUsers.put(user.getId(), r);
        return userCredentials;
    }

    private Channel createConnection(String id, String token) throws InterruptedException {
        Channel channel;
        logger.debug("creating connection for {}", id);
        ChannelFuture f = outboundBootstrap.connect("irc.twitch.tv", 6667).sync();
        channel = f.channel();
        channel.write("TWITCHCLIENT 2\r\n");
        channel.write("PASS oauth:" + token + "\r\n");
        channel.write("NICK " + id + "\r\n");
        channel.write("JOIN #" + channel + "\r\n");
        channel.flush();
        connections.put(id.toLowerCase(), channel);
        logger.debug("connection created");
        return channel;
    }

    public int getConnectionCount() {
        return this.connections.size();
    }

    private static class UserCredentials {
        private String id;
        private String token;

        private UserCredentials(String id, String token) {
            this.id = id;
            this.token = token;
        }

        public String getId() {
            return id;
        }

        public String getToken() {
            return token;
        }
    }

    @Sharable
    private class Handler extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            logger.trace("received message {}", msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause instanceof IOException) {
                logger.debug("exception", cause);
            } else {
                logger.warn("exception", cause);
            }
        }
    }

    private class ConnectionCleanupTask implements Runnable {
        @Override
        public void run() {
            try {
                logger.debug("starting cleanup");
                for (Map.Entry<String, Channel> entry : connections.entrySet()) {
                    Channel channel = entry.getValue();
                    Long lastMessage = channel.attr(lastMessageAttrKey).get();
                    if (System.currentTimeMillis() - lastMessage > FIVE_MINUTES) {
                        channel.disconnect();
                        connections.remove(entry.getKey());
                        logger.debug("connection released for {}", entry.getKey());
                    } else if (!channel.isActive()) {
                        connections.remove(entry.getKey());
                        logger.debug("connection released for {}", entry.getKey());
                    }
                }
                logger.debug("cleanup complete");
            } catch (Exception e) {
                logger.error("exception while clean up", e);
            }
        }
    }
}
