package client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

public class NioChatClient implements Runnable{
    private Selector selector;

    @Override
    public void run() {
        try {
            startClient();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startClient() throws IOException {
        // Создаем клиентский канал, подлкючаемся к серверу, устанавливаем блокирующий режим канала
        SocketChannel socketChannel = openConnection();
        // Создаем селектор
        selector = Selector.open();
        // Регистируем канал на селекторе
        socketChannel.register(selector, SelectionKey.OP_CONNECT|SelectionKey.OP_READ|SelectionKey.OP_WRITE);
        while(!Thread.interrupted()) {
            // Получаем количество каналов, готовых к работе
            int readyChannels = selector.selectNow();
            if(readyChannels == 0) {
                continue;
            }

            // Собираем все ключи из селектора
            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

            while(keyIterator.hasNext()) {
                // Получаем ключ из интератора
                SelectionKey currentKey = keyIterator.next();
                keyIterator.remove();

                // Если канал подключился к сокету
                if(currentKey.isConnectable()) {
                    System.out.println("I'm connected to the server!");
                    handleConnectible(currentKey);
                }

                if(currentKey.isWritable()){
                    handleWritable(currentKey);
                }
            }
        }
    }

    private void handleWritable(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel)key.channel();
        // Создадим буффер и заполним его 0
        ByteBuffer buffer = ByteBuffer.allocate(100);
        Scanner scanner = new Scanner(System.in);

        System.out.println("Enter message to server: ");
        String output = scanner.nextLine();

        // Пишем сначала в буфер, потом его отправляем в канал
        buffer.put(output.getBytes());
        buffer.flip();
        channel.write(buffer);

        System.out.println("Message send");

        buffer.clear();
        channel.close();
        key.cancel();
    }

    private void handleConnectible(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        if(channel.isConnectionPending()) {
            channel.finishConnect();
        }
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_WRITE|SelectionKey.OP_READ);
    }

    private static SocketChannel openConnection() throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("127.0.0.1", 8189));
        socketChannel.configureBlocking(false);
        return socketChannel;
    }

    public static void main(String[] args) {
        new Thread(new NioChatClient()).start();
    }
}