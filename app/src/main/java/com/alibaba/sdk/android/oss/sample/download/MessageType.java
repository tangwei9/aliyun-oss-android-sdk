package com.alibaba.sdk.android.oss.sample.download;

public enum MessageType {
    MESSAGE_NOT_FOUND(-1),
    MESSAGE_PROGRESS(1),
    MESSAGE_FINISHED(2),
    MESSAGE_PAUSE(3),
    MESSAGE_CANCEL(4);

    private int type;

    MessageType(int type){
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public static MessageType parse(int type) {
        for(MessageType messageType : MessageType.values()){
            if(messageType.getType() == type){
                return messageType;
            }
        }

        return MESSAGE_NOT_FOUND;
    }
}
