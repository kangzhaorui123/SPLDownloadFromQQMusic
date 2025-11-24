// ApiClient.java
package com.kenny.spldownloader.network;

import android.util.Log;
import com.kenny.spldownloader.config.AppConfig;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ApiClient {
    private static final String TAG = "ApiClient";

    private static ApiClient instance;

    public static ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    private ApiClient() {}

    public String executeGetRequest(String url) throws ApiException {
        Log.d(TAG, "执行HTTP请求: " + url);

        HttpURLConnection connection = null;
        try {
            URL urlObj = new URL(url);
            connection = (HttpURLConnection) urlObj.openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(AppConfig.CONNECT_TIMEOUT);
            connection.setReadTimeout(AppConfig.READ_TIMEOUT);

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "HTTP响应代码: " + responseCode);

            if (responseCode != HttpURLConnection.HTTP_OK) {
                // 读取错误流
                String errorResponse = readErrorStream(connection);
                throw new ApiException("HTTP请求失败，响应码: " + responseCode + ", 错误信息: " + errorResponse);
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String responseBody = response.toString();
            Log.d(TAG, "API响应: " + responseBody);

            validateApiResponse(responseBody);

            return responseBody;

        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw (ApiException) e;
            }
            throw new ApiException("网络请求失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // 新增：返回 JSONObject 的方法
    public JSONObject executeGetRequestJson(String url) throws ApiException {
        String response = executeGetRequest(url);
        try {
            return new JSONObject(response);
        } catch (Exception e) {
            throw new ApiException("JSON解析失败: " + e.getMessage());
        }
    }

    private String readErrorStream(HttpURLConnection connection) {
        try {
            InputStream errorStream = connection.getErrorStream();
            if (errorStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorResponse.append(line);
                }
                reader.close();
                return errorResponse.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "读取错误流失败: " + e.getMessage());
        }
        return "无法读取错误信息";
    }

    private void validateApiResponse(String response) {
        try {
            JSONObject json = new JSONObject(response);
            int code = json.getInt("code");

            if (code != 200) {
                String message = json.optString("message", "未知错误");
                if (code == 503) {
                    throw new ApiException("服务暂时不可用: " + message, true);
                }
                throw new ApiException("API错误: " + message + " (代码: " + code + ")");
            }
        } catch (Exception e) {
            // JSON解析失败，继续使用原始响应
            Log.w(TAG, "JSON解析失败: " + e.getMessage());
        }
    }
}