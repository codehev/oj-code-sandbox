import java.util.ArrayList;
import java.util.List;

/**
 * 无限占用空间（浪费系统内存）
 *
 * 一定记得去掉包名，导入的包不用去掉，以及把类名改为Main
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        List<byte[]> bytes = new ArrayList<>();
        while (true) {
            bytes.add(new byte[10000]);
        }
    }
}
