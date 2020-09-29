package com.geekbrains.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class NioChatServerExample implements Runnable {
    // Сокет
    private ServerSocketChannel serverSocketChannel;
    // Селектор
    private Selector selector;
    private ByteBuffer buf = ByteBuffer.allocate(256);
    private int acceptedClientIndex = 1;
    // Велком мессага
    private final ByteBuffer welcomeBuf = ByteBuffer.wrap("Добро пожаловать в чат!\n".getBytes());

    // Конструктор
    NioChatServerExample() throws IOException {
        // Создаем серверный канал
        this.serverSocketChannel = ServerSocketChannel.open();
        // Привязываем сокет к каналу
        this.serverSocketChannel.socket().bind(new InetSocketAddress(8189));
        // Устанавливаем неблокирующий режим для регистрации на селекторе
        this.serverSocketChannel.configureBlocking(false);
        // Создаем селектор
        this.selector = Selector.open();
        // Регистируем наш канал на селекторе
        this.serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    @Override
    public void run() {
        try {
            System.out.println("Сервер запущен (Порт: 8189)");
            Iterator<SelectionKey> iter;
            // key содержит инфу о подключенном канале
            SelectionKey key;
            while (this.serverSocketChannel.isOpen()) {
                // Сидим, ждем, пока хотя бы 1 канал не будет готов к работе
                selector.select();
                // Набор ключей для обработки, каждый ключ - это зарегистированный канал
                iter = this.selector.selectedKeys().iterator();
                while (iter.hasNext()) {
                    // Получаем ключ из итератора
                    key = iter.next();
                    iter.remove();
                    // Проверяем, готов ли канал этого ключа создать новое соединение
                    if (key.isAcceptable()) this.handleAccept(key);
                    // Проверяем, готов ли канал этого ключа читать данные
                    if (key.isReadable()) this.handleRead(key);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        // Получаем соединение от клиента, на выходе получаем SocketChannel
        SocketChannel sc = serverSocketChannel.accept();
        if (sc != null) {
            // Собираем имя
            String clientName = "Клиент #" + acceptedClientIndex;
            acceptedClientIndex++;
            // Неблокирующий режим
            sc.configureBlocking(false);
            // Регистируем канал клиента в селекторе, устанвливаем OP_READ, как признак того, что клиент готов читать сообщения
            // Делаем аттач, может быть любой объект Object
            sc.register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE);
            // Отрпавляем в канал приветственное сообщение
            sc.write(welcomeBuf);
            // Переводим каретку на первый символ
            welcomeBuf.rewind();
            System.out.println("Подключился новый клиент " + clientName);
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        // Получаем канал переданного ключа
        SocketChannel channel = (SocketChannel)key.channel();
        channel.configureBlocking(false);
        ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.clear();
        int bytesRead = channel.read(buffer);

        while(bytesRead>0){
            System.out.println("Read bytes: "+ bytesRead);
            bytesRead = channel.read(buffer);
            if(bytesRead == -1){
                channel.close();
                key.cancel();
            }
            buffer.flip();
            while(buffer.hasRemaining()){
                System.out.print((char)buffer.get());
            }
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        // Получаем канал переданного ключа
        SocketChannel ch = (SocketChannel) key.channel();
        StringBuilder sb = new StringBuilder();

        buf.clear();
        int read = 0;
        while ((read = ch.read(buf)) > 0) {
            buf.flip();
            byte[] bytes = new byte[buf.limit()];
            buf.put(bytes);
        }
    }

    private void broadcastMessage(String msg) throws IOException {
        ByteBuffer msgBuf = ByteBuffer.wrap(msg.getBytes());
        // Бежим по всем ключам, которые зарегистированы на селекторе
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.channel() instanceof SocketChannel) {
                // Получаем канал из ключа
                SocketChannel sch = (SocketChannel) key.channel();
                // Пишем сообщение в канал
                sch.write(msgBuf);
                // Передвигаем каретку на начало
                msgBuf.rewind();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new Thread(new NioChatServerExample()).start();
    }
}