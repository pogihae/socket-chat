import java.io.*;

public class ChatServerApp {
    public static void main(String[] args) {
        /* make server configuration file
        try {
            DataOutputStream tet = new DataOutputStream(new FileOutputStream("serverinfo.dat"));
            tet.writeUTF("localhost port 5001");
        } catch (IOException e) {
            e.printStackTrace();
        }
        */
        String fileName = "serverinfo.dat";
        int port;

        try {
            DataInputStream confReader = new DataInputStream(
                    new FileInputStream(fileName));
            String conf = confReader.readUTF();

            String[] confs = conf.split(" ");

            port = Integer.parseInt(confs[2]);
        } catch (IOException e) {
            System.out.println(fileName + " not exist\n");
            port = 1234;
            e.printStackTrace();
        }

        //시작
        ChatServer chatServer = new ChatServer(port);
        chatServer.startServer();

    }
}
