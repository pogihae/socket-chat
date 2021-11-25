import javax.swing.*;
import java.io.*;

public class ChatClientApp {
    public static void main(String[] args) {
        String fileName = "serverinfo.dat";
        String ip;
        int port;

        try {
            DataInputStream confReader = new DataInputStream(
                    new FileInputStream(fileName));
            String conf = confReader.readUTF();

            String[] confs = conf.split(" ");

            ip = confs[0];
            port = Integer.parseInt(confs[2]);
        } catch (IOException e) {
            System.out.println(fileName+" not exist\n");
            ip = "localhost";
            port = 1234;
            e.printStackTrace();
        }

        //시작
        ChatClient chatClient = new ChatClient(ip, port);
        chatClient.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        chatClient.frame.setVisible(true);
        chatClient.startClient();
    }
}
