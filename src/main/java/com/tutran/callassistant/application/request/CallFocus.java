package com.tutran.callassistant.application.request;

/** Khía cạnh người dùng quan tâm; chỉ ảnh hưởng phần phân tích ưu tiên, không đổi verdict. */
public enum CallFocus {
    /** Nghe không rõ, rè, mất tiếng, giật, trễ. */
    AUDIO_QUALITY,
    /** Không gọi được, không đổ chuông, thiết lập chậm. */
    SETUP,
    /** Rớt cuộc gọi, ai là bên kết thúc. */
    DISCONNECT,
    /** ICE / TURN / media transport. */
    MEDIA
}
