package cn.edu.ruc.info.util;

public class MaskUtil {

    /**
     * 手机号脱敏：保留前3后4，中间替换为 ****
     * 例如 13812345678 → 138****5678
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone == null ? "" : phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 身份证号脱敏：保留前4后4，中间替换为 ********
     * 例如 110101199001011234 → 1101********1234
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard == null ? "" : idCard;
        }
        return idCard.substring(0, 4) + "********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 通用字符串脱敏：保留前 prefixLen 和后 suffixLen，其余全变 *
     */
    public static String mask(String str, int prefixLen, int suffixLen) {
        if (str == null || str.isEmpty())
            return "";
        if (str.length() <= prefixLen + suffixLen) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(str, 0, prefixLen);
        sb.append("*".repeat(str.length() - prefixLen - suffixLen));
        sb.append(str.substring(str.length() - suffixLen));
        return sb.toString();
    }
}