package com.esgame.testmina;

import android.util.Log;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.filter.keepalive.KeepAliveFilter;
import org.apache.mina.filter.keepalive.KeepAliveMessageFactory;
import org.apache.mina.filter.keepalive.KeepAliveRequestTimeoutHandler;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

import java.net.InetSocketAddress;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;


public class SocketManager {

    class Params {

        //连接超时时间
        public static final int CONNECT_TIMEOUT = 30000;

        //长连接发送频率
        public static final int REQUEST_INTERVAL = 10;

        //应答超时
        public static final int REQUEST_TIMEOUT = 5;

        //IP
        public static final String HOSTNAME = "192.168.1.33";

        //端口号
        public static final int PORT = 1001;

        //心跳包发送
        public static final String SEND= "ping_send";

        //心跳包接受
        public static final String RECEIVE = "ping_receive";

    }

    public static final String LOG_TAG = "SocketManager";

    private static volatile SocketManager manager;

    private static NioSocketConnector connector;
    private static ConnectFuture connectFuture;
    private static IoSession ioSession;

    public  SocketManagerHandler mSocketManagerHandler;

    public static SocketManager getInstance() {
        if (manager==null) {
            synchronized (SocketManager.class) {
                manager = new SocketManager();
            }
        }
        return manager;
    }

    private SocketManager() {

        connector=new NioSocketConnector();

        connector.setConnectTimeoutMillis(Params.CONNECT_TIMEOUT); //设置连接超时

        connector.setHandler(this.clientSessionHandler());  //设置接收信息管理

        connector.getFilterChain().addLast("protocol", new ProtocolCodecFilter(new TextLineCodecFactory(Charset.forName("UTF-8")))); //设置过滤器（使用Mina提供的文本换行符编解码器）

        connector.getSessionConfig().setIdleTime(IdleStatus.BOTH_IDLE, Params.REQUEST_TIMEOUT); //读写通道5秒内无操作进入空闲状态

        connector.getSessionConfig().setReadBufferSize(2048);//设置读取数据的缓冲区大小

        connector.getSessionConfig().setKeepAlive(true);
        connector.getFilterChain().addLast("heartbeat", this.hearBeat());
    }

    /**
     * 连接
     * @return
     */
    public void startConnect() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (connector!=null && connector.isActive() &&
                        connectFuture!=null && connectFuture.isConnected() &&
                        ioSession!=null && ioSession.isConnected()) {
                    return;
                }
                try {
                    connectFuture = connector.connect(new InetSocketAddress(Params.HOSTNAME, Params.PORT));

                    connectFuture.awaitUninterruptibly();//等待是否连接成功，相当于是转异步执行为同步执行
                    ioSession = connectFuture.getSession();//连接成功后获取会话对象。如果没有上面的等待，由于connect()方法是异步的，session 可能会无法获取。

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d(LOG_TAG, "服务器与客户端连接失败");
                }
            }
        }).start();
    }

    /**
     * 关闭
     */
    public void close() {
        if(ioSession!=null && ioSession.isConnected()){
            ioSession.closeOnFlush();
        }
        if(connectFuture!=null && connectFuture.isConnected()) {
            connectFuture.cancel();
        }
        if(connector!=null && !connector.isDisposed()) {
            connector.dispose();
        }
    }

    /**
     * 发送
     * @param message
     * @return
     */
    public boolean sendMessage(String message){
        if (ioSession==null || !ioSession.isConnected()){
            return false;
        }

        WriteFuture writeFuture=ioSession.write(message + "\r\n");
        if (writeFuture==null){
            return false;
        }
        writeFuture.awaitUninterruptibly();
        if (writeFuture.isWritten()){
            return true;
        }
        else {
            return false;
        }
    }

    private KeepAliveFilter hearBeat() {

        KeepAliveMessageFactory heartBeatFactory = new KeepAliveMessageFactory() {
            @Override
            public boolean isRequest(IoSession ioSession, Object o) {
                if (o instanceof String && o.equals(Params.SEND)) {
                    return true;
                }
                return false;
            }

            @Override
            public boolean isResponse(IoSession ioSession, Object o) {
                if (o instanceof String && o.equals(Params.RECEIVE)) {
                    return true;
                }
                return false;
            }

            @Override
            public Object getRequest(IoSession ioSession) {
                Log.d(LOG_TAG, "发送心跳");
                return Params.SEND;
            }

            @Override
            public Object getResponse(IoSession ioSession, Object o) {
                return null;
            }
        };

        KeepAliveRequestTimeoutHandler heartBeatHandler = new KeepAliveRequestTimeoutHandler() {
            @Override
            public void keepAliveRequestTimedOut(KeepAliveFilter filter, IoSession session) throws Exception {
                Log.d(LOG_TAG, "心跳超时");
            }
        };

        KeepAliveFilter heartBeat = new KeepAliveFilter(heartBeatFactory, IdleStatus.BOTH_IDLE, heartBeatHandler);

        //是否回发
        heartBeat.setForwardEvent(true);
        //心跳发送频率
        heartBeat.setRequestInterval(Params.REQUEST_INTERVAL);

        return heartBeat;
    }

    private IoHandler clientSessionHandler() {

        return new IoHandlerAdapter() {
            @Override
            public void sessionCreated(IoSession session) throws Exception {

                Log.d(LOG_TAG, "服务器与客户端创建连接");
            }

            @Override
            public void sessionOpened(IoSession session) throws Exception {

                Log.d(LOG_TAG, "服务器与客户端连接打开");
            }

            @Override
            public void sessionClosed(IoSession session) throws Exception {

                Log.d(LOG_TAG, "服务器与客户端断开连接");
            }

            @Override
            public void exceptionCaught(IoSession session, Throwable cause) throws Exception{

                Log.d(LOG_TAG, "服务器发送异常:"+cause);
            }

            @Override
            public void messageReceived(IoSession session, Object message) throws Exception {

                if (mSocketManagerHandler != null) {
                    mSocketManagerHandler.messageReceived(message.toString());
                }

                Log.d(LOG_TAG, "客户端接受消息成功"+message.toString());
            }

            @Override
            public void messageSent(IoSession session, Object message) throws Exception {

                Log.d(LOG_TAG, "客户端发送消息成功"+message.toString());
            }

            @Override
            public void sessionIdle(IoSession session, IdleStatus status) throws Exception {

                Log.d(LOG_TAG, "客户端进入空闲状态");
            }
        };
    }

    public interface SocketManagerHandler {
        void messageReceived(String text);
//        void happenException();
    }
}
