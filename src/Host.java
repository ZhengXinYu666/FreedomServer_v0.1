/**
 *
 * 简单代理服务器
 * Freedom_Server
 *
 * @author 郑黑脸
 * @version v0.1
 *
 */
final public class Host {

    public String host;
    public String address;
    public int port;

    public boolean cal() {
        if (host == null) {
            return false;
        }
        int start = host.indexOf(": ");//:
        if (start == -1) {
            return false;
        }
        int next = host.indexOf(':', start + 2);//127
        if (next == -1) {//localhost: 127.0.0.1 找不到port
            port = 80;
            address = host.substring(start + 2);//127.0.0.1
        } else {
            address = host.substring(start + 2, next);
            port = Integer.valueOf(host.substring(next + 1));
        }
        return true;
    }
}
