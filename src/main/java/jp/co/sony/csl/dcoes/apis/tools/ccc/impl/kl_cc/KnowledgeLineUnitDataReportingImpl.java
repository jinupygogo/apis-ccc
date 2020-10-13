package jp.co.sony.csl.dcoes.apis.tools.ccc.impl.kl_cc;

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
import jp.co.sony.csl.dcoes.apis.tools.ccc.UnitDataReporting;

/**
 * KnowledgeLine に対してユニットデータを通知する実装.
 * {@link UnitDataReporting} で使用される.
 * @author OES Project
 */
public class KnowledgeLineUnitDataReportingImpl implements UnitDataReporting.Impl {
	private static final Logger log = LoggerFactory.getLogger(KnowledgeLineUnitDataReportingImpl.class);

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
	 * - CONFIG.unitDataReporting.host : 接続先ホスト名 [{@link String}]
	 * - CONFIG.unitDataReporting.ssl : SSL フラグ [{@link Boolean}]
	 * - CONFIG.unitDataReporting.sslTrustAll : SSL なんでも OK フラグ [{@link Boolean}]
	 * - CONFIG.unitDataReporting.port : 接続先ポート [{@link Integer}].
	 *                                   設定がない場合 SSL なら 443, そうでなければ 80.
	 * - CONFIG.unitDataReporting.uri : 接続先 URI [{@link String}]
	 * @param vertx vertx オブジェクト
	 */
	public KnowledgeLineUnitDataReportingImpl(Vertx vertx) {
		vertx_ = vertx;
		String host = VertxConfig.config.getString("unitDataReporting", "host");
		Boolean isSsl = VertxConfig.config.getBoolean(false, "unitDataReporting", "ssl");
		Integer port = (isSsl) ? VertxConfig.config.getInteger(443, "unitDataReporting", "port") : VertxConfig.config.getInteger(80, "unitDataReporting", "port");
		Boolean sslTrustAll = VertxConfig.config.getBoolean(false, "unitDataReporting", "sslTrustAll");
		uri_ = VertxConfig.config.getString("unitDataReporting", "uri");
		if (log.isInfoEnabled()) log.info("host : " + host);
		if (log.isInfoEnabled()) log.info("port : " + port);
		if (isSsl) if (log.isInfoEnabled()) log.info("sslTrustAll : " + sslTrustAll);
		if (log.isInfoEnabled()) log.info("uri : " + uri_);
		client_ = vertx_.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port).setSsl(isSsl).setTrustAll(sslTrustAll));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public void report(JsonObject unitData, Handler<AsyncResult<Void>> completionHandler) {
		convertDateTimeField_(unitData);
		JsonArray unitDataAsArray = toArray_(unitData);
		Buffer body = Buffer.buffer(unitDataAsArray.encode());
		if (log.isDebugEnabled()) log.debug("body : " + body);
		new Poster_(body).execute_(completionHandler);
	}

	private JsonArray toArray_(JsonObject obj) {
		JsonArray result = new JsonArray();
		for (String aKey : obj.fieldNames()) {
			result.add(obj.getJsonObject(aKey));
		}
		return result;
	}

	/**
	 * "time" で終わる属性はすべて APIS プログラムの標準フォーマットの日時文字列のはずなので ISO フォーマットに変換する.
	 * @param obj 変換対象 UNITDATA オブジェクト
	 */
	private void convertDateTimeField_(JsonObject obj) {
		for (String aKey : obj.fieldNames()) {
			Object aVal = obj.getValue(aKey);
			if (aVal instanceof String) {
				if (aKey.endsWith("time")) {
					ZonedDateTime zdt = DateTimeUtil.toSystemDefaultZonedDateTime((String) aVal);
					obj.put(aKey, DateTimeUtil.toISO8601OffsetString(zdt));
				}
			} else if (aVal instanceof JsonObject) {
				convertDateTimeField_((JsonObject) aVal);
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
			Long requestTimeoutMsec = VertxConfig.config.getLong(DEFAULT_REQUEST_TIMEOUT_MSEC, "unitDataReporting", "requestTimeoutMsec");
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
