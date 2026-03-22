import java.net.ServerSocket; public class TestSocket { public static void main(String[] args) throws Exception { new ServerSocket(0).close(); System.out.println("Success"); } }
