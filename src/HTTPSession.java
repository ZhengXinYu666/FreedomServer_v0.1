import java.io.*;
import java.net.Socket;

public class HTTPSession implements Runnable {

    //线程计数器
    static long threadCount = 0;
    //获取客户端Socket
    private Socket clientSocket = null;
    Thread t = null;
    // 设定HTTP 请求的最大字节数 1024*8=2^13
    final static int bufsize = 8192;
    //定义一个字节数组存储请求  设定数组的大小为bufsize
    byte[] buf = new byte[bufsize];
    // Host类实例化对象  目标host
    Host targethost = null;

    // 接受到客户端Socket s连接
    public HTTPSession(Socket s) {
        // TODO Auto-generated constructor stub
        clientSocket = s;
        // 为该次会话建立一个守护线程
        t = new Thread(this);
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        //输出start的线程ID
        System.out.println("\t[+] HTTPSession.run():StartID : " + t.getId());
        //线程计数增加
        threadCount++;
        try {
            //获取输入流对象 客户端获取输入流
            InputStream isInputStream = clientSocket.getInputStream();
            //如果对象为空则返回
            if (isInputStream == null)
                return;

            // 首次读bufsize大小的isInputStream写到buf---readll其实就是数据的长度
            int readll = GetHeaderToBuf(isInputStream, bufsize, buf, 0);

            // 构建读取头输入流
            //字节数组输入流，将输入流读取的数据保存到字节数组的缓冲区
            ByteArrayInputStream bais = new ByteArrayInputStream(buf, 0, readll);
            InputStreamReader isr = new InputStreamReader(bais);
            BufferedReader br = new BufferedReader(isr);

            // 从请求头流读取数据
            targethost = new Host();
            ReadHeaderData(br,targethost);

            // 根据主机信息，输出IP地址和端口号
            targethost.cal();
            System.out.println("\t\t[+] Address:[" + targethost.address
                    + "]Port:" + targethost.port);

            // 客户端请求链接中转管道
            System.out.println("\t\t[+] Pipe Start: -----------------");
            try {
                Pipe(buf, readll, clientSocket.getInputStream(),
                        clientSocket.getOutputStream(), targethost);
            } catch (Exception e) {
                System.out.println("\t\t[#] Pipe Exception!" + e.toString());
                // e.printStackTrace();// print red color
            }
            System.out.println("\t\t[-] Address:[" + targethost.address
                    + "]Port:" + targethost.port);
            System.out.println("\t\t[-] Pipe End  : -----------------");

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println("\t[-] HTTPSession.run():End ID : " + t.getId());
        System.out.println("\t[*] ThreadCount:" + --threadCount);
    }// run()

    /**
     * 读取请求头流数据
     *
     * @param br
     * @param targethost
     * @throws IOException
     */
    private void ReadHeaderData(BufferedReader br, Host targethost)
            throws IOException {
        String headdataline = null;
        boolean flag = false;
        //按行读取数据
        while ((headdataline = br.readLine()) != null) {
            //添加判定 如果以host:为开始，就将读到的这个作为请求头数据，并将标记置位true
            if (headdataline.toLowerCase().startsWith("host:")) {
                targethost.host = headdataline;
                flag = true;
            }
            // 输出请求头信息
            System.out.println("\t\t[*] " + headdataline);
        }// while
        // 请求头的流数据如果没有Host信息
        if (!flag) {
            clientSocket.getOutputStream().write("error!".getBytes());
            clientSocket.close();
            System.out.println("\t\t[#] No host of head data");
            return;
        }
    }

    /**
     * // 首次读bufsize大小的isInputStream写到buf
     *
     * @param is
     * @param bufsize
     * @param buf
     * @param readll
     * @return readll
     * @throws IOException
     */
    private int GetHeaderToBuf(InputStream is, final int bufsize,
                               byte[] buf, int readll) throws IOException {
        //分割头字节
        int splitheadbyte = 0;
        //读取buf数组
        int readl = is.read(buf, 0, bufsize);
        while (readl > 0) {
            readll += readl;
            //找Http请求头的结束位置
            splitheadbyte = FindHeaderEnd(buf, readll);
            if (splitheadbyte > 0) {
                // break while 表示找到请求头结束位置
                break;
            }
            // 读buf剩下长度bufsize - readll
            readl = is.read(buf, readll, bufsize - readll);
            System.out.println("\t\t[*] 读bufsize剩下长度bufsize-havereadlen");
        }// while
        return readll;

    }

    /**
     * 找Http请求头的结束位置
     *
     * @param buf
     * @param readll
     * @return
     */
    private int FindHeaderEnd(final byte[] buf, int readll) {
        int splitbyte = 0;
        while (splitbyte + 3 < readll) {
            if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n'
                    && buf[splitbyte + 2] == '\r' && buf[splitbyte + 3] == '\n') {
                // 报文首部与报文主体有一个空行（CR+LF）
                return splitbyte + 4;
            }
            splitbyte++;
        }
        return 0;
    }

    /**
     * // 客户端请求链接中转管道
     *
     * @param requesthead
     * @param requestLen
     * @param clientIS
     * @param clientOS
     * @param targethost
     * @throws IOException
     *
     * 思路：我们要做的是一个中转的通道
     *
     *
     */
    void Pipe(byte[] requesthead, int requestLen, InputStream clientIS,
              OutputStream clientOS, Host targethost) throws IOException {
        byte bytes[] = new byte[1024 * 32];
        // 建立目标Socket和目标输出流和目标输入流
        Socket targetsocket = new Socket(targethost.address, targethost.port);
        //设置连接超时时间
        targetsocket.setSoTimeout(3000);
        OutputStream targetOS = targetsocket.getOutputStream();
        InputStream targetIS = targetsocket.getInputStream();
        try {
            do {
                //输出代理服务器请求连接启动并记录该目标socket的哈希值
                System.out.println("\t\t\t[+] Proxy requset-connect Start , Target Socket: "
                                + targetsocket.hashCode());
                // 向目标Socket的输出流写入请求头
                targetOS.write(requesthead, 0, requestLen);
                int resultLen = 0;
                try {
                    while ((resultLen = targetIS.read(bytes)) != -1
                            && !clientSocket.isClosed()
                            && !targetsocket.isClosed()) {
                        // 请求到目标的正文向输出客户端Socket写入
                        clientOS.write(bytes, 0, resultLen);
                    }
                } catch (Exception e) {
                    System.out.println("\t\t\t[#] Target Socket Exception: "
                            + e.toString());
                }
                //代理服务请求连接被破坏并输出该目标Socket的哈希值
                System.out.println("\t\t\t[-] Proxy requset-connect Broken, Target Socket: "
                                + targetsocket.hashCode());
                //当客户端Socket关闭，请求行信息未读完
            } while (!clientSocket.isClosed()
                    && (requestLen = clientIS.read(requesthead)) != -1);
        } catch (Exception e) {
            System.out.println("\t\t\t[#] Client Socket exception:"
                    + e.toString());
        }
        System.out.println("\t\t\t[-] End, Target Socket: "
                + targetsocket.hashCode());

        //释放资源
        targetOS.close();
        targetIS.close();
        clientIS.close();
        clientOS.close();
        targetsocket.close();
        clientSocket.close();
    }

}
