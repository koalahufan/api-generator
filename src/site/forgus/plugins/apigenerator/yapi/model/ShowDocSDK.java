package site.forgus.plugins.apigenerator.yapi.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import site.forgus.plugins.apigenerator.util.SSLClient;
import site.forgus.plugins.apigenerator.yapi.sdk.ConfigException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.util.Date;

/**
 * showdoc sdk
 *
 * @author huzh
 * @version 1.0
 * @date 2021/2/24 10:49
 */
public class ShowDocSDK {

    private static String mdApiUri = "/api/item/updateByApi";

    private static final String URL_ERROR_MSG = "ShowDoc server url is Unreachable!";

    private static Gson gson;

    static {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Date.class, new DateSerializer()).setDateFormat(DateFormat.LONG);
        builder.registerTypeAdapter(Date.class, new DateDeserializer()).setDateFormat(DateFormat.LONG);
        gson = builder.create();
    }

    /**
     * 保存接口（新增或更新）
     *
     * @param showDocInterface ShowDoc Api 参数
     */
    public static void saveInterface(String serverUrl, ShowDocInterface showDocInterface) throws Exception {
        String string = doPost(serverUrl + mdApiUri, gson.toJson(showDocInterface));
        ShowDocResponse showDocResponse = gson.fromJson(string, ShowDocResponse.class);
        if (!"0".equals(showDocResponse.getError_code())) {
            throw new ConfigException("request error！" + string);
        }
    }

    private static String doPost(String url, String body)
            throws IOException, ConfigException, NoSuchAlgorithmException, KeyManagementException {
        return doHttpRequest(buildPostRequestWithJsonType(url, body));
    }

    private static HttpPost buildPostRequestWithJsonType(String url, String body) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-type", "application/json;charset=utf-8");
        httpPost.setEntity(new StringEntity(body == null ? "" : body, StandardCharsets.UTF_8));
        httpPost.setConfig(RequestConfig.custom().setConnectTimeout(3000).build());
        return httpPost;
    }

    private static String doHttpRequest(HttpUriRequest httpUriRequest)
            throws NoSuchAlgorithmException, IOException, ConfigException, KeyManagementException {
        CloseableHttpClient httpclient = new SSLClient();
        try(CloseableHttpResponse response = httpclient.execute(httpUriRequest)) {
            if(response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new ConfigException(URL_ERROR_MSG + response.getEntity().getContent());
            }
            return getStreamAsString(response.getEntity().getContent());
        }
    }

    public static String getStreamAsString(InputStream stream) throws IOException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringWriter writer = new StringWriter();

            char[] chars = new char[256];
            int count = 0;
            while ((count = reader.read(chars)) > 0) {
                writer.write(chars, 0, count);
            }

            return writer.toString();
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
