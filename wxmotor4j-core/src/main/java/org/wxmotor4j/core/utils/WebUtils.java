package org.wxmotor4j.core.utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.wxmotor4j.core.utils.file.FileItem;

/**
 * 网络工具类
 * 
 * @ClassName: WebUtils
 * @author Tluo
 * @date 2016年12月29日
 *
 */
public abstract class WebUtils {
	private static final String DEF_CHARSET = "UTF-8";
	private static final String METHOD_POST = "POST";
	private static final String METHOD_GET = "GET";

	private static HostnameVerifier verifier = null;

	private static SSLContext ctx = null;
	private static SSLSocketFactory socketFactory = null;

	private static class DefaultTrustManager implements X509TrustManager {
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}

		public void checkClientTrusted(X509Certificate[] chain, String authType)
				throws CertificateException {
		}

		public void checkServerTrusted(X509Certificate[] chain, String authType)
				throws CertificateException {
		}
	}

	static {

		try {
			ctx = SSLContext.getInstance("TLS");
			ctx.init(new KeyManager[0], new TrustManager[] { new DefaultTrustManager() },
					new SecureRandom());

			ctx.getClientSessionContext().setSessionTimeout(15);
			ctx.getClientSessionContext().setSessionCacheSize(1000);

			socketFactory = ctx.getSocketFactory();
		} catch (Exception e) {

		}

		verifier = new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) {
				return false;// 默认认证不通过，进行证书校验。
			}
		};

	}

	private WebUtils() {
	}

	/**
	 * 执行HTTP GET请求。
	 * 
	 * @param url
	 *            请求地址
	 * @param params
	 *            请求参数
	 * @param charset
	 *            字符集，如UTF-8, GBK, GB2312
	 * @return 响应字符串
	 * @throws IOException
	 */
	public static String doGet(String url, Map<String, String> params, String charset)
			throws IOException {
		HttpURLConnection conn = null;
		String rsp = null;

		try {
			String ctype = "application/x-www-form-urlencoded;charset=" + charset;
			String query = buildQuery(params, charset);
			try {
				conn = getConnection(buildGetUrl(url, query), METHOD_GET, ctype);
			} catch (IOException e) {
				throw e;
			}

			try {
				rsp = getResponseAsString(conn);
			} catch (IOException e) {
				throw e;
			}

		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}

		return rsp;
	}

	/**
	 * 执行HTTP POST请求。
	 * 
	 * @param url
	 *            请求地址
	 * @param params
	 *            请求参数
	 * @param charset
	 *            字符集，如UTF-8, GBK, GB2312
	 * @return 响应字符串
	 * @throws IOException
	 */
	public static String doPost(String url, Map<String, String> params, String charset,
			int connectTimeout, int readTimeout) throws IOException {
		String ctype = "application/x-www-form-urlencoded;charset=" + charset;
		String query = buildQuery(params, charset);
		byte[] content = {};
		if (query != null) {
			content = query.getBytes(charset);
		}
		return doPost(url, ctype, content, connectTimeout, readTimeout);
	}

	/**
	 * 执行HTTP POST请求。
	 * 
	 * @param url
	 *            请求地址
	 * @param ctype
	 *            请求类型
	 * @param content
	 *            请求字节数组
	 * @return 响应字符串
	 * @throws IOException
	 */
	public static String doPost(String url, String ctype, byte[] content, int connectTimeout,
			int readTimeout) throws IOException {
		HttpURLConnection conn = null;
		OutputStream out = null;
		String rsp = null;
		try {
			try {
				conn = getConnection(new URL(url), METHOD_POST, ctype);
				conn.setConnectTimeout(connectTimeout);
				conn.setReadTimeout(readTimeout);
			} catch (IOException e) {
				throw e;
			}
			try {
				out = conn.getOutputStream();
				out.write(content);
				rsp = getResponseAsString(conn);
			} catch (IOException e) {
				throw e;
			}

		} finally {
			if (out != null) {
				out.close();
				out = null;
			}
			if (conn != null) {
				conn.disconnect();
				conn = null;

			}
		}

		return rsp;
	}

	/**
	 * 执行带文件上传的HTTP POST请求。
	 * 
	 * @param url
	 *            请求地址
	 * @param textParams
	 *            文本请求参数
	 * @param fileParams
	 *            文件请求参数
	 * @param charset
	 *            字符集，如UTF-8, GBK, GB2312
	 * @return 响应字符串
	 * @throws IOException
	 */
	public static String doPost(String url, Map<String, String> params,
			Map<String, FileItem> fileParams, String charset, String ctype, int connectTimeout,
			int readTimeout) throws IOException {
		if (fileParams == null || fileParams.isEmpty()) {
			return doPost(url, params, charset, connectTimeout, readTimeout);
		}

		String boundary = System.currentTimeMillis() + ""; // 随机分隔线
		HttpURLConnection conn = null;
		DataOutputStream dataOut = null;
		String rsp = null;
		try {
			try {
				if (StringUtils.isEmpty(ctype)) {
					ctype = "multipart/form-data;boundary=" + boundary + ";charset=" + charset;
				} else {
					ctype = ctype + "boundary=" + boundary;
				}
				conn = getConnection(new URL(url), METHOD_POST, ctype);
				conn.setConnectTimeout(connectTimeout);
				conn.setReadTimeout(readTimeout);
			} catch (IOException e) {
				throw e;
			}

			try {
				dataOut = new DataOutputStream(conn.getOutputStream());

				byte[] entryBoundaryBytes = ("\r\n--" + boundary + "\r\n").getBytes(charset);
				// 组装文本请求参数
				if (null != params && params.size() > 0) {
					Set<Entry<String, String>> textEntrySet = params.entrySet();
					for (Entry<String, String> textEntry : textEntrySet) {
						byte[] textBytes = getTextEntry(textEntry.getKey(), textEntry.getValue(),
								charset);
						dataOut.write(entryBoundaryBytes);
						dataOut.write(textBytes);
					}
				}

				// 组装文件请求参数
				Set<Entry<String, FileItem>> fileEntrySet = fileParams.entrySet();
				for (Entry<String, FileItem> fileEntry : fileEntrySet) {
					FileItem fileItem = fileEntry.getValue();
					byte[] fileBytes = getFileEntry(fileEntry.getKey(), fileItem.getFileName(),
							fileItem.getMimeType(), charset);
					dataOut.write(entryBoundaryBytes);
					dataOut.write(fileBytes);
					dataOut.write(fileItem.getContent());
				}

				// 添加请求结束标志
				byte[] endBoundaryBytes = ("\r\n--" + boundary + "--\r\n").getBytes(charset);
				dataOut.write(endBoundaryBytes);
				rsp = getResponseAsString(conn);
			} catch (IOException e) {
				throw e;
			}

		} finally {
			if (dataOut != null) {
				dataOut.close();
			}
			if (conn != null) {
				conn.disconnect();
			}
		}

		return rsp;
	}

	public static String buildQuery(Map<String, String> params, String charset) throws IOException {
		if (params == null || params.isEmpty()) {
			return null;
		}

		StringBuilder query = new StringBuilder();
		Set<Entry<String, String>> entries = params.entrySet();
		boolean hasParam = false;

		for (Entry<String, String> entry : entries) {
			String name = entry.getKey();
			String value = entry.getValue();
			// 忽略参数名或参数值为空的参数
			if (StringUtils.areNotEmpty(name, value)) {
				if (hasParam) {
					query.append("&");
				} else {
					hasParam = true;
				}

				query.append(name).append("=").append(URLEncoder.encode(value, charset));
			}
		}

		return query.toString();
	}

	public static String buildForm(String baseUrl, Map<String, String> parameters) {
		java.lang.StringBuffer sb = new StringBuffer();
		sb.append("<form name=\"punchout_form\" method=\"post\" action=\"");
		sb.append(baseUrl);
		sb.append("\">\n");
		sb.append(buildHiddenFields(parameters));

		sb.append("<input type=\"submit\" value=\"立即支付\" style=\"display:none\" >\n");
		sb.append("</form>\n");
		sb.append("<script>document.forms[0].submit();</script>");
		java.lang.String form = sb.toString();
		return form;
	}

	/**
	 * 使用指定的字符集反编码请求参数值。
	 * 
	 * @param value
	 *            参数值
	 * @param charset
	 *            字符集
	 * @return 反编码后的参数值
	 */
	public static String decode(String value, String charset) {
		String result = null;
		if (!StringUtils.isEmpty(value)) {
			try {
				result = URLDecoder.decode(value, charset);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return result;
	}

	/**
	 * 使用指定的字符集编码请求参数值。
	 * 
	 * @param value
	 *            参数值
	 * @param charset
	 *            字符集
	 * @return 编码后的参数值
	 */
	public static String encode(String value, String charset) {
		String result = null;
		if (!StringUtils.isEmpty(value)) {
			try {
				result = URLEncoder.encode(value, charset);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return result;
	}

	/**
	 * 从URL中提取所有的参数。
	 * 
	 * @param query
	 *            URL地址
	 * @return 参数映射
	 */
	public static Map<String, String> splitUrlQuery(String query) {
		Map<String, String> result = new HashMap<String, String>();

		String[] pairs = query.split("&");
		if (pairs != null && pairs.length > 0) {
			for (String pair : pairs) {
				String[] param = pair.split("=", 2);
				if (param != null && param.length == 2) {
					result.put(param[0], param[1]);
				}
			}
		}

		return result;
	}

	/**
	 * post 下载文件
	 * 
	 * @Title: downloadFile
	 * @param 参数
	 * @return 返回类型
	 * @throws 异常
	 */
	public static void downloadFilePost(String url, String charset, byte[] content,
			int connectTimeout, int readTimeout, String filePath) throws Exception {
		String ctype = "application/x-www-form-urlencoded;charset=" + charset;

		HttpURLConnection conn = null;
		OutputStream out = null;
		InputStream in = null;
		FileOutputStream fos = null;
		try {
			try {
				conn = getConnection(new URL(url), METHOD_GET, ctype);
				conn.setConnectTimeout(connectTimeout);
				conn.setReadTimeout(readTimeout);
			} catch (IOException e) {
				throw e;
			}
			try {
				if (content != null && content.length > 0) {
					out = conn.getOutputStream();
					out.write(content);
				}
				if (HttpsURLConnection.HTTP_OK != conn.getResponseCode()) {
					throw new Exception("请求状态异常");
				}
				in = conn.getInputStream();
				fos = new FileOutputStream(new File(filePath));
				int len = -1;
				byte[] b = new byte[1024];
				while ((len = in.read(b)) != -1) {
					fos.write(b, 0, len);
				}
			} catch (IOException e) {
				throw e;
			}
		} finally {
			try {
				if (fos != null) {
					fos.close();
					fos = null;
				}

			} finally {
				if (conn != null) {
					conn.disconnect();
					conn = null;
					in = null;
					out = null;
				}
			}

		}

	}

	private static byte[] getTextEntry(String fieldName, String fieldValue, String charset)
			throws IOException {
		StringBuilder entry = new StringBuilder();
		entry.append("Content-Disposition:form-data;name=\"");
		entry.append(fieldName);
		entry.append("\"\r\nContent-Type:text/plain\r\n\r\n");
		entry.append(fieldValue);
		return entry.toString().getBytes(charset);
	}

	private static byte[] getFileEntry(String fieldName, String fileName, String mimeType,
			String charset) throws IOException {
		StringBuilder entry = new StringBuilder();
		entry.append("Content-Disposition:form-data;name=\"");
		entry.append(fieldName);
		entry.append("\";filename=\"");
		entry.append(fileName);
		entry.append("\"\r\nContent-Type:");
		entry.append(mimeType);
		entry.append("\r\n\r\n");
		return entry.toString().getBytes(charset);
	}

	private static HttpURLConnection getConnection(URL url, String method, String ctype)
			throws IOException {
		HttpURLConnection conn = null;
		if ("https".equals(url.getProtocol())) {
			HttpsURLConnection connHttps = (HttpsURLConnection) url.openConnection();
			connHttps.setSSLSocketFactory(socketFactory);
			connHttps.setHostnameVerifier(verifier);
			conn = connHttps;
		} else {
			conn = (HttpURLConnection) url.openConnection();
		}

		conn.setRequestMethod(method);
		conn.setDoInput(true);
		conn.setDoOutput(true);
		conn.setRequestProperty("Accept", "text/xml,text/javascript,text/html");
		conn.setRequestProperty("User-Agent", "aop-sdk-java");
		conn.setRequestProperty("Content-Type", ctype);
		return conn;
	}

	private static URL buildGetUrl(String strUrl, String query) throws IOException {
		URL url = new URL(strUrl);
		if (StringUtils.isEmpty(query)) {
			return url;
		}

		if (StringUtils.isEmpty(url.getQuery())) {
			if (strUrl.endsWith("?")) {
				strUrl = strUrl + query;
			} else {
				strUrl = strUrl + "?" + query;
			}
		} else {
			if (strUrl.endsWith("&")) {
				strUrl = strUrl + query;
			} else {
				strUrl = strUrl + "&" + query;
			}
		}

		return new URL(strUrl);
	}

	private static String getResponseAsString(HttpURLConnection conn) throws IOException {
		String charset = getResponseCharset(conn.getContentType());
		InputStream es = conn.getErrorStream();
		if (es == null) {
			return getStreamAsString(conn.getInputStream(), charset);
		} else {
			String msg = getStreamAsString(es, charset);
			if (StringUtils.isEmpty(msg)) {
				throw new IOException(conn.getResponseCode() + ":" + conn.getResponseMessage());
			} else {
				throw new IOException(msg);
			}
		}
	}

	private static String getStreamAsString(InputStream stream, String charset) throws IOException {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, charset));
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

	private static String getResponseCharset(String ctype) {
		String charset = DEF_CHARSET;
		if (!StringUtils.isEmpty(ctype)) {
			String[] params = ctype.split(";");
			for (String param : params) {
				param = param.trim();
				if (param.startsWith("charset")) {
					String[] pair = param.split("=", 2);
					if (pair.length == 2) {
						if (!StringUtils.isEmpty(pair[1])) {
							charset = pair[1].trim();
						}
					}
					break;
				}
			}
		}

		return charset;
	}

	private static String buildHiddenFields(Map<String, String> parameters) {
		if (parameters == null || parameters.isEmpty()) {
			return "";
		}
		java.lang.StringBuffer sb = new StringBuffer();
		Set<String> keys = parameters.keySet();
		for (String key : keys) {
			String value = parameters.get(key);
			// 除去参数中的空值
			if (key == null || value == null) {
				continue;
			}
			sb.append(buildHiddenField(key, value));
		}
		java.lang.String result = sb.toString();
		return result;
	}

	private static String buildHiddenField(String key, String value) {
		java.lang.StringBuffer sb = new StringBuffer();
		sb.append("<input type=\"hidden\" name=\"");
		sb.append(key);

		sb.append("\" value=\"");
		// 转义双引号
		String a = value.replace("\"", "&quot;");
		sb.append(a).append("\">\n");
		return sb.toString();
	}

}
