package com.hongyue.app.media.util.misc;

public class TimeUtils {


    public static String getPlayerTimeStr(int time) {
        Integer hour = time/(60*60);
        Integer minute = (time-hour*3600)/60;
        Integer seconds = time-hour*3600-minute*60;
        StringBuffer sb = new StringBuffer();
        if (hour >= 3600) {
            if(hour.toString().length() == 1) {
                sb.append("0"+hour);
            } else {
                sb.append(hour);
            }
            sb.append(":");
        }
        if(minute.toString().length() == 1) {
            sb.append("0" + minute);
        } else {
            sb.append(minute);
        }
        sb.append(":");
        if(seconds.toString().length() == 1) {
            sb.append("0" + seconds);
        } else {
            sb.append(seconds);
        }

        return sb.toString();
    }


}
