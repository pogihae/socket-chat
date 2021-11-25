import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * ChatServer
 * <p>
 * 채팅 서버측
 * 유저별 User 클래스 생성
 * User 클래스 별 스레드 생성
 */
public class ChatServer implements ChatProtocol {
    private ExecutorService executorService;
    private ServerSocket serverSocket;
    private Map<String, User> idToUser;

    /**
     * Constructor
     */
    public ChatServer(int port) {
        idToUser = new ConcurrentHashMap<>();   //thread-safe

        executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startServer() {
        executorService.submit(new ChatServerThread());
    }

    /**
     * ChatServerThread
     * <p>
     * 유저 등록을 위한 스레드
     */
    private class ChatServerThread implements Runnable {
        Socket socket;

        ChatServerThread() {
            socket = null;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    socket = serverSocket.accept();
                    System.out.println("connected " + socket.getRemoteSocketAddress());

                    //닉네임 유효성 검사
                    String nickName = getValidID(socket);
                    System.out.println("Valid ID: " + nickName);

                    //유저 등록
                    new User(socket, nickName);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    /**
     * getValidID
     * <p>
     * 유효성 검사 후
     * 실패시 클라이언트에게 실패 메시지 전송 후 다시 대기
     * 성공시 닉네임 리턴
     */
    private String getValidID(Socket socket) {
        Scanner inputStream;
        PrintWriter outputStream;

        try {
            inputStream = new Scanner(socket.getInputStream());
            outputStream = new PrintWriter(socket.getOutputStream(), true);

            while (true) {
                String received = inputStream.nextLine();
                String[] fromClient = separateState(received);

                if (!fromClient[STATE].equals(State.CONNECT.name())) continue;
                String nickName = fromClient[BODY];

                //유효성 검사
                String body = null;
                if (Arrays.stream(State.values())
                        .anyMatch(s -> s.name().equalsIgnoreCase(nickName))) {
                    body = "Can't use state for name";
                }
                if (idToUser.containsKey(nickName)) {
                    body = "Already used name";
                }
                if (fromClient[1].contains(" ")) {
                    body = "Can't use space in name";
                }
                if (body == null) return nickName;

                //등록 실패 전송
                System.out.println("Invalid id: " + nickName);
                String rejectName = makeProtocolMSG(State.REJECT, body);
                outputStream.println(rejectName);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * User
     * <p>
     * 유저 클래스
     * <p>
     * 생성시 receive 위한 스레드 생성 후 수신대기
     * 입장 및 퇴장 시 업데이트된 유저목록 모두에게 재전송
     * <p>
     * 전송을 위한 스레드 생성(not busy wait)
     * UserA.send = 서버가 UserA에게 전송
     */
    private class User {
        private final Socket socket;
        private final String nickName;

        Scanner inputStream;
        PrintWriter outputStream;

        User(Socket socket, String nickName) {
            this.socket = socket;
            this.nickName = nickName;
            try {
                inputStream = new Scanner(socket.getInputStream());
                outputStream = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                e.printStackTrace();
            }

            //유저 목록 전송
            idToUser.put(nickName, this);
            for (User user : idToUser.values()) {
                user.sendUserList();
                user.send(State.ALL.name(), State.SERVER.name(), nickName+ " entered");
            }
            System.out.println("Entered " + socket.getRemoteSocketAddress());

            //수신 대기
            receive();
        }

        /**
         * receive
         * <p>
         * 수신을 위한 스레드
         */
        void receive() {
            executorService.submit(() -> {
                while (true) {
                    try {
                        String fromClient = inputStream.nextLine();
                        String[] parsed = separateState(fromClient);
                        String state = parsed[STATE];

                        if (state.equals(State.MSG.name())) {
                            String target = parsed[MSG_TARGET];
                            String msg = parsed[MSG_CONTENT];

                            //모두에게
                            if (target.equals(State.ALL.name()))
                                for (User user : idToUser.values())
                                    user.send(target, nickName, msg);
                                //귓속말
                            else whisper(target, msg);
                        }
                    } catch (Exception e) {
                        //퇴장 시 유저 목록 전송
                        idToUser.remove(nickName);
                        for (User user : idToUser.values()) {
                            user.sendUserList();
                            user.send(State.ALL.name(), State.SERVER.name(), nickName+ " exit");
                        }
                        System.out.println("Connect End");
                        break;
                    }
                }
            });
        }

        /**
         * send
         * <p>
         * 송신을 위한 스레드
         * target ALL이 아니면 whisper
         */
        void send(String target, String src, String msg) {
            executorService.submit(() -> {
                String requestMSG = makeProtocolMSG(State.MSG, target + " " + src + " " + msg);
                outputStream.println(requestMSG);
            });
        }

        /**
         * whisper
         * <p>
         * 상대 존재 여부 판단 후 타겟에게만 전송
         */
        void whisper(String target, String msg) {
            User targetUser = idToUser.get(target);
            //상대 존재하지 않는경우
            if (targetUser == null) {
                outputStream.println(makeProtocolMSG(State.REJECT, target + " isn't here!"));
                sendUserList();
            }
            //상대 존재
            else {
                targetUser.send(target, nickName, msg);
                this.send(target, nickName, msg);
            }
        }

        /**
         * sendUserList
         * <p>
         * 귓속말을 위한 유저 목록 전송
         */
        private void sendUserList() {
            try {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                if (idToUser.isEmpty()) {
                    out.println(makeProtocolMSG(State.LUSER, "0"));
                    System.out.println(makeProtocolMSG(State.LUSER, "0"));
                    return;
                }

                StringBuilder userL = new StringBuilder();
                int userCnt = 0;
                for (String id : idToUser.keySet()) {
                    userL.append(id).append(" ");
                    userCnt++;
                }

                String userListMSG = makeProtocolMSG(State.LUSER, userCnt + " " + userL);
                out.println(userListMSG);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}