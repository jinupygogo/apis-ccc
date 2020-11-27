package jp.co.sony.csl.dcoes.apis.tools.ccc.impl.http_post;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.ZonedDateTime;

import jp.co.sony.csl.dcoes.apis.common.util.DateTimeUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.tools.ccc.DealReporting;

/**
 * ウェブサービスに対して HTTP POST で融通情報を通知する実装.
 * {@link DealReporting} で使用される.
 * @author OES Project
 */
public class HttpPostDealReportingImpl implements DealReporting.Impl {
	private static final Logger log = LoggerFactory.getLogger(HttpPostDealReportingImpl.class);

	/**
	 * HTTP 接続のタイムアウトのデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_REQUEST_TIMEOUT_MSEC = 5000L;

	private Vertx vertx_;
	private HttpClient client_;
	private String uri_;

	/**
	 * インスタンスを作成する.
	 * CONFIG から設定を取得し初期化する.
	 * - CONFIG.dealReporting.host : 接続先ホスト名 [{@link String}]
	 * - CONFIG.dealReporting.ssl : SSL フラグ [{@link Boolean}]
	 * - CONFIG.dealReporting.sslTrustAll : SSL なんでも OK フラグ [{@link Boolean}]
	 * - CONFIG.dealReporting.port : 接続先ポート [{@link Integer}].
	 *                               設定がない場合 SSL なら 443, そうでなければ 80.
	 * - CONFIG.dealReporting.uri : 接続先 URI [{@link String}]
	 * @param vertx vertx オブジェクト
	 */
	public HttpPostDealReportingImpl(Vertx vertx) {
		vertx_ = vertx;
		String host = VertxConfig.config.getString("dealReporting", "host");
		Boolean isSsl = VertxConfig.config.getBoolean(false, "dealReporting", "ssl");
		Integer port = (isSsl) ? VertxConfig.config.getInteger(443, "dealReporting", "port") : VertxConfig.config.getInteger(80, "dealReporting", "port");
		Boolean sslTrustAll = VertxConfig.config.getBoolean(false, "dealReporting", "sslTrustAll");
		uri_ = VertxConfig.config.getString("dealReporting", "uri");
		if (log.isInfoEnabled()) log.info("host : " + host);
		if (log.isInfoEnabled()) log.info("port : " + port);
		if (isSsl) if (log.isInfoEnabled()) log.info("sslTrustAll : " + sslTrustAll);
		if (log.isInfoEnabled()) log.info("uri : " + uri_);
		client_ = vertx_.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port).setSsl(isSsl).setTrustAll(sslTrustAll));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public void report(JsonObject deal, Handler<AsyncResult<Void>> completionHandler) {
		report(new JsonArray().add(deal), completionHandler);
	}
	/**
	 * {@inheritDoc}
	 */
	@Override public void report(JsonArray deals, Handler<AsyncResult<Void>> completionHandler) {
		for (Object aDeal : deals) {
			convertDateTimeField_((JsonObject) aDeal);
		}
		Buffer body = Buffer.buffer(deals.encode());
		if (log.isDebugEnabled()) log.debug("body : " + body);
		new Poster_(body).execute_(completionHandler);
	}

	/**
	 * "DateTime" で終わる属性はすべて APIS プログラムの標準フォーマットの日時文字列のはずなので ISO フォーマットに変換する.
	 * @param obj 変換対象 DEAL オブジェクト
	 */
	private void convertDateTimeField_(JsonObject obj) {
		for (String aKey : obj.fieldNames()) {
			Object aVal = obj.getValue(aKey);
			if (aVal instanceof String) {
				if (aKey.endsWith("DateTime") || aKey.equals("dateTime")) {
					ZonedDateTime zdt = DateTimeUtil.toSystemDefaultZonedDateTime((String) aVal);
					obj.put(aKey, DateTimeUtil.toISO8601OffsetString(zdt));
				}
			} else if (aVal instanceof JsonObject) {
				convertDateTimeField_((JsonObject) aVal);
			} else if (aVal instanceof JsonArray) {
				for (Object anObj : (JsonArray) aVal) {
					if (anObj instanceof JsonObject) {
						convertDateTimeField_((JsonObject) anObj);
					}
				}
			}
		}
	}

	////

	private class Poster_ {
		private Buffer body_;
		private boolean completed_ = false;
		private Poster_(Buffer body) {
			body_ = body;
		}
		/**
		 * HTTP POST 処理実行.
		 * ( 実装がまずいのか ) 二度結果が返ってくることがあるためここでブロックする.
		 * @param completionHandler the completion handler
		 */
		private void execute_(Handler<AsyncResult<Void>> completionHandler) {
			post_(r -> {
				if (!completed_) {
					completed_ = true;
					completionHandler.handle(r);
				} else {
					if (log.isWarnEnabled()) log.warn("post_() result returned more than once : " + r);
				}
			});
		}
		private void post_(Handler<AsyncResult<Void>> completionHandler) {
			Long requestTimeoutMsec = VertxConfig.config.getLong(DEFAULT_REQUEST_TIMEOUT_MSEC, "dealReporting", "requestTimeoutMsec");
			client_.post(uri_, resPost -> {
				if (log.isDebugEnabled()) log.debug("status : " + resPost.statusCode());
				if (resPost.statusCode() == 200) {
					completionHandler.handle(Future.succeededFuture());
				} else {
					resPost.bodyHandler(error -> {
						completionHandler.handle(Future.failedFuture("http post failed : " + resPost.statusCode() + " : " + resPost.statusMessage() + " : " + error));
					}).exceptionHandler(t -> {
						completionHandler.handle(Future.failedFuture("http post failed : " + resPost.statusCode() + " : " + resPost.statusMessage() + " : " + t));
					});
				}
			}).setTimeout(requestTimeoutMsec).exceptionHandler(t -> {
				completionHandler.handle(Future.failedFuture(t));
			}).putHeader("content-type", "application/json").putHeader("content-length", String.valueOf(body_.length())).write(body_).end();
		}
	}

}
