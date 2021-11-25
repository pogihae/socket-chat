public interface ChatProtocol {
    int STATE = 0;
    int BODY = 1;

    int MSG_TARGET = 1;
    int MSG_SRC = 2;
    int MSG_CONTENT = 3;

    enum State {
        CONNECT, MSG, REJECT, LUSER, ALL, SERVER
    }

    default String makeProtocolMSG(State state, String body) {
        return state.name() + " " + body;
    }

    /**
     * separateState
     * <p>
     * 프로토콜 메시지에서 STATE 분리
     * MSG 인 경우 src target content 추가 분리
     */
    default String[] separateState(String protocolMSG) {
        protocolMSG = protocolMSG.trim();

        if (protocolMSG.startsWith(State.MSG.name())) {
            String[] parsed = protocolMSG.split(" ");
            String state = parsed[STATE];
            String target = parsed[MSG_TARGET];
            String src = parsed[MSG_SRC];
            StringBuilder content = new StringBuilder();
            for (int i = 3; i < parsed.length; i++)
                content.append(parsed[i]).append(" ");
            return new String[]{state, target, src, content.toString()};
        }

        int firstSpace = protocolMSG.indexOf(" ");
        String state = protocolMSG.substring(0, firstSpace);
        String body = protocolMSG.substring(firstSpace + 1);
        return new String[]{state, body};
    }
}