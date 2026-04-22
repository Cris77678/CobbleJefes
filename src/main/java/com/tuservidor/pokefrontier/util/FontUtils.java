package com.tuservidor.pokefrontier.util;

public class FontUtils {
    public static String toSmallCaps(String input) {
        if (input == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean skipNext = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '§' || c == '&') { sb.append(c); skipNext = true; continue; }
            if (skipNext) { sb.append(c); skipNext = false; continue; }
            switch (Character.toLowerCase(c)) {
                case 'a': sb.append('ᴀ'); break; case 'b': sb.append('ʙ'); break;
                case 'c': sb.append('ᴄ'); break; case 'd': sb.append('ᴅ'); break;
                case 'e': sb.append('ᴇ'); break; case 'f': sb.append('ꜰ'); break;
                case 'g': sb.append('ɢ'); break; case 'h': sb.append('ʜ'); break;
                case 'i': sb.append('ɪ'); break; case 'j': sb.append('ᴊ'); break;
                case 'k': sb.append('ᴋ'); break; case 'l': sb.append('ʟ'); break;
                case 'm': sb.append('ᴍ'); break; case 'n': sb.append('ɴ'); break;
                case 'ñ': sb.append('ñ'); break; case 'o': sb.append('ᴏ'); break;
                case 'p': sb.append('ᴘ'); break; case 'r': sb.append('ʀ'); break;
                case 's': sb.append('s'); break; case 't': sb.append('ᴛ'); break;
                case 'u': sb.append('ᴜ'); break; case 'v': sb.append('ᴠ'); break;
                case 'w': sb.append('ᴡ'); break; case 'y': sb.append('ʏ'); break;
                case 'z': sb.append('ᴢ'); break; default: sb.append(c); break;
            }
        }
        return sb.toString();
    }
}
