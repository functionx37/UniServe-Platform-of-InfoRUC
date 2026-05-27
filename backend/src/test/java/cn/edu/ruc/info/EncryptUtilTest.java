package cn.edu.ruc.info;

import cn.edu.ruc.info.util.EncryptUtil;
import cn.edu.ruc.info.util.MaskUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class EncryptUtilTest {

    @Autowired
    private EncryptUtil encryptUtil;

    @Test
    void testEncryptDecrypt() {
        String phone = "13812345678";
        String encrypted = encryptUtil.encrypt(phone);
        System.out.println("加密后: " + encrypted);
        String decrypted = encryptUtil.decrypt(encrypted);
        System.out.println("解密后: " + decrypted);
        assertEquals(phone, decrypted);
    }

    @Test
    void testMask() {
        String phone = "13812345678";
        String masked = MaskUtil.maskPhone(phone);
        System.out.println("脱敏后: " + masked);
        assertEquals("138****5678", masked);

        String idCard = "110101199001011234";
        String maskedId = MaskUtil.maskIdCard(idCard);
        System.out.println("身份证脱敏后: " + maskedId);
        assertEquals("1101********1234", maskedId);
    }
}