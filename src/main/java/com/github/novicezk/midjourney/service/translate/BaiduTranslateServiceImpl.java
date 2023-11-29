package com.github.novicezk.midjourney.service.translate;


import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.MD5;
import com.github.novicezk.midjourney.ProxyProperties;
import com.github.novicezk.midjourney.service.TranslateService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.support.BeanDefinitionValidationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import java.util.*;
@Slf4j
public class BaiduTranslateServiceImpl implements TranslateService {


	public static String textTrans(String from, String to, String q, String termIds) {
		// 请求url
		String url = "https://aip.baidubce.com/rpc/2.0/mt/texttrans/v1";
		try {
			Map<String, Object> map = new HashMap<>();
			map.put("from", from);
			map.put("to", to);
			map.put("q", q);
			map.put("termIds", termIds);

			String param = GsonUtils.toJson(map);

			// 注意这里仅为了简化编码每一次请求都去获取access_token，线上环境access_token有过期时间， 客户端可自行缓存，过期后重新获取。
			String accessToken = "[调用鉴权接口获取的token]";

			String result = HttpUtil.post(url, accessToken, "application/json", param);
			System.out.println(result);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	static final OkHttpClient HTTP_CLIENT = new OkHttpClient().newBuilder().build();

	private static final String TRANSLATE_API = "https://fanyi-api.baidu.com/api/trans/vip/translate";
	private final String appid;
	private final String appKey;
	private final String appSecret;

	class Employee {
		static String token;
	}
		 
	public BaiduTranslateServiceImpl(ProxyProperties.BaiduTranslateConfig translateConfig) {
		this.appid = translateConfig.getAppid();
		this.appKey = translateConfig.getAppKey();
		this.appSecret = translateConfig.getAppSecret();

		try {
			this.getAccessToken();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		log.info(GlobalVals.NAME);
		
		if (!CharSequenceUtil.isAllNotBlank(this.appid, this.appSecret)) {
			throw new BeanDefinitionValidationException("mj.baidu-translate.appid或mj.baidu-translate.app-secret未配置");
		}
	}
		class GlobalVals {
		  static final int ID = 1212;
		  static final String NAME = "Samre";
		}

	    /**
     * 从用户的AK，SK生成鉴权签名（Access Token）
     *
     * @return 鉴权签名（Access Token）
     * @throws IOException IO异常
     */
		String getAccessToken() throws IOException {

        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        RequestBody body = RequestBody.create(mediaType, "grant_type=client_credentials&client_id=" + this.appKey
                + "&client_secret=" + this.appSecret);
        Request request = new Request.Builder()
                .url("https://aip.baidubce.com/oauth/2.0/token")
                .method("POST", body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
        Response response = HTTP_CLIENT.newCall(request).execute();
        return new JSONObject(response.body().string()).getString("access_token");
//		return "";
    }

	@Override
	public String translateToEnglish(String prompt) {
		if (!containsChinese(prompt)) {
			return prompt;
		}
		String salt = RandomUtil.randomNumbers(5);
		String sign = MD5.create().digestHex(this.appid + prompt + salt + this.appSecret);
		HttpHeaders headers = new HttpHeaders();
//		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("from", "zh");
		body.add("to", "en");
		body.add("appid", this.appid);
		body.add("salt", salt);
		body.add("q", prompt);
		body.add("sign", sign);
		HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
		try {
			ResponseEntity<String> responseEntity = new RestTemplate().exchange(TRANSLATE_API, HttpMethod.POST, requestEntity, String.class);
			if (responseEntity.getStatusCode() != HttpStatus.OK || CharSequenceUtil.isBlank(responseEntity.getBody())) {
				throw new ValidateException(responseEntity.getStatusCodeValue() + " - " + responseEntity.getBody());
			}
			JSONObject result = new JSONObject(responseEntity.getBody());
			if (result.has("error_code")) {
				throw new ValidateException(result.getString("error_code") + " - " + result.getString("error_msg"));
			}
			List<String> strings = new ArrayList<>();
			JSONArray transResult = result.getJSONArray("trans_result");
			for (int i = 0; i < transResult.length(); i++) {
				strings.add(transResult.getJSONObject(i).getString("dst"));
			}
			return CharSequenceUtil.join("\n", strings);
		} catch (Exception e) {
			log.warn("调用百度翻译失败: {}", e.getMessage());
		}
		return prompt;
	}

}
