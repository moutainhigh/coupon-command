package cn.vpclub.coupon.command.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.cache.MapCache;
import org.dom4j.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import sun.misc.BASE64Encoder;

import java.io.*;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Created by zhangyingdong on 2018/6/26.
 */
@Slf4j
public class YouKuUtil {

    public static String doGenerateSign(String value,String merprikeypath) throws IOException {
        byte[] privateKeyBytes = getProp(merprikeypath);
        String reqSign = "";
        try
        {
            reqSign = sign(value, privateKeyBytes);
        }
        catch (Exception e)
        {
            e.fillInStackTrace();
        }
        return reqSign;
    }

    private static byte[] getProp(String key) throws IOException {
        InputStream in = null;
        ResourceBundle rb = null;
        byte[] kb = (byte[])null;
        FileInputStream fis = null;
        String merPriKeyPath = key;
        log.info("merPriKeyPath===>"+merPriKeyPath);
        kb = (byte[])null;
        try
        {
//            Resource resource = new ClassPathResource(merPriKeyPath);
//            File f = resource.getFile();
////            File f = new File(merPriKeyPath);
//            kb = new byte[(int)f.length()];
//            log.info("kb:"+kb.length);
//            fis = new FileInputStream(f);
//            fis.read(kb);
            InputStream ksfis = null;
            ksfis = MapCache.class.getResourceAsStream(merPriKeyPath);
            BufferedInputStream ksbufin = new BufferedInputStream(ksfis);
            int count = ksfis.available();
            kb = new byte[count];
            log.info("kb:"+kb.length);
            ksbufin.read(kb);




        }
        catch (Exception e)
        {
            throw e;
        }
        return kb;
    }

    private static String sign(String dataToSign, byte[] privateKeyBytes)
            throws GeneralSecurityException, IOException
    {
        PKCS8EncodedKeySpec peks = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey pk = kf.generatePrivate(peks);

        Signature sig = Signature.getInstance("SHA1withRSA");
        sig.initSign(pk);
        sig.update(dataToSign.getBytes("gb2312"));
        byte[] sb = sig.sign();


        BASE64Encoder base64 = new BASE64Encoder();
        String b64Str = base64.encode(sb);
        BufferedReader br = new BufferedReader(new StringReader(b64Str));
        String tmpStr = "";
        String tmpStr1 = "";
        while ((tmpStr = br.readLine()) != null) {
            tmpStr1 = tmpStr1 + tmpStr;
        }
        b64Str = tmpStr1;
        return b64Str;
    }

    static public String getURL(LinkedHashMap<String,String> map) throws UnsupportedEncodingException
    {
        String md5 = "";
        Set<String> keys = map.keySet();
        for(String key : keys)
        {
            String value = map.get(key);
            if(key!=null)
                md5 += key+"="+ URLEncoder.encode(value, "UTF-8")+"&";
            else
                md5 += key+"=&";

        }
        md5 = md5.substring(0,md5.length()-1);
        return md5;
    }


    public static JSONObject xml2Json(String xmlStr) throws DocumentException {
        Document doc = DocumentHelper.parseText(xmlStr);
        JSONObject json = new JSONObject();
        dom4j2Json(doc.getRootElement(), json);
        return json;
    }
    public static void dom4j2Json(Element element, JSONObject json) {
        // 如果是属性
        for (Object o : element.attributes()) {
            Attribute attr = (Attribute) o;
            if (!isEmpty(attr.getValue())) {
                json.put("@" + attr.getName(), attr.getValue());
            }
        }
        List<Element> chdEl = element.elements();
        if (chdEl.isEmpty() && !isEmpty(element.getText())) {// 如果没有子元素,只有一个值
            json.put(element.getName(), element.getText());
        }

        for (Element e : chdEl) {// 有子元素
            if (!e.elements().isEmpty()) {// 子元素也有子元素
                JSONObject chdjson = new JSONObject();
                dom4j2Json(e, chdjson);
                Object o = json.get(e.getName());
                if (o != null) {
                    JSONArray jsona = null;
                    if (o instanceof JSONObject) {// 如果此元素已存在,则转为jsonArray
                        JSONObject jsono = (JSONObject) o;
                        json.remove(e.getName());
                        jsona = new JSONArray();
                        jsona.add(jsono);
                        jsona.add(chdjson);
                    }
                    if (o instanceof JSONArray) {
                        jsona = (JSONArray) o;
                        jsona.add(chdjson);
                    }
                    json.put(e.getName(), jsona);
                } else {
                    if (!chdjson.isEmpty()) {
                        json.put(e.getName(), chdjson);
                    }
                }

            } else {// 子元素没有子元素
                for (Object o : element.attributes()) {
                    Attribute attr = (Attribute) o;
                    if (!isEmpty(attr.getValue())) {
                        json.put("@" + attr.getName(), attr.getValue());
                    }
                }
                if (!e.getText().isEmpty()) {
                    json.put(e.getName(), e.getText());
                }
            }
        }
    }

    public static boolean isEmpty(String str) {

        if (str == null || str.trim().isEmpty() || "null".equals(str)) {
            return true;
        }
        return false;
    }
}
