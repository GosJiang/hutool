package cn.hutool.socket.nio;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.thread.ThreadFactoryBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * NIO客户端
 *
 * @author looly
 * @since 4.4.5
 */
public abstract class NioClient implements Closeable {

    private Selector selector;
    private SocketChannel channel;
    private ExecutorService executorService;

    /**
     * 构造
     *
     * @param host 服务器地址
     * @param port 端口
     */
    public NioClient(String host, int port) {
        init(new InetSocketAddress(host, port));
    }

    /**
     * 构造
     *
     * @param address 服务器地址
     */
    public NioClient(InetSocketAddress address) {
        init(address);
    }

    /**
     * 初始化
     *
     * @param address 地址和端口
     * @return this
     */
    public NioClient init(InetSocketAddress address) {
        try {
            //创建一个SocketChannel对象，配置成非阻塞模式
            this.channel = SocketChannel.open();
            channel.configureBlocking(false);

            //创建一个选择器，并把SocketChannel交给selector对象
            this.selector = Selector.open();
            channel.register(selector, SelectionKey.OP_CONNECT);

            //发起建立连接的请求，这里会立即返回，当连接建立完成后，SocketChannel就会被选取出来
            channel.connect(address);
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
        return this;
    }

	/**
	 * 检查连接是否建立完成
	 */
    public boolean waitConnect() throws IOException {
    	boolean isConnect = false;
		while (0 != this.selector.select()) {
			final Iterator<SelectionKey> keyIter = selector.selectedKeys().iterator();
			while (keyIter.hasNext()) {
				//连接建立完成
				SelectionKey key = keyIter.next();
				if (key.isConnectable()) {
					if (this.channel.finishConnect()) {
						this.channel.register(selector, SelectionKey.OP_READ);
						isConnect = true;
					}
				}
				keyIter.remove();
				break;
			}
			if (isConnect) {
				break;
			}
		}
		return isConnect;
	}

    /**
     * 开始监听
     */
    public void listen() {
		this.executorService = Executors.newSingleThreadExecutor(r -> {
            final Thread thread = Executors.defaultThreadFactory().newThread(r);
            thread.setName("nio-client-listen");
            thread.setDaemon(true);
            return thread;
        });
		this.executorService.execute(() -> {
            try {
                doListen();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 开始监听
     *
     * @throws IOException IO异常
     */
    private void doListen() throws IOException {
        while (0 != this.selector.select()) {
            // 返回已选择键的集合
            final Iterator<SelectionKey> keyIter = selector.selectedKeys().iterator();
            while (keyIter.hasNext()) {
                handle(keyIter.next());
                keyIter.remove();
            }
        }
    }

    /**
     * 处理SelectionKey
     *
     * @param key SelectionKey
     */
    private void handle(SelectionKey key) throws IOException {
        //连接建立完成
//        if (key.isConnectable()) {
//            if (this.channel.finishConnect()) {
//                this.channel.register(selector, SelectionKey.OP_READ);
//            }
//        }

        // 读事件就绪
        if (key.isReadable()) {
            final SocketChannel socketChannel = (SocketChannel) key.channel();
            read(socketChannel);
        }
    }

    /**
     * 处理读事件<br>
     * 当收到读取准备就绪的信号后，回调此方法，用户可读取从客户端传出来的消息
     *
     * @param socketChannel SocketChannel
     */
    protected abstract void read(SocketChannel socketChannel);

    /**
     * 处理读事件<br>
     * 当收到读取准备就绪的信号后，回调此方法，用户可读取从客户端传世来的消息
     *
     * @param buffer 服务端数据存储缓存
     * @return this
     */
    public NioClient read(ByteBuffer buffer) {
        try {
            this.channel.read(buffer);
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
        return this;
    }

    /**
     * 实现写逻辑<br>
     * 当收到写出准备就绪的信号后，回调此方法，用户可向客户端发送消息
     *
     * @param datas 发送的数据
     * @return this
     */
    public NioClient write(ByteBuffer... datas) {
        try {
            this.channel.write(datas);
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
        return this;
    }

    public void closeListen() {
		this.executorService.shutdown();
	}

    @Override
    public void close() {
        IoUtil.close(this.selector);
        IoUtil.close(this.channel);
		closeListen();
    }
}
