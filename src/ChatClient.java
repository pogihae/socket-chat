import java.awt.*;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;
import javax.swing.text.*;

/**
 * ChatClient
 * <p>
 * 채팅 클라이언트측
 * 수신을 위한 별도 스레드 생성
 */
public class ChatClient implements ChatProtocol {

    private final String serverIP;
    private final int serverPort;

    private String nickName;
    private Set<String> userSet;    //유저목록

    private Scanner in;
    private PrintWriter out;

    JFrame frame = new JFrame("Chatter");
    JTextField textField = new JTextField(50);
    JTextPane messagePane = new JTextPane();    //귓속말 색 변경을 위한 pane으로 변경
    JButton sendButton = new JButton("SEND");
    JComboBox<String> userCombo;                //유저 목록 콤보

    /**
     * Constructor
     *
     * @param serverIP   아이피주소 문자열
     * @param serverPort 포트번호 정수
     */
    public ChatClient(String serverIP, int serverPort) {
        this.serverIP = serverIP;
        this.serverPort = serverPort;

        userSet = ConcurrentHashMap.newKeySet();    //thread-safe
        userSet.add(State.ALL.name());

        textField.setEditable(false);
        messagePane.setEditable(false);
        messagePane.setPreferredSize(new Dimension(50, 300));
        userCombo = new JComboBox<>(userSet.toArray(new String[0]));

        //하단
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(textField, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);
        panel.add(userCombo, BorderLayout.WEST);

        frame.getContentPane().add(panel, BorderLayout.SOUTH);
        frame.getContentPane().add(new JScrollPane(messagePane), BorderLayout.CENTER);
        frame.pack();

        textField.addActionListener(e -> send());
        sendButton.addActionListener(e -> send());
    }

    /**
     * getName
     * <p>
     * 아이디 타이핑 창
     */
    private String getName() {
        return JOptionPane.showInputDialog(
                frame,
                "Choose a nickname:",
                "Nickname",
                JOptionPane.PLAIN_MESSAGE
        );
    }

    /**
     * startClient
     * <p>
     * 닉네임 설정, 유저 목록 수신 후
     * 수신 대기를 위한 별도 스레드 생성
     */
    public void startClient() {
        new Thread(() -> {
            try {
                Socket socket = new Socket(serverIP, serverPort);
                in = new Scanner(socket.getInputStream());
                out = new PrintWriter(socket.getOutputStream(), true);

                //닉네임 설정, 유저 목록 수신
                enter();
                System.out.println("Entered room");
                textField.setEditable(true);

                //수신 대기
                receive();
            } catch (Exception e) {
                e.printStackTrace();
                endClient();
            }
        }).start();
    }

    /**
     * enter
     * <p>
     * 닉네임 설정 후 전송
     * 유효성 검사 통과 후 유저목록 수신 후 리턴
     */
    private void enter() {
        while (true) {
            nickName = getName();
            out.println(makeProtocolMSG(State.CONNECT, nickName));

            String recvMsg = in.nextLine();
            String[] parsed = separateState(recvMsg);
            System.out.println(recvMsg);

            if (interpretProtocolMSG(parsed)) break;
        }
    }

    /**
     * updateUserList
     * <p>
     * 유저 리스트 수신 시 업데이트
     *
     * @param body parsed 된 protocol message 중 body
     */
    private void updateUserList(String body) {
        String[] parsed = body.split(" ");
        int userNum = Integer.parseInt(parsed[0]);
        if (userNum == 0) {
            return;
        }
        userSet.addAll(Arrays.asList(parsed).subList(1, userNum + 1));

        //gui 송신대상
        userCombo.removeAllItems();
        for (String id : userSet) {
            if (!id.equals(this.nickName))
                userCombo.addItem(id);
        }
    }

    /**
     * receive
     * <p>
     * startClient 에서 생성된 스레드 위에서 수신대기
     */
    private void receive() {
        System.out.println("Receiving...");
        while (true) {
            try {
                String[] parsed = separateState(in.nextLine());
                if (!interpretProtocolMSG(parsed)) throw new Exception("Unknown ERR");
            } catch (Exception e) {
                e.printStackTrace();
                endClient();
                break;
            }
        }
    }

    /**
     * send
     * <p>
     * 송신을 위한 스레드
     * 송신 후 송신 텍스트 창 비움
     */
    private void send() {
        new Thread(() -> {
            String msg = textField.getText();
            System.out.println("Send " + msg);
            String protocol = makeProtocolMSG(State.MSG, userCombo.getSelectedItem() + " " + nickName + " " + msg);
            out.println(protocol);
            textField.setText("");
        }).start();
    }

    /**
     * interpretProtocolMSG
     * <p>
     * STATE 별 행동
     * MSG : 수신자 송신자 내용 구분 후 수신자가 ALL 아니면 GRAY 색으로 내용 출력
     * LUSER : 유저리스트 업데이트
     * REJECT : 수신된 경고 내용으로 팦업 경고창 생성
     */
    private boolean interpretProtocolMSG(String[] parsed) {
        String state = parsed[STATE];

        if (state.equals(State.MSG.name())) {
            String src = parsed[MSG_SRC];
            String target = parsed[MSG_TARGET];
            String msg = parsed[MSG_CONTENT];
            Color color = (State.ALL.name().equals(target)) ? Color.WHITE : Color.GRAY;
            if(src.equals(State.SERVER.name())) color = Color.YELLOW;
            appendToPane(messagePane, src + ": " + msg + "\n", color);
            return true;
        } else if (state.equals(State.LUSER.name())) {
            updateUserList(parsed[BODY]);
            return true;
        } else if (parsed[STATE].equals(State.REJECT.name())) {
            JOptionPane.showMessageDialog(frame,
                    parsed[BODY],
                    "Warning",
                    JOptionPane.WARNING_MESSAGE);
        }
        return false;
    }

    /**
     * appendToPane
     * <p>
     * JTextPane 사용을 위한 함수
     * 색설정
     */
    private void appendToPane(JTextPane tp, String msg, Color c) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setBackground(style, c);

        try {
            int len = tp.getDocument().getLength();
            tp.getDocument().insertString(len, msg, style);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void endClient() {
        frame.setVisible(false);
        frame.dispose();
    }
}