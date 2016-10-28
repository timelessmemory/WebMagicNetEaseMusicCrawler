import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONPath;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.pipeline.ConsolePipeline;
import us.codecraft.webmagic.pipeline.FilePipeline;
import us.codecraft.webmagic.processor.PageProcessor;

public class NetEaseMusicPageProcessor implements PageProcessor {
	
	//正则表达式\\. \\转义java中的\  \.转义正则中的.
	
	//主域名
	public static final String BASE_URL = "http://music.163.com/";

	//匹配专辑URL
	public static final String ALBUM_URL = "http://music\\.163\\.com/album\\?id=\\d+";
	
	//匹配歌曲URL
	public static final String MUSIC_URL = "http://music\\.163\\.com/song\\?id=\\d+";
	
	//初始地址, JAY_CHOU 周杰伦的床边故事专辑, 可以改为其他歌手专辑地址
	public static final String START_URL = "http://music.163.com/album?id=34720827";
	
	//加密使用到的文本
	public static final String TEXT = "{\"username\": \"\", \"rememberLogin\": \"true\", \"password\": \"\"}";
	
	//爬取结果保存文件路径
	public static final String RESULT_PATH = "/home/user/workspace/WebMagicCrawler/result/";
	
	private Site site = Site.me()
							.setDomain("http://music.163.com")
							.setSleepTime(1000)
							.setRetryTimes(30)
							.setCharset("utf-8")
							.setTimeOut(30000)
							.setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_2) AppleWebKit/537.31 (KHTML, like Gecko) Chrome/26.0.1410.65 Safari/537.31");

	@Override
	public Site getSite() {
		return site;
	}
	
	@Override
	public void process(Page page) {
		//根据URL判断页面类型
		if (page.getUrl().regex(ALBUM_URL).match()) {
			//爬取歌曲URl加入队列
			page.addTargetRequests(page.getHtml().xpath("//div[@id=\"song-list-pre-cache\"]").links().regex(MUSIC_URL).all());
			
			//爬取专辑URL加入队列
			page.addTargetRequests(page.getHtml().xpath("//div[@class=\"cver u-cover u-cover-3\"]").links().regex(ALBUM_URL).all());
		} else {
			String url = page.getUrl().toString();
			page.putField("title", page.getHtml().xpath("//em[@class='f-ff2']/text()"));
			page.putField("author", page.getHtml().xpath("//p[@class='des s-fc4']/span/a/text()"));
			page.putField("album", page.getHtml().xpath("//p[@class='des s-fc4']/a/text()"));
			page.putField("URL", url);
			
			//单独对AJAX请求获取评论数, 使用JSON解析返回结果
			page.putField("commentCount", JSONPath.eval(JSON.parse(crawlAjaxUrl(url.substring(url.indexOf("id=") + 3))), "$.total"));
		}
	}
	
	public static void main(String[] args) {
		Spider.create(new NetEaseMusicPageProcessor())
		//初始URL
		.addUrl(START_URL)
		.addPipeline(new ConsolePipeline())
		//结果输出到文件
		.addPipeline(new FilePipeline(RESULT_PATH))
		.run();
	}
	
	//对AJAX数据进行单独请求
	public String crawlAjaxUrl(String songId) { 
		CloseableHttpClient httpclient = HttpClients.createDefault();
		CloseableHttpResponse response =null;
		
		try {
			//参数加密
	    	String secKey = new BigInteger(100, new SecureRandom()).toString(32).substring(0, 16);
	        String encText = aesEncrypt(aesEncrypt(TEXT, "0CoJUm6Qyw8W8jud"), secKey);
	        String encSecKey = rsaEncrypt(secKey);
	    	
	        HttpPost httpPost = new HttpPost("http://music.163.com/weapi/v1/resource/comments/R_SO_4_" + songId + "/?csrf_token=");
	        httpPost.addHeader("Referer", BASE_URL);
	        
	        List<NameValuePair> ls = new ArrayList<NameValuePair>();
	        ls.add(new BasicNameValuePair("params", encText));
	        ls.add(new BasicNameValuePair("encSecKey", encSecKey));
	        
	        UrlEncodedFormEntity paramEntity = new UrlEncodedFormEntity(ls, "utf-8");
	        httpPost.setEntity(paramEntity);
	        
	        response = httpclient.execute(httpPost);
            HttpEntity entity = response.getEntity();
            
            if (entity != null) {
                return EntityUtils.toString(entity, "utf-8");
            }
            
		} catch (Exception e) {
        	e.printStackTrace();
		} finally {
			try {
				response.close();
				httpclient.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return "";
	}
	
	public String aesEncrypt(String value, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes("UTF-8"), "AES"), 
        		new IvParameterSpec("0102030405060708".getBytes("UTF-8")));
        return Base64.encodeBase64String(cipher.doFinal(value.getBytes()));
    }

    public String rsaEncrypt(String value) throws UnsupportedEncodingException {
        value = new StringBuilder(value).reverse().toString();
        BigInteger valueInt = hexToBigInteger(stringToHex(value));
        BigInteger pubkey = hexToBigInteger("010001");
        BigInteger modulus = hexToBigInteger("00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7");
        return valueInt.modPow(pubkey, modulus).toString(16);
    }

    public BigInteger hexToBigInteger(String hex) {
        return new BigInteger(hex, 16);
    }

    public String stringToHex(String text) throws UnsupportedEncodingException {
        return DatatypeConverter.printHexBinary(text.getBytes("UTF-8"));
    }

    public String stampToDate(long stamp){
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(stamp));
    }
}